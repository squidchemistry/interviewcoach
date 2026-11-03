"""
FastAPI routes for stateless question generation and evaluation.

Nothing here is persisted; see `sessions.py` for the stored workflow.
"""

from fastapi import APIRouter, Depends

from app.api.dependencies import get_ai_service
from app.application.interview.dto import (
    EvaluationRequest,
    EvaluationResponse,
    QuestionRequest,
    QuestionResponse,
)
from app.application.interview.interfaces import AIService
from app.application.interview.use_cases import (
    EvaluateAnswerUseCase,
    GenerateQuestionsUseCase,
)

router = APIRouter()


@router.post("/generate-questions", response_model=QuestionResponse)
def generate_questions(
    request: QuestionRequest,
    ai_service: AIService = Depends(get_ai_service),
) -> QuestionResponse:
    """
    Generate interview questions for a given role and experience level.

    No authentication required - open for trial use.
    """
    result = GenerateQuestionsUseCase(ai_service).execute(request)
    if result.is_err:
        raise result.error  # type: ignore[misc]
    return result.value  # type: ignore[return-value]


@router.post("/evaluate", response_model=EvaluationResponse)
def evaluate_answer(
    request: EvaluationRequest,
    ai_service: AIService = Depends(get_ai_service),
) -> EvaluationResponse:
    """
    Evaluate an interview answer and provide feedback.

    No authentication required - open for trial use.
    """
    result = EvaluateAnswerUseCase(ai_service).execute(request)
    if result.is_err:
        raise result.error  # type: ignore[misc]
    return result.value  # type: ignore[return-value]
