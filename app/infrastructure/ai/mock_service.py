"""
Deterministic mock implementation of the AIService port.

Used when no GROQ_API_KEY is configured (LLM_PROVIDER=auto) or when
LLM_PROVIDER=mock. Same input always gives the same output, so tests can
assert on it and the demo works offline.
"""

import re

from app.domain.interview.entities import InterviewEvaluation

_GENERIC_QUESTIONS = [
    "Walk me through a project you are proud of as a {role}. What trade-offs did you make?",
    "How would you debug a production issue that only happens under load?",
    "Describe how you would design a service that must stay available while a dependency is down.",
    "How do you decide what to test, and at which level (unit, integration, end-to-end)?",
    "Tell me about a time you disagreed with a technical decision. What did you do?",
    "How would you explain your last system's architecture to a new teammate in five minutes?",
    "What would you check first if an API's p95 latency doubled after a deploy?",
    "How do you keep secrets and credentials out of source control and logs?",
    "Describe a time you had to learn a new technology quickly to deliver something.",
    "With {experience} of experience, what is one skill you are deliberately improving, and how?",
]

_TOPIC_QUESTIONS = {
    "backend": "How would you design pagination for an API listing millions of records?",
    "frontend": "How do you keep a React app responsive when rendering a very large list?",
    "data": "How would you detect and handle data drift in a production model or pipeline?",
    "devops": "How would you roll back a bad deploy with zero downtime?",
    "product": "How do you decide what not to build in the next quarter?",
}

_STRUCTURE_HINTS = ("because", "for example", "for instance", "trade-off", "tradeoff", "first", "then")


class MockAIService:
    """Rule-based stand-in for an LLM. No network, no key."""

    name = "mock"

    def generate_questions(self, role: str, experience: str, count: int = 5) -> list[str]:
        role_lower = role.lower()
        topical = [q for key, q in _TOPIC_QUESTIONS.items() if key in role_lower]
        pool = topical + [q.format(role=role, experience=experience) for q in _GENERIC_QUESTIONS]
        return pool[:count]

    def evaluate_answer(self, question: str, answer: str) -> InterviewEvaluation:
        text = answer.lower()
        words = len(re.findall(r"\w+", answer))
        has_structure = sum(1 for hint in _STRUCTURE_HINTS if hint in text)
        has_numbers = bool(re.search(r"\d", answer))

        score = 2
        score += min(4, words // 25)  # up to +4 for a developed answer (100+ words)
        score += min(3, has_structure)  # reasoning and examples
        score += 1 if has_numbers else 0  # concrete, measurable detail
        score = max(1, min(10, score))

        strengths: list[str] = []
        weaknesses: list[str] = []
        if words >= 60:
            strengths.append("Answer is developed in enough depth")
        else:
            weaknesses.append("Answer is short; expand on your approach")
        if has_structure:
            strengths.append("Explains reasoning or gives an example")
        else:
            weaknesses.append("Add a concrete example and explain why")
        if has_numbers:
            strengths.append("Uses concrete, measurable details")
        else:
            weaknesses.append("Quantify the impact (numbers, scale, time saved)")

        improved = (
            "Situation: set the context in one sentence. "
            "Task: state what you were responsible for. "
            "Action: explain the steps you took and why, including one trade-off. "
            "Result: give a measurable outcome and what you learned."
        )
        return InterviewEvaluation(
            score=score,
            strengths=strengths,
            weaknesses=weaknesses,
            improved_answer=improved,
            model="mock",
        )
