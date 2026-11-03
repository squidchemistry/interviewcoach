from fastapi import APIRouter

from .health import router as health_router
from .interview import router as interview_router
from .sessions import router as sessions_router

api_router = APIRouter()

# Group routes by business capability (domain).
api_router.include_router(interview_router, prefix="/interview", tags=["interview"])
api_router.include_router(sessions_router, prefix="/sessions", tags=["sessions"])

__all__ = ["api_router", "health_router"]
