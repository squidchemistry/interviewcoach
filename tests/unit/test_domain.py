import pytest

from app.domain.interview.entities import (
    InterviewEvaluation,
    InterviewSession,
    SessionStatus,
)
from app.shared.errors import ConflictError, NotFoundError


def evaluation(score: int, strengths=None, weaknesses=None) -> InterviewEvaluation:
    return InterviewEvaluation(
        score=score,
        strengths=strengths or [],
        weaknesses=weaknesses or [],
        improved_answer="",
    )


def test_start_creates_ordered_questions_with_ids():
    session = InterviewSession.start("Backend Engineer", "2-5 years", ["q1", " ", "q2"])
    assert [q.text for q in session.questions] == ["q1", "q2"]
    assert [q.position for q in session.questions] == [0, 1]
    assert len({q.id for q in session.questions}) == 2
    assert session.status == SessionStatus.IN_PROGRESS


def test_start_requires_questions_and_role():
    with pytest.raises(ValueError):
        InterviewSession.start("Backend Engineer", "2-5 years", [])
    with pytest.raises(ValueError):
        InterviewSession.start(" ", "2-5 years", ["q1"])


def test_answering_every_question_completes_the_session():
    session = InterviewSession.start("Dev", "0-2 years", ["q1", "q2"])
    q1, q2 = session.questions
    session.record_answer(q1.id, "a1", evaluation(6))
    assert session.status == SessionStatus.IN_PROGRESS
    session.record_answer(q2.id, "a2", evaluation(8))
    assert session.status == SessionStatus.COMPLETED
    assert session.completed_at is not None


def test_cannot_answer_twice_or_after_completion():
    session = InterviewSession.start("Dev", "0-2 years", ["q1", "q2"])
    q1, q2 = session.questions
    session.record_answer(q1.id, "a1", evaluation(6))
    with pytest.raises(ConflictError):
        session.record_answer(q1.id, "again", evaluation(6))
    session.record_answer(q2.id, "a2", evaluation(6))
    with pytest.raises(ConflictError):
        session.ensure_can_answer(q2.id)


def test_unknown_question_is_not_found():
    session = InterviewSession.start("Dev", "0-2 years", ["q1"])
    with pytest.raises(NotFoundError):
        session.record_answer("nope", "a", evaluation(5))


def test_empty_answer_rejected():
    session = InterviewSession.start("Dev", "0-2 years", ["q1"])
    with pytest.raises(ValueError):
        session.record_answer(session.questions[0].id, "   ", evaluation(5))


def test_summary_aggregates_scores():
    session = InterviewSession.start("Dev", "0-2 years", ["q1", "q2", "q3"])
    q1, q2, _ = session.questions
    session.record_answer(q1.id, "a1", evaluation(8, ["clear"], ["short"]))
    session.record_answer(q2.id, "a2", evaluation(3, ["clear", "honest"], ["vague"]))
    summary = session.summary()
    assert summary.answered_questions == 2
    assert summary.total_questions == 3
    assert summary.average_score == 5.5
    assert (summary.best_score, summary.worst_score) == (8, 3)
    assert (summary.strong_answers, summary.weak_answers) == (1, 1)
    assert summary.top_strengths == ["clear", "honest"]
    assert summary.top_weaknesses == ["short", "vague"]


def test_summary_of_unanswered_session_has_no_scores():
    summary = InterviewSession.start("Dev", "0-2 years", ["q1"]).summary()
    assert summary.average_score is None
    assert summary.best_score is None


def test_evaluation_score_bounds():
    with pytest.raises(ValueError):
        evaluation(0)
    with pytest.raises(ValueError):
        evaluation(11)
