from alembic.autogenerate import compare_metadata
from alembic.migration import MigrationContext

from app.infrastructure.persistence.models import Base
from app.infrastructure.seed import DEMO_SESSIONS, seed


def test_migrations_match_orm_models(app):
    """If someone edits models.py without a migration, this fails."""
    with app.state.engine.connect() as connection:
        diff = compare_metadata(MigrationContext.configure(connection), Base.metadata)
    assert diff == []


def test_seed_creates_demo_sessions_once(app, client):
    with app.state.session_factory() as db:
        assert seed(db) == len(DEMO_SESSIONS)
    with app.state.session_factory() as db:
        assert seed(db) == 0

    page = client.get("/v1/sessions").json()
    assert page["total"] == len(DEMO_SESSIONS)
    statuses = sorted(item["status"] for item in page["items"])
    assert statuses == ["completed", "in_progress", "in_progress"]
