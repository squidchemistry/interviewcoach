LONG_ANSWER = (
    "First I would measure p95 latency, because guessing wastes time. For example, a missing "
    "index once made one query take 2 seconds; adding it cut latency by 90%. " + "Detail. " * 40
)


def create_session(client, n=3, role="Backend Engineer"):
    response = client.post(
        "/v1/sessions", json={"role": role, "experience": "2-5 years", "num_questions": n}
    )
    assert response.status_code == 201, response.text
    return response.json()


def test_health_and_readiness(client):
    assert client.get("/health").json()["status"] == "ok"
    assert client.get("/ping").json() == {"status": "alive"}
    ready = client.get("/ready")
    assert ready.status_code == 200
    assert ready.json() == {"status": "ready", "checks": {"database": "ok", "llm_provider": "mock"}}


class _DownEngine:
    def connect(self):
        from sqlalchemy.exc import OperationalError

        raise OperationalError("SELECT 1", {}, ConnectionRefusedError("connection refused"))


def test_readiness_reports_503_when_database_is_down(client, app):
    original = app.state.engine
    app.state.engine = _DownEngine()
    try:
        response = client.get("/ready")
    finally:
        app.state.engine = original
    assert response.status_code == 503
    assert response.json()["checks"]["database"] == "unavailable"


def test_create_and_get_session(client):
    created = create_session(client, n=4)
    assert created["status"] == "in_progress"
    assert created["total_questions"] == 4
    assert [q["position"] for q in created["questions"]] == [0, 1, 2, 3]

    fetched = client.get(f"/v1/sessions/{created['id']}")
    assert fetched.status_code == 200
    assert fetched.json()["questions"] == created["questions"]


def test_full_session_flow_persists_answers_and_summary(client):
    session = create_session(client, n=2)
    q1, q2 = session["questions"]

    first = client.post(
        f"/v1/sessions/{session['id']}/answers", json={"question_id": q1["id"], "answer": LONG_ANSWER}
    )
    assert first.status_code == 201
    body = first.json()
    assert body["session_status"] == "in_progress"
    assert body["next_question_id"] == q2["id"]
    assert 1 <= body["answer"]["evaluation"]["score"] <= 10

    second = client.post(
        f"/v1/sessions/{session['id']}/answers", json={"question_id": q2["id"], "answer": "Not sure."}
    )
    assert second.json()["session_status"] == "completed"

    stored = client.get(f"/v1/sessions/{session['id']}").json()
    assert stored["status"] == "completed"
    assert stored["completed_at"] is not None
    assert stored["questions"][0]["answer"]["text"] == LONG_ANSWER.strip()

    summary = client.get(f"/v1/sessions/{session['id']}/summary").json()
    assert summary["answered_questions"] == 2
    assert summary["best_score"] > summary["worst_score"]
    assert summary["average_score"] == (summary["best_score"] + summary["worst_score"]) / 2


def test_answering_twice_is_409_with_error_shape(client):
    session = create_session(client, n=2)
    payload = {"question_id": session["questions"][0]["id"], "answer": "An answer"}
    assert client.post(f"/v1/sessions/{session['id']}/answers", json=payload).status_code == 201

    again = client.post(f"/v1/sessions/{session['id']}/answers", json=payload)
    assert again.status_code == 409
    assert again.json()["error"]["code"] == "conflict"


def test_concurrent_duplicate_insert_is_rejected_by_database(app):
    """Two requests that both passed the in-memory check: the unique constraint decides."""
    import pytest

    from app.domain.interview.entities import InterviewEvaluation, InterviewSession
    from app.infrastructure.persistence.repository import SqlAlchemyInterviewSessionRepository
    from app.shared.errors import ConflictError

    factory = app.state.session_factory
    session = InterviewSession.start("Dev", "1 year", ["q1", "q2"])
    with factory() as db:
        SqlAlchemyInterviewSessionRepository(db).add(session)

    ev = InterviewEvaluation(score=5, strengths=[], weaknesses=[], improved_answer="")
    with factory() as db_a, factory() as db_b:
        repo_a, repo_b = SqlAlchemyInterviewSessionRepository(db_a), SqlAlchemyInterviewSessionRepository(db_b)
        copy_a, copy_b = repo_a.get(session.id), repo_b.get(session.id)
        qid = copy_a.questions[0].id
        copy_a.record_answer(qid, "from request A", ev)
        copy_b.record_answer(qid, "from request B", ev)
        repo_a.save_answer(copy_a, qid)
        with pytest.raises(ConflictError):
            repo_b.save_answer(copy_b, qid)


def test_unknown_ids_return_404(client):
    assert client.get("/v1/sessions/does-not-exist").json()["error"]["code"] == "not_found"
    assert client.get("/v1/sessions/does-not-exist/summary").status_code == 404
    session = create_session(client, n=1)
    response = client.post(
        f"/v1/sessions/{session['id']}/answers", json={"question_id": "nope", "answer": "x"}
    )
    assert response.status_code == 404


def test_validation_errors_use_the_common_shape(client):
    response = client.post("/v1/sessions", json={"role": "", "experience": "1 year", "num_questions": 50})
    assert response.status_code == 422
    error = response.json()["error"]
    assert error["code"] == "validation_error"
    fields = {e["field"] for e in error["details"]["errors"]}
    assert {"body.role", "body.num_questions"} <= fields


def test_list_sessions_is_paginated_newest_first(client):
    ids = [create_session(client, n=1, role=f"Role {i}")["id"] for i in range(5)]

    page1 = client.get("/v1/sessions?limit=2&offset=0").json()
    page3 = client.get("/v1/sessions?limit=2&offset=4").json()
    assert page1["total"] == 5
    assert [s["id"] for s in page1["items"]] == ids[::-1][:2]
    assert [s["id"] for s in page3["items"]] == [ids[0]]
    assert page1["items"][0]["average_score"] is None

    assert client.get("/v1/sessions?limit=0").status_code == 422
    assert client.get("/v1/sessions?limit=101").status_code == 422


def test_unknown_route_uses_error_shape(client):
    response = client.get("/v1/nope")
    assert response.status_code == 404
    assert response.json()["error"]["code"] == "not_found"


def test_legacy_stateless_endpoints_still_work(client):
    questions = client.post(
        "/v1/interview/generate-questions", json={"role": "Dev", "experience": "1 year"}
    ).json()["questions"]
    assert len(questions) == 5
    evaluation = client.post(
        "/v1/interview/evaluate", json={"question": questions[0], "answer": LONG_ANSWER}
    ).json()
    assert set(evaluation) == {"score", "strengths", "weaknesses", "improved_answer"}


def test_cors_allows_configured_origin_only(client):
    allowed = client.options(
        "/v1/sessions",
        headers={"Origin": "http://localhost:3000", "Access-Control-Request-Method": "POST"},
    )
    assert allowed.headers.get("access-control-allow-origin") == "http://localhost:3000"
    denied = client.options(
        "/v1/sessions",
        headers={"Origin": "https://evil.example", "Access-Control-Request-Method": "POST"},
    )
    assert "access-control-allow-origin" not in denied.headers
