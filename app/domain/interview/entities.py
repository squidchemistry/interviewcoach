"""
Domain entities for the Interview domain.

These are plain dataclasses with no framework or database dependencies.
Identifiers are generated here (UUID4 strings) so an aggregate is complete
before it is persisted and the repository never has to hand IDs back.
"""

from __future__ import annotations

import uuid
from dataclasses import dataclass, field
from datetime import datetime, timezone
from enum import Enum
from typing import Optional

from app.shared.errors import ConflictError, NotFoundError


def _new_id() -> str:
    return str(uuid.uuid4())


def _utcnow() -> datetime:
    return datetime.now(timezone.utc)


class SessionStatus(str, Enum):
    IN_PROGRESS = "in_progress"
    COMPLETED = "completed"


@dataclass
class InterviewQuestion:
    """Domain entity representing a generated interview question (stateless use)."""

    question: str
    role: str
    experience_level: str

    def __post_init__(self) -> None:
        """Validate domain invariants."""
        if not self.question or not self.question.strip():
            raise ValueError("Question cannot be empty")
        if not self.role or not self.role.strip():
            raise ValueError("Role cannot be empty")


@dataclass
class InterviewEvaluation:
    """Domain entity representing an interview answer evaluation."""

    score: int
    strengths: list[str]
    weaknesses: list[str]
    improved_answer: str
    model: Optional[str] = None

    def __post_init__(self) -> None:
        """Validate domain invariants."""
        if not (1 <= self.score <= 10):
            raise ValueError("Score must be between 1 and 10")
        if not isinstance(self.strengths, list):
            raise ValueError("Strengths must be a list")
        if not isinstance(self.weaknesses, list):
            raise ValueError("Weaknesses must be a list")


@dataclass
class Answer:
    """A candidate's answer to one question, with its evaluation."""

    question_id: str
    text: str
    evaluation: InterviewEvaluation
    id: str = field(default_factory=_new_id)
    created_at: datetime = field(default_factory=_utcnow)


@dataclass
class Question:
    """A question that belongs to an interview session."""

    position: int
    text: str
    id: str = field(default_factory=_new_id)
    answer: Optional[Answer] = None

    @property
    def is_answered(self) -> bool:
        return self.answer is not None


@dataclass
class SessionSummary:
    """Aggregate results for a session (computed, never stored)."""

    session_id: str
    status: SessionStatus
    total_questions: int
    answered_questions: int
    average_score: Optional[float]
    best_score: Optional[int]
    worst_score: Optional[int]
    strong_answers: int  # score >= 7
    weak_answers: int  # score < 5
    top_strengths: list[str]
    top_weaknesses: list[str]


@dataclass
class InterviewSession:
    """
    Aggregate root: a practice interview with its questions and answers.

    All state changes go through methods on this class so invariants
    (one answer per question, no answers after completion) live in one place.
    """

    role: str
    experience: str
    questions: list[Question]
    id: str = field(default_factory=_new_id)
    status: SessionStatus = SessionStatus.IN_PROGRESS
    created_at: datetime = field(default_factory=_utcnow)
    completed_at: Optional[datetime] = None

    def __post_init__(self) -> None:
        if not self.role or not self.role.strip():
            raise ValueError("Role cannot be empty")
        if not self.experience or not self.experience.strip():
            raise ValueError("Experience cannot be empty")

    @classmethod
    def start(cls, role: str, experience: str, question_texts: list[str]) -> InterviewSession:
        texts = [t.strip() for t in question_texts if t and t.strip()]
        if not texts:
            raise ValueError("A session needs at least one question")
        questions = [Question(position=i, text=t) for i, t in enumerate(texts)]
        return cls(role=role.strip(), experience=experience.strip(), questions=questions)

    @property
    def answered_count(self) -> int:
        return sum(1 for q in self.questions if q.is_answered)

    def get_question(self, question_id: str) -> Question:
        for q in self.questions:
            if q.id == question_id:
                return q
        raise NotFoundError(
            "Question not found in this session",
            details={"session_id": self.id, "question_id": question_id},
        )

    def ensure_can_answer(self, question_id: str) -> Question:
        """Check an answer would be accepted, without changing state."""
        if self.status == SessionStatus.COMPLETED:
            raise ConflictError("Session is already completed", details={"session_id": self.id})
        question = self.get_question(question_id)
        if question.is_answered:
            raise ConflictError(
                "Question has already been answered",
                details={"session_id": self.id, "question_id": question_id},
            )
        return question

    def record_answer(
        self, question_id: str, text: str, evaluation: InterviewEvaluation
    ) -> Answer:
        if not text or not text.strip():
            raise ValueError("Answer cannot be empty")
        question = self.ensure_can_answer(question_id)
        answer = Answer(question_id=question.id, text=text.strip(), evaluation=evaluation)
        question.answer = answer
        if self.answered_count == len(self.questions):
            self.status = SessionStatus.COMPLETED
            self.completed_at = answer.created_at
        return answer

    def summary(self) -> SessionSummary:
        evaluations = [q.answer.evaluation for q in self.questions if q.answer]
        scores = [e.score for e in evaluations]
        return SessionSummary(
            session_id=self.id,
            status=self.status,
            total_questions=len(self.questions),
            answered_questions=len(scores),
            average_score=round(sum(scores) / len(scores), 2) if scores else None,
            best_score=max(scores) if scores else None,
            worst_score=min(scores) if scores else None,
            strong_answers=sum(1 for s in scores if s >= 7),
            weak_answers=sum(1 for s in scores if s < 5),
            top_strengths=_first_unique([s for e in evaluations for s in e.strengths], 5),
            top_weaknesses=_first_unique([w for e in evaluations for w in e.weaknesses], 5),
        )


def _first_unique(items: list[str], limit: int) -> list[str]:
    seen: list[str] = []
    for item in items:
        if item not in seen:
            seen.append(item)
        if len(seen) == limit:
            break
    return seen
