"""
Shared fixtures.

API tests run against a real database created by the Alembic migrations:
- TEST_DATABASE_URL set (CI, docker): that Postgres database, reset per test.
- otherwise: a fresh SQLite file per test, so `pytest` works with no setup.
"""

import os
from pathlib import Path

import pytest
from alembic import command
from alembic.config import Config
from fastapi.testclient import TestClient

from app.infrastructure.config.settings import Settings

ROOT = Path(__file__).resolve().parent.parent


def alembic_config(database_url: str) -> Config:
    cfg = Config(str(ROOT / "alembic.ini"))
    cfg.set_main_option("script_location", str(ROOT / "migrations"))
    cfg.set_main_option("sqlalchemy.url", database_url.replace("%", "%%"))
    cfg.attributes["configure_logger"] = False
    return cfg


@pytest.fixture
def database_url(tmp_path: Path) -> str:
    return os.environ.get("TEST_DATABASE_URL") or f"sqlite:///{(tmp_path / 'test.db').as_posix()}"


@pytest.fixture
def migrated_db(database_url: str):
    cfg = alembic_config(Settings(DATABASE_URL=database_url).database_url)
    command.upgrade(cfg, "head")
    yield database_url
    command.downgrade(cfg, "base")


@pytest.fixture
def settings(migrated_db: str) -> Settings:
    return Settings(
        APP_ENV="test",
        DATABASE_URL=migrated_db,
        LLM_PROVIDER="mock",
        GROQ_API_KEY="",
        CORS_ORIGINS="http://localhost:3000",
        _env_file=None,
    )


@pytest.fixture
def app(settings: Settings):
    from main import create_app

    application = create_app(settings)
    yield application
    application.state.engine.dispose()


@pytest.fixture
def client(app) -> TestClient:
    with TestClient(app) as test_client:
        yield test_client
