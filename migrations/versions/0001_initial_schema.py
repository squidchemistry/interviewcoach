"""Initial schema: interview sessions, questions, answers, evaluations

Revision ID: 0001
Revises:
Create Date: 2026-11-03
"""
from collections.abc import Sequence
from typing import Union

import sqlalchemy as sa
from alembic import op

revision: str = "0001"
down_revision: Union[str, None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        "interview_sessions",
        sa.Column("id", sa.String(length=36), primary_key=True),
        sa.Column("role", sa.String(length=100), nullable=False),
        sa.Column("experience", sa.String(length=50), nullable=False),
        sa.Column("status", sa.String(length=20), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("completed_at", sa.DateTime(timezone=True), nullable=True),
    )
    op.create_index("ix_interview_sessions_status", "interview_sessions", ["status"])
    op.create_index("ix_interview_sessions_created_at", "interview_sessions", ["created_at"])

    op.create_table(
        "questions",
        sa.Column("id", sa.String(length=36), primary_key=True),
        sa.Column(
            "session_id",
            sa.String(length=36),
            sa.ForeignKey("interview_sessions.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("position", sa.Integer(), nullable=False),
        sa.Column("text", sa.Text(), nullable=False),
        sa.UniqueConstraint("session_id", "position", name="uq_questions_session_position"),
    )
    op.create_index("ix_questions_session_id", "questions", ["session_id"])

    op.create_table(
        "answers",
        sa.Column("id", sa.String(length=36), primary_key=True),
        sa.Column(
            "question_id",
            sa.String(length=36),
            sa.ForeignKey("questions.id", ondelete="CASCADE"),
            nullable=False,
            unique=True,
        ),
        sa.Column("text", sa.Text(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
    )

    op.create_table(
        "evaluations",
        sa.Column("id", sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column(
            "answer_id",
            sa.String(length=36),
            sa.ForeignKey("answers.id", ondelete="CASCADE"),
            nullable=False,
            unique=True,
        ),
        sa.Column("score", sa.Integer(), nullable=False),
        sa.Column("strengths", sa.JSON(), nullable=False),
        sa.Column("weaknesses", sa.JSON(), nullable=False),
        sa.Column("improved_answer", sa.Text(), nullable=False),
        sa.Column("model", sa.String(length=100), nullable=True),
    )


def downgrade() -> None:
    op.drop_table("evaluations")
    op.drop_table("answers")
    op.drop_index("ix_questions_session_id", table_name="questions")
    op.drop_table("questions")
    op.drop_index("ix_interview_sessions_created_at", table_name="interview_sessions")
    op.drop_index("ix_interview_sessions_status", table_name="interview_sessions")
    op.drop_table("interview_sessions")
