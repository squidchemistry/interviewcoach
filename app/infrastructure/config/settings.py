from functools import lru_cache
from typing import Literal

from pydantic import Field, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

Environment = Literal["development", "staging", "production", "test"]
LLMProvider = Literal["auto", "groq", "mock"]

# Tried in order. Groq retires models over time, so this is an env-configurable
# list (GROQ_MODELS) rather than one hard-coded name.
DEFAULT_GROQ_MODELS = "llama-3.3-70b-versatile,openai/gpt-oss-20b,llama-3.1-8b-instant"


class Settings(BaseSettings):
    """
    Application settings loaded from environment variables (and optional .env).

    This object is used across the app to avoid scattering direct
    environment access. It can be extended as new concerns appear
    (e.g. caching, messaging, feature flags).
    """

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        populate_by_name=True,
        extra="ignore",
    )

    app_name: str = Field(default="AI Interview Coach", alias="APP_NAME")
    environment: Environment = Field(default="development", alias="APP_ENV")
    version: str = Field(default="0.2.0", alias="APP_VERSION")
    enable_docs: bool = Field(default=True, alias="ENABLE_DOCS")

    # LLM settings. "auto" uses Groq when GROQ_API_KEY is set, otherwise the
    # deterministic mock provider (so tests and demos run without a key).
    llm_provider: LLMProvider = Field(default="auto", alias="LLM_PROVIDER")
    groq_api_key: str = Field(default="", alias="GROQ_API_KEY")
    groq_models: str = Field(default=DEFAULT_GROQ_MODELS, alias="GROQ_MODELS")
    llm_timeout_seconds: float = Field(default=20.0, alias="LLM_TIMEOUT_SECONDS")
    llm_max_retries: int = Field(default=2, alias="LLM_MAX_RETRIES")

    # Database settings
    database_url: str = Field(
        default="postgresql+psycopg://coach:coach@localhost:5432/interviewcoach",
        alias="DATABASE_URL",
    )
    db_pool_size: int = Field(default=5, alias="DB_POOL_SIZE")
    db_max_overflow: int = Field(default=5, alias="DB_MAX_OVERFLOW")
    db_pool_timeout_seconds: int = Field(default=10, alias="DB_POOL_TIMEOUT_SECONDS")
    db_connect_timeout_seconds: int = Field(default=5, alias="DB_CONNECT_TIMEOUT_SECONDS")
    seed_demo_data: bool = Field(default=False, alias="SEED_DEMO_DATA")

    # CORS settings
    cors_origins: str = Field(
        default="http://localhost:3000,http://localhost:5173",
        alias="CORS_ORIGINS"
    )

    @field_validator("database_url")
    @classmethod
    def normalise_database_url(cls, value: str) -> str:
        # Render and Heroku hand out postgres:// URLs; SQLAlchemy needs a driver name.
        for prefix in ("postgres://", "postgresql://"):
            if value.startswith(prefix):
                return "postgresql+psycopg://" + value[len(prefix):]
        return value

    @property
    def groq_model_list(self) -> list[str]:
        return [m.strip() for m in self.groq_models.split(",") if m.strip()]

    @property
    def cors_origin_list(self) -> list[str]:
        return [o.strip() for o in self.cors_origins.split(",") if o.strip()]

    @property
    def resolved_llm_provider(self) -> Literal["groq", "mock"]:
        if self.llm_provider == "auto":
            return "groq" if self.groq_api_key else "mock"
        return self.llm_provider


@lru_cache
def get_settings() -> Settings:
    """
    Return a cached Settings instance.

    Using an LRU cache ensures environment variables are loaded once per process,
    which keeps configuration access fast and predictable.
    """

    return Settings()
