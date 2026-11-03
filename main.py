from typing import Optional

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.error_handlers import register_error_handlers
from app.api.routes import api_router, health_router
from app.infrastructure.ai import build_ai_service
from app.infrastructure.config.settings import Settings, get_settings
from app.infrastructure.logging.logger import configure_logging
from app.infrastructure.persistence.database import build_engine, build_session_factory


def create_app(settings: Optional[Settings] = None) -> FastAPI:
    """
    Application factory and composition root.

    This wires together:
    - Settings and logging configuration.
    - The database engine (one connection pool per process) and AI provider.
    - API routers and global exception handlers.
    - CORS middleware for frontend integration.

    Tests call `create_app(Settings(...))` to point at a test database.
    """
    settings = settings or get_settings()
    configure_logging(settings)

    app = FastAPI(
        title="AI Interview Coach",
        version=settings.version,
        docs_url="/docs" if settings.enable_docs else None,
        redoc_url="/redoc" if settings.enable_docs else None,
    )

    engine = build_engine(settings)
    app.state.settings = settings
    app.state.engine = engine
    app.state.session_factory = build_session_factory(engine)
    app.state.ai_service = build_ai_service(settings)

    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origin_list,
        allow_credentials=False,
        allow_methods=["GET", "POST", "OPTIONS"],
        allow_headers=["*"],
    )

    app.include_router(health_router, tags=["health"])
    app.include_router(api_router, prefix="/v1")

    register_error_handlers(app)

    return app


app = create_app()
