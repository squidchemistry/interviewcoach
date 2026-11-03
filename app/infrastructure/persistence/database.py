"""
Engine and session factory.

One engine (and so one connection pool) per process. Request handlers get a
short-lived Session from the factory, so the app tier itself stays stateless.
"""

from sqlalchemy import Engine, create_engine, event, text
from sqlalchemy.orm import Session, sessionmaker

from app.infrastructure.config.settings import Settings


def build_engine(settings: Settings) -> Engine:
    url = settings.database_url
    if url.startswith("sqlite"):
        # SQLite is only used for fast local tests; it has no real pool.
        engine = create_engine(url, connect_args={"check_same_thread": False})

        @event.listens_for(engine, "connect")
        def _enable_foreign_keys(dbapi_connection, _record):  # pragma: no cover - trivial
            cursor = dbapi_connection.cursor()
            cursor.execute("PRAGMA foreign_keys=ON")
            cursor.close()

        return engine

    return create_engine(
        url,
        pool_size=settings.db_pool_size,
        max_overflow=settings.db_max_overflow,
        pool_timeout=settings.db_pool_timeout_seconds,
        pool_pre_ping=True,  # drop connections the server closed (e.g. after idle)
        pool_recycle=300,
        # Without this a dead host can hang /ready (and requests) for minutes.
        connect_args={"connect_timeout": settings.db_connect_timeout_seconds},
    )


def build_session_factory(engine: Engine) -> sessionmaker[Session]:
    return sessionmaker(bind=engine, autoflush=False, expire_on_commit=False)


def ping(engine: Engine) -> None:
    """Raise if the database is unreachable."""
    with engine.connect() as connection:
        connection.execute(text("SELECT 1"))
