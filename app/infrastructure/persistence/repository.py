"""
SQLAlchemy implementation of the InterviewSessionRepository port.
"""

from datetime import datetime, timezone
from typing import Optional

from sqlalchemy import func, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.domain.interview.entities import (
    Answer,
    InterviewEvaluation,
    InterviewSession,
    Question,
    SessionStatus,
)
from app.infrastructure.persistence.models import (
    AnswerModel,
    EvaluationModel,
    InterviewSessionModel,
    QuestionModel,
)
from app.shared.errors import ConflictError


def _aware(value: Optional[datetime]) -> Optional[datetime]:
    # SQLite drops tzinfo; Postgres keeps it. Normalise to UTC-aware.
    if value is not None and value.tzinfo is None:
        return value.replace(tzinfo=timezone.utc)
    return value


def _to_domain(row: InterviewSessionModel) -> InterviewSession:
    questions = []
    for q in row.questions:
        answer = None
        if q.answer is not None:
            ev = q.answer.evaluation
            answer = Answer(
                id=q.answer.id,
                question_id=q.id,
                text=q.answer.text,
                created_at=_aware(q.answer.created_at),  # type: ignore[arg-type]
                evaluation=InterviewEvaluation(
                    score=ev.score,
                    strengths=list(ev.strengths or []),
                    weaknesses=list(ev.weaknesses or []),
                    improved_answer=ev.improved_answer,
                    model=ev.model,
                ),
            )
        questions.append(Question(id=q.id, position=q.position, text=q.text, answer=answer))
    return InterviewSession(
        id=row.id,
        role=row.role,
        experience=row.experience,
        questions=questions,
        status=SessionStatus(row.status),
        created_at=_aware(row.created_at),  # type: ignore[arg-type]
        completed_at=_aware(row.completed_at),
    )


def _answer_row(answer: Answer) -> AnswerModel:
    ev = answer.evaluation
    return AnswerModel(
        id=answer.id,
        question_id=answer.question_id,
        text=answer.text,
        created_at=answer.created_at,
        evaluation=EvaluationModel(
            score=ev.score,
            strengths=ev.strengths,
            weaknesses=ev.weaknesses,
            improved_answer=ev.improved_answer,
            model=ev.model,
        ),
    )


class SqlAlchemyInterviewSessionRepository:
    def __init__(self, db: Session) -> None:
        self._db = db

    def add(self, session: InterviewSession) -> None:
        row = InterviewSessionModel(
            id=session.id,
            role=session.role,
            experience=session.experience,
            status=session.status.value,
            created_at=session.created_at,
            completed_at=session.completed_at,
            questions=[
                QuestionModel(
                    id=q.id,
                    position=q.position,
                    text=q.text,
                    answer=_answer_row(q.answer) if q.answer else None,
                )
                for q in session.questions
            ],
        )
        self._db.add(row)
        self._db.commit()

    def get(self, session_id: str) -> Optional[InterviewSession]:
        # Relationships are lazy="selectin": 1 query per level, not per row.
        row = self._db.get(InterviewSessionModel, session_id)
        return _to_domain(row) if row else None

    def list(self, *, limit: int, offset: int) -> tuple[list[InterviewSession], int]:
        total = self._db.scalar(select(func.count()).select_from(InterviewSessionModel)) or 0
        rows = self._db.scalars(
            select(InterviewSessionModel)
            .order_by(InterviewSessionModel.created_at.desc(), InterviewSessionModel.id)
            .limit(limit)
            .offset(offset)
        ).all()
        return [_to_domain(r) for r in rows], total

    def save_answer(self, session: InterviewSession, question_id: str) -> None:
        question = next(q for q in session.questions if q.id == question_id)
        if question.answer is None:
            raise ValueError("No answer recorded for this question")
        row = self._db.get(InterviewSessionModel, session.id)
        if row is None:
            raise ValueError("Session does not exist")
        row.status = session.status.value
        row.completed_at = session.completed_at
        # Attach through the relationship (not db.add) so the identity map
        # stays consistent if this Session is used for more reads.
        question_row = next(q for q in row.questions if q.id == question_id)
        question_row.answer = _answer_row(question.answer)
        try:
            self._db.commit()
        except IntegrityError as e:
            self._db.rollback()
            raise ConflictError(
                "Question has already been answered",
                details={"session_id": session.id, "question_id": question_id},
            ) from e
