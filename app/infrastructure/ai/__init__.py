"""
AI service implementations.
"""

from app.application.interview.interfaces import AIService
from app.infrastructure.config.settings import Settings


def build_ai_service(settings: Settings) -> AIService:
    """Pick the AI provider from settings (LLM_PROVIDER / GROQ_API_KEY)."""
    if settings.resolved_llm_provider == "mock":
        from .mock_service import MockAIService

        return MockAIService()

    from .groq_service import GroqAIService

    return GroqAIService(
        api_key=settings.groq_api_key,
        models=settings.groq_model_list,
        timeout_seconds=settings.llm_timeout_seconds,
        max_retries=settings.llm_max_retries,
    )
