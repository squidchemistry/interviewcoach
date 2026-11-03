"""
Ports (interfaces) for the Interview domain.

These define contracts that infrastructure must implement. The application
layer depends only on these protocols, never on Groq or SQLAlchemy directly.
"""

from typing import Optional, Protocol

from app.domain.interview.entities import InterviewEvaluation, InterviewSession


class AIService(Protocol):
    """
    Port for AI/LLM service operations.
    """

    name: str

    def generate_questions(self, role: str, experience: str, count: int = 5) -> list[str]:
        """Generate interview questions for a given role and experience level."""
        ...

    def evaluate_answer(self, question: str, answer: str) -> InterviewEvaluation:
        """Evaluate an interview answer and provide feedback."""
        ...


class InterviewSessionRepository(Protocol):
    """
    Port for persisting interview sessions (the aggregate root).
    """

    def add(self, session: InterviewSession) -> None:
        """Persist a new session with its questions."""
        ...

    def get(self, session_id: str) -> Optional[InterviewSession]:
        """Load a session with questions, answers and evaluations, or None."""
        ...

    def list(self, *, limit: int, offset: int) -> tuple[list[InterviewSession], int]:
        """Return one page of sessions (newest first) and the total count."""
        ...

    def save_answer(self, session: InterviewSession, question_id: str) -> None:
        """
        Persist the answer just recorded on `session` for `question_id`, plus
        the session's new status. Raises ConflictError if another request
        answered the same question first.
        """
        ...
