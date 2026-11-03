"""In-memory fakes for the application-layer ports."""

import copy
from typing import Optional

from app.domain.interview.entities import InterviewEvaluation, InterviewSession
from app.shared.errors import ConflictError, InfrastructureError


class FakeAIService:
    name = "fake"

    def __init__(self, *, fail: bool = False, score: int = 7) -> None:
        self.fail = fail
        self.score = score
        self.calls: list[str] = []

    def generate_questions(self, role: str, experience: str, count: int = 5) -> list[str]:
        self.calls.append("generate_questions")
        if self.fail:
            raise InfrastructureError("LLM down")
        return [f"{role} question {i + 1}" for i in range(count)]

    def evaluate_answer(self, question: str, answer: str) -> InterviewEvaluation:
        self.calls.append("evaluate_answer")
        if self.fail:
            raise InfrastructureError("LLM down")
        return InterviewEvaluation(
            score=self.score,
            strengths=["clear"],
            weaknesses=["needs numbers"],
            improved_answer="better answer",
            model="fake",
        )


class FakeSessionRepository:
    """Stores deep copies, like a real database would."""

    def __init__(self) -> None:
        self.sessions: dict[str, InterviewSession] = {}

    def add(self, session: InterviewSession) -> None:
        self.sessions[session.id] = copy.deepcopy(session)

    def get(self, session_id: str) -> Optional[InterviewSession]:
        session = self.sessions.get(session_id)
        return copy.deepcopy(session) if session else None

    def list(self, *, limit: int, offset: int) -> tuple[list[InterviewSession], int]:
        ordered = sorted(self.sessions.values(), key=lambda s: s.created_at, reverse=True)
        return [copy.deepcopy(s) for s in ordered[offset : offset + limit]], len(ordered)

    def save_answer(self, session: InterviewSession, question_id: str) -> None:
        stored = self.sessions[session.id]
        if stored.get_question(question_id).is_answered:
            raise ConflictError("Question has already been answered")
        self.sessions[session.id] = copy.deepcopy(session)
