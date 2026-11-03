import pytest
from fastapi.testclient import TestClient


@pytest.fixture
def limited_client(settings):
    from main import create_app

    app = create_app(settings.model_copy(update={"rate_limit_per_minute": 2}))
    with TestClient(app) as client:
        yield client
    app.state.engine.dispose()


def test_llm_endpoints_return_429_with_retry_after(limited_client):
    body = {"role": "Dev", "experience": "1 year", "num_questions": 1}
    assert limited_client.post("/v1/sessions", json=body).status_code == 201
    assert limited_client.post("/v1/interview/generate-questions", json=body).status_code == 200

    blocked = limited_client.post("/v1/sessions", json=body)
    assert blocked.status_code == 429
    assert blocked.json()["error"]["code"] == "rate_limited"
    assert int(blocked.headers["Retry-After"]) >= 1

    # Reads do not spend LLM calls and are not limited.
    assert limited_client.get("/v1/sessions").status_code == 200
