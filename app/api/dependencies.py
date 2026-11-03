"""
Common API-layer dependencies.

The composition root (`main.create_app`) puts long-lived, stateless objects
on `app.state` (DB session factory, AI service). These dependencies hand out
per-request objects built from them, and tests can override any of them.
"""

from collections.abc import Iterator

from fastapi import Depends, Request
from sqlalchemy.orm import Session

from app.application.interview.interfaces import AIService, InterviewSessionRepository
from app.infrastructure.persistence.repository import SqlAlchemyInterviewSessionRepository


def get_db(request: Request) -> Iterator[Session]:
    db = request.app.state.session_factory()
    try:
        yield db
    finally:
        db.close()


def get_session_repository(db: Session = Depends(get_db)) -> InterviewSessionRepository:
    return SqlAlchemyInterviewSessionRepository(db)


def get_ai_service(request: Request) -> AIService:
    return request.app.state.ai_service
