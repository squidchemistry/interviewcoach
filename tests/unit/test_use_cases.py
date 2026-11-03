from datetime import datetime, timedelta, timezone

from app.application.interview.dto import (
    CreateSessionRequest,
    EvaluationRequest,
    QuestionRequest,
    SubmitAnswerRequest,
)
from app.application.interview.use_cases import (
    CreateSessionUseCase,
    EvaluateAnswerUseCase,
    GenerateQuestionsUseCase,
    GetSessionSummaryUseCase,
    GetSessionUseCase,
    ListSessionsUseCase,
    SubmitAnswerUseCase,
)
from app.shared.errors import ConflictError, InfrastructureError, NotFoundError, ValidationError
from tests.fakes import FakeAIService, FakeSessionRepository


def create(ai, repo, n=3):
    result = CreateSessionUseCase(ai, repo).execute(
        CreateSessionRequest(role="Backend Engineer", experience="2-5 years", num_questions=n)
    )
    assert result.is_ok, result.error
    return result.value


def test_generate_questions_returns_ai_output():
    result = GenerateQuestionsUseCase(FakeAIService()).execute(
        QuestionRequest(role="Dev", experience="1 year")
    )
    assert result.is_ok
    assert len(result.value.questions) == 5


def test_evaluate_maps_ai_failure_to_infrastructure_error():
    result = EvaluateAnswerUseCase(FakeAIService(fail=True)).execute(
        EvaluationRequest(question="q", answer="a")
    )
    assert isinstance(result.error, InfrastructureError)


def test_create_session_persists_questions():
    ai, repo = FakeAIService(), FakeSessionRepository()
    session = create(ai, repo, n=4)
    assert session.total_questions == 4
    assert session.status == "in_progress"
    assert session.id in repo.sessions


def test_create_session_ai_failure_persists_nothing():
    repo = FakeSessionRepository()
    result = CreateSessionUseCase(FakeAIService(fail=True), repo).execute(
        CreateSessionRequest(role="Dev", experience="1 year")
    )
    assert isinstance(result.error, InfrastructureError)
    assert repo.sessions == {}


def test_submit_answer_stores_evaluation_and_completes_session():
    ai, repo = FakeAIService(score=8), FakeSessionRepository()
    session = create(ai, repo, n=2)
    use_case = SubmitAnswerUseCase(ai, repo)

    first = use_case.execute(session.id, SubmitAnswerRequest(question_id=session.questions[0].id, answer="a1"))
    assert first.is_ok
    assert first.value.session_status == "in_progress"
    assert first.value.next_question_id == session.questions[1].id
    assert first.value.answer.evaluation.score == 8

    second = use_case.execute(session.id, SubmitAnswerRequest(question_id=session.questions[1].id, answer="a2"))
    assert second.value.session_status == "completed"
    assert second.value.next_question_id is None

    stored = GetSessionUseCase(repo).execute(session.id).value
    assert stored.answered_questions == 2


def test_submit_answer_twice_is_conflict_without_llm_call():
    ai, repo = FakeAIService(), FakeSessionRepository()
    session = create(ai, repo, n=2)
    request = SubmitAnswerRequest(question_id=session.questions[0].id, answer="a1")
    SubmitAnswerUseCase(ai, repo).execute(session.id, request)
    calls_before = len(ai.calls)

    result = SubmitAnswerUseCase(ai, repo).execute(session.id, request)
    assert isinstance(result.error, ConflictError)
    assert len(ai.calls) == calls_before  # rejected before spending an LLM call


def test_submit_answer_unknown_session_or_question():
    ai, repo = FakeAIService(), FakeSessionRepository()
    missing = SubmitAnswerUseCase(ai, repo).execute("missing", SubmitAnswerRequest(question_id="q", answer="a"))
    assert isinstance(missing.error, NotFoundError)

    session = create(ai, repo)
    bad_q = SubmitAnswerUseCase(ai, repo).execute(session.id, SubmitAnswerRequest(question_id="q", answer="a"))
    assert isinstance(bad_q.error, NotFoundError)


def test_submit_answer_llm_failure_leaves_question_unanswered():
    repo = FakeSessionRepository()
    session = create(FakeAIService(), repo)
    result = SubmitAnswerUseCase(FakeAIService(fail=True), repo).execute(
        session.id, SubmitAnswerRequest(question_id=session.questions[0].id, answer="a")
    )
    assert isinstance(result.error, InfrastructureError)
    assert GetSessionUseCase(repo).execute(session.id).value.answered_questions == 0


def test_list_sessions_paginates_newest_first():
    ai, repo = FakeAIService(), FakeSessionRepository()
    ids = [create(ai, repo).id for _ in range(5)]
    base = datetime(2026, 1, 1, tzinfo=timezone.utc)
    for i, session_id in enumerate(ids):
        repo.sessions[session_id].created_at = base + timedelta(minutes=i)

    page = ListSessionsUseCase(repo).execute(limit=2, offset=1).value
    assert page.total == 5
    assert [item.id for item in page.items] == [ids[3], ids[2]]


def test_list_sessions_rejects_bad_paging():
    repo = FakeSessionRepository()
    assert isinstance(ListSessionsUseCase(repo).execute(limit=0).error, ValidationError)
    assert isinstance(ListSessionsUseCase(repo).execute(limit=101).error, ValidationError)
    assert isinstance(ListSessionsUseCase(repo).execute(offset=-1).error, ValidationError)


def test_summary_use_case():
    ai, repo = FakeAIService(score=6), FakeSessionRepository()
    session = create(ai, repo, n=2)
    SubmitAnswerUseCase(ai, repo).execute(
        session.id, SubmitAnswerRequest(question_id=session.questions[0].id, answer="a")
    )
    summary = GetSessionSummaryUseCase(repo).execute(session.id).value
    assert summary.answered_questions == 1
    assert summary.average_score == 6
    assert isinstance(GetSessionSummaryUseCase(repo).execute("nope").error, NotFoundError)
