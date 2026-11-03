"""
FastAPI routes for persistent interview sessions.
"""

from fastapi import APIRouter, Depends, Query, status

from app.api.dependencies import get_ai_service, get_session_repository
from app.application.interview.dto import (
    CreateSessionRequest,
    SessionPage,
    SessionResponse,
    SessionSummaryResponse,
    SubmitAnswerRequest,
    SubmitAnswerResponse,
)
from app.application.interview.interfaces import AIService, InterviewSessionRepository
from app.application.interview.use_cases import (
    MAX_PAGE_SIZE,
    CreateSessionUseCase,
    GetSessionSummaryUseCase,
    GetSessionUseCase,
    ListSessionsUseCase,
    SubmitAnswerUseCase,
)
from app.shared.result import Result

router = APIRouter()


def _unwrap(result: Result):
    if result.is_err:
        raise result.error  # type: ignore[misc]
    return result.value


@router.post("", response_model=SessionResponse, status_code=status.HTTP_201_CREATED)
def create_session(
    request: CreateSessionRequest,
    ai_service: AIService = Depends(get_ai_service),
    repository: InterviewSessionRepository = Depends(get_session_repository),
) -> SessionResponse:
    """Start a session: generate questions and store them."""
    return _unwrap(CreateSessionUseCase(ai_service, repository).execute(request))


@router.get("", response_model=SessionPage)
def list_sessions(
    limit: int = Query(default=20, ge=1, le=MAX_PAGE_SIZE),
    offset: int = Query(default=0, ge=0),
    repository: InterviewSessionRepository = Depends(get_session_repository),
) -> SessionPage:
    """List sessions, newest first (offset pagination)."""
    return _unwrap(ListSessionsUseCase(repository).execute(limit=limit, offset=offset))


@router.get("/{session_id}", response_model=SessionResponse)
def get_session(
    session_id: str,
    repository: InterviewSessionRepository = Depends(get_session_repository),
) -> SessionResponse:
    """Get one session with its questions, answers and evaluations."""
    return _unwrap(GetSessionUseCase(repository).execute(session_id))


@router.post(
    "/{session_id}/answers",
    response_model=SubmitAnswerResponse,
    status_code=status.HTTP_201_CREATED,
)
def submit_answer(
    session_id: str,
    request: SubmitAnswerRequest,
    ai_service: AIService = Depends(get_ai_service),
    repository: InterviewSessionRepository = Depends(get_session_repository),
) -> SubmitAnswerResponse:
    """Answer one question; the answer is evaluated and stored."""
    return _unwrap(SubmitAnswerUseCase(ai_service, repository).execute(session_id, request))


@router.get("/{session_id}/summary", response_model=SessionSummaryResponse)
def get_session_summary(
    session_id: str,
    repository: InterviewSessionRepository = Depends(get_session_repository),
) -> SessionSummaryResponse:
    """Aggregate results for a session (works on partial sessions too)."""
    return _unwrap(GetSessionSummaryUseCase(repository).execute(session_id))
