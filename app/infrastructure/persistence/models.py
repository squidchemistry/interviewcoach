"""
SQLAlchemy ORM models (persistence representation only).

These are mapped to and from the domain dataclasses in `repository.py`, so
the domain layer never imports SQLAlchemy.
"""

from datetime import datetime
from typing import Optional

from sqlalchemy import JSON, DateTime, ForeignKey, Integer, String, Text, UniqueConstraint
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, relationship


class Base(DeclarativeBase):
    pass


class InterviewSessionModel(Base):
    __tablename__ = "interview_sessions"

    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    role: Mapped[str] = mapped_column(String(100), nullable=False)
    experience: Mapped[str] = mapped_column(String(50), nullable=False)
    status: Mapped[str] = mapped_column(String(20), nullable=False, index=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, index=True)
    completed_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True))

    questions: Mapped[list["QuestionModel"]] = relationship(
        back_populates="session",
        cascade="all, delete-orphan",
        order_by="QuestionModel.position",
        lazy="selectin",
    )


class QuestionModel(Base):
    __tablename__ = "questions"
    __table_args__ = (UniqueConstraint("session_id", "position", name="uq_questions_session_position"),)

    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    session_id: Mapped[str] = mapped_column(
        ForeignKey("interview_sessions.id", ondelete="CASCADE"), nullable=False, index=True
    )
    position: Mapped[int] = mapped_column(Integer, nullable=False)
    text: Mapped[str] = mapped_column(Text, nullable=False)

    session: Mapped[InterviewSessionModel] = relationship(back_populates="questions")
    answer: Mapped[Optional["AnswerModel"]] = relationship(
        back_populates="question", cascade="all, delete-orphan", uselist=False, lazy="selectin"
    )


class AnswerModel(Base):
    __tablename__ = "answers"

    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    # unique=True: at most one answer per question, enforced by the database
    # so two concurrent submissions cannot both win.
    question_id: Mapped[str] = mapped_column(
        ForeignKey("questions.id", ondelete="CASCADE"), nullable=False, unique=True
    )
    text: Mapped[str] = mapped_column(Text, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)

    question: Mapped[QuestionModel] = relationship(back_populates="answer")
    evaluation: Mapped["EvaluationModel"] = relationship(
        back_populates="answer", cascade="all, delete-orphan", uselist=False, lazy="selectin"
    )


class EvaluationModel(Base):
    __tablename__ = "evaluations"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    answer_id: Mapped[str] = mapped_column(
        ForeignKey("answers.id", ondelete="CASCADE"), nullable=False, unique=True
    )
    score: Mapped[int] = mapped_column(Integer, nullable=False)
    strengths: Mapped[list] = mapped_column(JSON, nullable=False, default=list)
    weaknesses: Mapped[list] = mapped_column(JSON, nullable=False, default=list)
    improved_answer: Mapped[str] = mapped_column(Text, nullable=False, default="")
    model: Mapped[Optional[str]] = mapped_column(String(100))

    answer: Mapped[AnswerModel] = relationship(back_populates="evaluation")
