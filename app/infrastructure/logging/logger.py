import json
import logging
from datetime import datetime, timezone
from logging.config import dictConfig

from app.infrastructure.config.settings import Settings

# Attributes every LogRecord has; anything else came from `extra=` and is logged as a field.
_RESERVED = set(vars(logging.makeLogRecord({}))) | {"message", "asctime"}


class JsonFormatter(logging.Formatter):
    """
    One JSON object per line, so log platforms (Render, Datadog, Loki) can
    index fields such as `request_id`, `path` or `model` without regexes.
    """

    def format(self, record: logging.LogRecord) -> str:
        payload = {
            "ts": datetime.fromtimestamp(record.created, tz=timezone.utc).isoformat(),
            "level": record.levelname,
            "logger": record.name,
            "message": record.getMessage(),
        }
        for key, value in vars(record).items():
            if key not in _RESERVED and not key.startswith("_"):
                payload[key] = value
        if record.exc_info:
            payload["exc_info"] = self.formatException(record.exc_info)
        return json.dumps(payload, default=str)


def configure_logging(settings: Settings) -> None:
    """
    Configure application-wide logging (JSON lines to stdout).
    """

    level = logging.INFO if settings.environment != "production" else logging.WARNING

    dictConfig(
        {
            "version": 1,
            "disable_existing_loggers": False,
            "formatters": {
                "json": {"()": JsonFormatter},
            },
            "handlers": {
                "console": {
                    "class": "logging.StreamHandler",
                    "formatter": "json",
                },
            },
            "root": {
                "level": level,
                "handlers": ["console"],
            },
        }
    )


def get_logger(name: str) -> logging.Logger:
    """
    Convenience helper to get a logger with the configured settings applied.
    """

    return logging.getLogger(name)
