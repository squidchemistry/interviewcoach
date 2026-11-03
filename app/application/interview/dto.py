"""
Data Transfer Objects for the Interview domain.
"""

from datetime import datetime
from typing import Optional

from pydantic import BaseModel, Field

from app.domain.interview.entities import (
    InterviewEvaluation,
    InterviewSession,
    Question,
    SessionSummary,
)


class QuestionRequest(BaseModel):
    """Request DTO for generating interview questions."""

    role: str = Field(min_length=1, max_length=100)
    experience: str = Field(min_length=1, max_length=50)


class QuestionResponse(BaseModel):
    """Response DTO for interview questions."""

    questions: list[str]


class EvaluationRequest(BaseModel):
    """Request DTO for evaluating an interview answer."""

    question: str = Field(min_length=1, max_length=2000)
    answer: str = Field(min_length=1, max_length=10000)


class EvaluationResponse(BaseModel):
    """Response DTO for interview evaluation."""

    score: int
    strengths: list[str]
    weaknesses: list[str]
    improved_answer: str

    @classmethod
    def from_domain(cls, evaluation: InterviewEvaluation) -> "EvaluationResponse":
        return cls(
            score=evaluation.score,
            strengths=evaluation.strengths,
            weaknesses=evaluation.weaknesses,
            improved_answer=evaluation.improved_answer,
        )


# --- Persistent sessions -----------------------------------------------------


class CreateSessionRequest(BaseModel):
    role: str = Field(min_length=1, max_length=100, examples=["Backend Engineer"])
    experience: str = Field(min_length=1, max_length=50, examples=["2-5 years"])
    num_questions: int = Field(default=5, ge=1, le=10)


class SubmitAnswerRequest(BaseModel):
    question_id: str = Field(min_length=1, max_length=36)
    answer: str = Field(min_length=1, max_length=10000)


class AnswerResponse(BaseModel):
    id: str
    text: str
    created_at: datetime
    evaluation: EvaluationResponse


class SessionQuestionResponse(BaseModel):
    id: str
    position: int
    text: str
    answer: Optional[AnswerResponse] = None

    @classmethod
    def from_domain(cls, question: Question) -> "SessionQuestionResponse":
        answer = None
        if question.answer:
            answer = AnswerResponse(
                id=question.answer.id,
                text=question.answer.text,
                created_at=question.answer.created_at,
                evaluation=EvaluationResponse.from_domain(question.answer.evaluation),
            )
        return cls(id=question.id, position=question.position, text=question.text, answer=answer)


class SessionResponse(BaseModel):
    id: str
    role: str
    experience: str
    status: str
    created_at: datetime
    completed_at: Optional[datetime]
    total_questions: int
    answered_questions: int
    questions: list[SessionQuestionResponse]

    @classmethod
    def from_domain(cls, session: InterviewSession) -> "SessionResponse":
        return cls(
            id=session.id,
            role=session.role,
            experience=session.experience,
            status=session.status.value,
            created_at=session.created_at,
            completed_at=session.completed_at,
            total_questions=len(session.questions),
            answered_questions=session.answered_count,
            questions=[SessionQuestionResponse.from_domain(q) for q in session.questions],
        )


class SessionListItem(BaseModel):
    id: str
    role: str
    experience: str
    status: str
    created_at: datetime
    completed_at: Optional[datetime]
    total_questions: int
    answered_questions: int
    average_score: Optional[float]

    @classmethod
    def from_domain(cls, session: InterviewSession) -> "SessionListItem":
        return cls(
            id=session.id,
            role=session.role,
            experience=session.experience,
            status=session.status.value,
            created_at=session.created_at,
            completed_at=session.completed_at,
            total_questions=len(session.questions),
            answered_questions=session.answered_count,
            average_score=session.summary().average_score,
        )


class SessionPage(BaseModel):
    items: list[SessionListItem]
    total: int
    limit: int
    offset: int


class SubmitAnswerResponse(BaseModel):
    session_id: str
    question_id: str
    session_status: str
    answered_questions: int
    total_questions: int
    next_question_id: Optional[str]
    answer: AnswerResponse


class SessionSummaryResponse(BaseModel):
    session_id: str
    status: str
    total_questions: int
    answered_questions: int
    average_score: Optional[float]
    best_score: Optional[int]
    worst_score: Optional[int]
    strong_answers: int
    weak_answers: int
    top_strengths: list[str]
    top_weaknesses: list[str]

    @classmethod
    def from_domain(cls, summary: SessionSummary) -> "SessionSummaryResponse":
        return cls(
            session_id=summary.session_id,
            status=summary.status.value,
            total_questions=summary.total_questions,
            answered_questions=summary.answered_questions,
            average_score=summary.average_score,
            best_score=summary.best_score,
            worst_score=summary.worst_score,
            strong_answers=summary.strong_answers,
            weak_answers=summary.weak_answers,
            top_strengths=summary.top_strengths,
            top_weaknesses=summary.top_weaknesses,
        )
