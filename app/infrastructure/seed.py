"""
Demo data: a few completed and in-progress sessions, so the history page and
summary endpoint have something to show on a fresh database.

Always uses the mock provider (deterministic, no API cost) and is idempotent:
it does nothing if any session already exists.

    python -m app.infrastructure.seed
"""

import logging

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.application.interview.dto import CreateSessionRequest, SubmitAnswerRequest
from app.application.interview.use_cases import CreateSessionUseCase, SubmitAnswerUseCase
from app.infrastructure.ai.mock_service import MockAIService
from app.infrastructure.config.settings import get_settings
from app.infrastructure.persistence.database import build_engine, build_session_factory
from app.infrastructure.persistence.models import InterviewSessionModel
from app.infrastructure.persistence.repository import SqlAlchemyInterviewSessionRepository

logger = logging.getLogger(__name__)

DEMO_SESSIONS = [
    {
        "role": "Backend Engineer",
        "experience": "2-5 years",
        "answers": [
            "I would use keyset pagination because offset scans get slower as the offset grows. "
            "For example, on a table with 10 million rows I would order by (created_at, id) and "
            "pass the last seen pair as a cursor, then add a composite index on those columns.",
            "First I would check dashboards for p95 latency and error rate, then compare with the "
            "last deploy. For example, I once found a missing index that turned a 20 ms query "
            "into 2 seconds under load.",
            "I would add a timeout, a retry with backoff, and a circuit breaker, then serve a cached "
            "or degraded response because availability matters more than freshness here.",
        ],
    },
    {
        "role": "Frontend Developer",
        "experience": "0-2 years",
        "answers": [
            "I would virtualize the list so only visible rows render.",
            "I test the important user flows.",
        ],
    },
    {"role": "Data Scientist", "experience": "5-10 years", "answers": []},
]


def seed(db: Session) -> int:
    """Insert demo sessions if the table is empty. Returns how many were created."""
    existing = db.scalar(select(func.count()).select_from(InterviewSessionModel)) or 0
    if existing:
        return 0
    ai = MockAIService()
    repository = SqlAlchemyInterviewSessionRepository(db)
    created = 0
    for demo in DEMO_SESSIONS:
        num_questions = max(3, len(demo["answers"]))
        result = CreateSessionUseCase(ai, repository).execute(
            CreateSessionRequest(role=demo["role"], experience=demo["experience"], num_questions=num_questions)
        )
        session = result.value
        assert session is not None, result.error
        for question, answer in zip(session.questions, demo["answers"], strict=False):
            SubmitAnswerUseCase(ai, repository).execute(
                session.id, SubmitAnswerRequest(question_id=question.id, answer=answer)
            )
        created += 1
    return created


def main() -> None:
    engine = build_engine(get_settings())
    with build_session_factory(engine)() as db:
        created = seed(db)
    print(f"seed: created {created} demo session(s)")


if __name__ == "__main__":
    main()
