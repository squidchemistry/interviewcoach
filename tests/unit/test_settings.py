import pytest

from app.infrastructure.config.settings import Settings


@pytest.mark.parametrize(
    "raw",
    ["postgres://u:p@host:5432/db", "postgresql://u:p@host:5432/db"],
)
def test_platform_database_urls_get_a_driver(raw):
    assert Settings(DATABASE_URL=raw, _env_file=None).database_url == "postgresql+psycopg://u:p@host:5432/db"


def test_lists_are_parsed_from_comma_separated_env():
    s = Settings(CORS_ORIGINS="https://a.app, https://b.app,", GROQ_MODELS=" m1 ,m2", _env_file=None)
    assert s.cors_origin_list == ["https://a.app", "https://b.app"]
    assert s.groq_model_list == ["m1", "m2"]
