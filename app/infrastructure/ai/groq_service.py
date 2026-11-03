"""
Groq AI service implementation.

Resilience strategy:
- Per-request timeout and SDK-level retries (with backoff) for transient
  failures: timeouts, connection errors, 429 and 5xx.
- An ordered model list. If a model is retired, rate limited past its
  retries, or returns unusable JSON, the next model is tried.
- Authentication errors stop immediately: another model will not fix a bad key.
"""

import json
import logging
from collections.abc import Callable
from typing import Any, Optional, TypeVar

import groq
from groq import Groq

from app.domain.interview.entities import InterviewEvaluation
from app.shared.errors import InfrastructureError

logger = logging.getLogger(__name__)

T = TypeVar("T")


def parse_questions(content: str, count: int) -> list[str]:
    data = json.loads(content)
    questions = data.get("questions", []) if isinstance(data, dict) else []
    questions = [str(q).strip() for q in questions if str(q).strip()]
    if not questions:
        raise ValueError("No questions returned")
    return questions[:count]


def parse_evaluation(content: str, model: Optional[str] = None) -> InterviewEvaluation:
    data = json.loads(content)
    if not isinstance(data, dict):
        raise ValueError("Evaluation is not a JSON object")
    try:
        score = int(round(float(data.get("score", 5))))
    except (TypeError, ValueError):
        score = 5
    return InterviewEvaluation(
        score=min(10, max(1, score)),
        strengths=[str(s) for s in data.get("strengths", []) or []],
        weaknesses=[str(w) for w in data.get("weaknesses", []) or []],
        improved_answer=str(data.get("improved_answer", "") or ""),
        model=model,
    )


class GroqAIService:
    """
    Groq API implementation of the AIService port.
    """

    name = "groq"

    def __init__(
        self,
        api_key: str,
        models: list[str],
        *,
        timeout_seconds: float = 20.0,
        max_retries: int = 2,
        client: Any = None,
    ) -> None:
        """
        Args:
            api_key: Groq API key.
            models: Model IDs to try, in order.
            timeout_seconds: Per-request timeout.
            max_retries: SDK retries per model for transient errors.
            client: Optional pre-built client (used by tests).
        """
        if not api_key and client is None:
            raise ValueError("GROQ_API_KEY is required for the Groq provider")
        if not models:
            raise ValueError("At least one Groq model must be configured")
        self._client = client or Groq(
            api_key=api_key, timeout=timeout_seconds, max_retries=max_retries
        )
        self._models = models

    @property
    def models(self) -> list[str]:
        return list(self._models)

    def _complete_json(self, prompt: str, parse: Callable[[str, str], T], operation: str) -> T:
        failures: list[dict[str, str]] = []
        for model in self._models:
            try:
                response = self._client.chat.completions.create(
                    model=model,
                    messages=[{"role": "user", "content": prompt}],
                    temperature=0.7,
                    response_format={"type": "json_object"},
                )
                content = response.choices[0].message.content
                if not content:
                    raise ValueError("Empty response")
                return parse(content, model)
            except groq.AuthenticationError as e:
                raise InfrastructureError(
                    "AI provider rejected the API key", details={"operation": operation}
                ) from e
            except (groq.APIError, ValueError) as e:
                # json.JSONDecodeError is a ValueError subclass.
                logger.warning(
                    "llm_model_failed",
                    extra={"model": model, "operation": operation, "error": type(e).__name__},
                )
                failures.append({"model": model, "error": type(e).__name__})
        raise InfrastructureError(
            f"All AI models failed to {operation}",
            details={"attempts": failures},
        )

    def generate_questions(self, role: str, experience: str, count: int = 5) -> list[str]:
        """
        Generate interview questions using Groq API.

        Raises:
            InfrastructureError: If every configured model fails
        """
        prompt = f"""
        Generate {count} realistic technical interview questions
        for a {role} with {experience} of experience.
        Return a JSON object with a key "questions" containing a list of strings.
        Output ONLY the raw JSON.
        """
        return self._complete_json(
            prompt, lambda content, _model: parse_questions(content, count), "generate questions"
        )

    def evaluate_answer(self, question: str, answer: str) -> InterviewEvaluation:
        """
        Evaluate an interview answer using Groq API.

        Raises:
            InfrastructureError: If every configured model fails
        """
        prompt = f"""
        You are an expert technical interviewer.

        Question: {question}
        Candidate Answer: {answer}

        Provide a JSON object with the following keys:
        - "score": integer (1-10)
        - "strengths": list of strings
        - "weaknesses": list of strings
        - "improved_answer": string

        Output ONLY the raw JSON.
        """
        return self._complete_json(prompt, parse_evaluation, "evaluate answer")
