import json
from types import SimpleNamespace

import groq
import httpx
import pytest

from app.infrastructure.ai import build_ai_service
from app.infrastructure.ai.groq_service import GroqAIService, parse_evaluation
from app.infrastructure.ai.mock_service import MockAIService
from app.infrastructure.config.settings import Settings
from app.shared.errors import InfrastructureError


def _status_error(cls, status: int):
    request = httpx.Request("POST", "https://api.groq.com/openai/v1/chat/completions")
    response = httpx.Response(status, request=request)
    return cls("error", response=response, body=None)


class ScriptedClient:
    """Mimics `client.chat.completions.create`; outcomes are keyed by model."""

    def __init__(self, outcomes: dict):
        self.outcomes = outcomes
        self.models_called: list[str] = []
        self.chat = SimpleNamespace(completions=SimpleNamespace(create=self._create))

    def _create(self, *, model, **_kwargs):
        self.models_called.append(model)
        outcome = self.outcomes[model]
        if isinstance(outcome, Exception):
            raise outcome
        message = SimpleNamespace(content=outcome)
        return SimpleNamespace(choices=[SimpleNamespace(message=message)])


EVALUATION = json.dumps(
    {"score": 7, "strengths": ["clear"], "weaknesses": ["vague"], "improved_answer": "better"}
)


def service(outcomes: dict) -> tuple[GroqAIService, ScriptedClient]:
    client = ScriptedClient(outcomes)
    return GroqAIService(api_key="", models=list(outcomes), client=client), client


def test_falls_back_when_first_model_is_retired():
    svc, client = service(
        {"retired-model": _status_error(groq.NotFoundError, 404), "good-model": EVALUATION}
    )
    result = svc.evaluate_answer("q", "a")
    assert result.score == 7
    assert result.model == "good-model"
    assert client.models_called == ["retired-model", "good-model"]


def test_falls_back_on_invalid_json_and_rate_limit():
    svc, client = service(
        {
            "a": "not json",
            "b": _status_error(groq.RateLimitError, 429),
            "c": json.dumps({"questions": ["one", "two", "three"]}),
        }
    )
    assert svc.generate_questions("Dev", "1 year", count=2) == ["one", "two"]
    assert client.models_called == ["a", "b", "c"]


def test_all_models_failing_raises_infrastructure_error():
    svc, _ = service({"a": _status_error(groq.InternalServerError, 500), "b": "{}"})
    with pytest.raises(InfrastructureError) as excinfo:
        svc.generate_questions("Dev", "1 year")
    assert [a["model"] for a in excinfo.value.details["attempts"]] == ["a", "b"]


def test_auth_error_does_not_try_other_models():
    svc, client = service({"a": _status_error(groq.AuthenticationError, 401), "b": EVALUATION})
    with pytest.raises(InfrastructureError):
        svc.evaluate_answer("q", "a")
    assert client.models_called == ["a"]


def test_parse_evaluation_clamps_and_coerces():
    ev = parse_evaluation(json.dumps({"score": "14", "strengths": None}))
    assert ev.score == 10
    assert ev.strengths == []
    assert parse_evaluation(json.dumps({"score": -3})).score == 1


def test_groq_requires_key_and_models():
    with pytest.raises(ValueError):
        GroqAIService(api_key="", models=["m"])
    with pytest.raises(ValueError):
        GroqAIService(api_key="k", models=[])


def test_mock_is_deterministic_and_rewards_better_answers():
    mock = MockAIService()
    assert mock.generate_questions("Backend Engineer", "2 years", 3) == mock.generate_questions(
        "Backend Engineer", "2 years", 3
    )
    assert len(mock.generate_questions("Anything", "1 year", 10)) == 10
    weak = mock.evaluate_answer("q", "I don't know")
    strong = mock.evaluate_answer(
        "q",
        "First I would measure p95 latency, because guessing wastes time. For example, in my last "
        "project a missing index made one query take 2 seconds; adding it cut latency by 90%. "
        + "More detail here. " * 20,
    )
    assert 1 <= weak.score < strong.score <= 10


def test_provider_selection():
    assert build_ai_service(Settings(LLM_PROVIDER="auto", GROQ_API_KEY="", _env_file=None)).name == "mock"
    assert build_ai_service(Settings(LLM_PROVIDER="mock", GROQ_API_KEY="k", _env_file=None)).name == "mock"
    groq_service = build_ai_service(
        Settings(LLM_PROVIDER="auto", GROQ_API_KEY="k", GROQ_MODELS="m1, m2", _env_file=None)
    )
    assert groq_service.name == "groq"
    assert groq_service.models == ["m1", "m2"]
