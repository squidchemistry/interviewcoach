"""
Use cases for the Interview domain.

Each use case returns a Result instead of raising, so the API layer decides
how errors become HTTP responses. Use cases only know the ports in
`interfaces.py`; concrete Groq/SQLAlchemy adapters are injected.
"""

from app.application.interview.dto import (
    CreateSessionRequest,
    EvaluationRequest,
    EvaluationResponse,
    QuestionRequest,
    QuestionResponse,
    SessionListItem,
    SessionPage,
    SessionQuestionResponse,
    SessionResponse,
    SessionSummaryResponse,
    SubmitAnswerRequest,
    SubmitAnswerResponse,
)
from app.application.interview.interfaces import AIService, InterviewSessionRepository
from app.domain.interview.entities import InterviewSession
from app.shared.errors import AppError, InfrastructureError, NotFoundError, ValidationError
from app.shared.result import Result

MAX_PAGE_SIZE = 100


def _wrap_unexpected(message: str, exc: Exception) -> AppError:
    if isinstance(exc, AppError):
        return exc
    if isinstance(exc, ValueError):
        return ValidationError(str(exc))
    return InfrastructureError(message, details={"error": str(exc)})


class GenerateQuestionsUseCase:
    """
    Use case: generate interview questions for a role and experience level.
    """

    def __init__(self, ai_service: AIService) -> None:
        self._ai_service = ai_service

    def execute(self, request: QuestionRequest) -> Result[QuestionResponse]:
        """
        Generate interview questions.

        Args:
            request: Contains role and experience level

        Returns:
            Result containing list of questions or an error
        """
        try:
            questions = self._ai_service.generate_questions(
                role=request.role, experience=request.experience
            )
            return Result.ok(QuestionResponse(questions=questions))
        except Exception as e:
            return Result.err(_wrap_unexpected("Failed to generate questions", e))


class EvaluateAnswerUseCase:
    """
    Use case: evaluate an interview answer.
    """

    def __init__(self, ai_service: AIService) -> None:
        self._ai_service = ai_service

    def execute(self, request: EvaluationRequest) -> Result[EvaluationResponse]:
        """
        Evaluate an interview answer.

        Args:
            request: Contains question and answer

        Returns:
            Result containing evaluation (score, strengths, weaknesses, improved_answer)
        """
        try:
            evaluation = self._ai_service.evaluate_answer(
                question=request.question, answer=request.answer
            )
            return Result.ok(EvaluationResponse.from_domain(evaluation))
        except Exception as e:
            return Result.err(_wrap_unexpected("Failed to evaluate answer", e))


class CreateSessionUseCase:
    """
    Use case: start a persistent interview session with AI-generated questions.
    """

    def __init__(self, ai_service: AIService, repository: InterviewSessionRepository) -> None:
        self._ai_service = ai_service
        self._repository = repository

    def execute(self, request: CreateSessionRequest) -> Result[SessionResponse]:
        try:
            texts = self._ai_service.generate_questions(
                role=request.role, experience=request.experience, count=request.num_questions
            )
            session = InterviewSession.start(
                role=request.role,
                experience=request.experience,
                question_texts=texts[: request.num_questions],
            )
            self._repository.add(session)
            return Result.ok(SessionResponse.from_domain(session))
        except Exception as e:
            return Result.err(_wrap_unexpected("Failed to create session", e))


class GetSessionUseCase:
    def __init__(self, repository: InterviewSessionRepository) -> None:
        self._repository = repository

    def execute(self, session_id: str) -> Result[SessionResponse]:
        session = self._repository.get(session_id)
        if session is None:
            return Result.err(NotFoundError("Session not found", details={"session_id": session_id}))
        return Result.ok(SessionResponse.from_domain(session))


class ListSessionsUseCase:
    def __init__(self, repository: InterviewSessionRepository) -> None:
        self._repository = repository

    def execute(self, *, limit: int = 20, offset: int = 0) -> Result[SessionPage]:
        if limit < 1 or limit > MAX_PAGE_SIZE:
            return Result.err(ValidationError(f"limit must be between 1 and {MAX_PAGE_SIZE}"))
        if offset < 0:
            return Result.err(ValidationError("offset must be >= 0"))
        sessions, total = self._repository.list(limit=limit, offset=offset)
        return Result.ok(
            SessionPage(
                items=[SessionListItem.from_domain(s) for s in sessions],
                total=total,
                limit=limit,
                offset=offset,
            )
        )


class SubmitAnswerUseCase:
    """
    Use case: evaluate an answer to one question of a session and store it.

    The (slow) LLM call happens before any write, so no database transaction
    is held open while waiting on the network.
    """

    def __init__(self, ai_service: AIService, repository: InterviewSessionRepository) -> None:
        self._ai_service = ai_service
        self._repository = repository

    def execute(self, session_id: str, request: SubmitAnswerRequest) -> Result[SubmitAnswerResponse]:
        try:
            session = self._repository.get(session_id)
            if session is None:
                raise NotFoundError("Session not found", details={"session_id": session_id})

            # Fail fast (404/409) before spending an LLM call.
            question = session.ensure_can_answer(request.question_id)
            evaluation = self._ai_service.evaluate_answer(question=question.text, answer=request.answer)

            session.record_answer(question.id, request.answer, evaluation)
            self._repository.save_answer(session, question.id)

            next_question = next((q for q in session.questions if not q.is_answered), None)
            answered = SessionQuestionResponse.from_domain(question)
            return Result.ok(
                SubmitAnswerResponse(
                    session_id=session.id,
                    question_id=question.id,
                    session_status=session.status.value,
                    answered_questions=session.answered_count,
                    total_questions=len(session.questions),
                    next_question_id=next_question.id if next_question else None,
                    answer=answered.answer,  # type: ignore[arg-type]
                )
            )
        except Exception as e:
            return Result.err(_wrap_unexpected("Failed to submit answer", e))


class GetSessionSummaryUseCase:
    def __init__(self, repository: InterviewSessionRepository) -> None:
        self._repository = repository

    def execute(self, session_id: str) -> Result[SessionSummaryResponse]:
        session = self._repository.get(session_id)
        if session is None:
            return Result.err(NotFoundError("Session not found", details={"session_id": session_id}))
        return Result.ok(SessionSummaryResponse.from_domain(session.summary()))
