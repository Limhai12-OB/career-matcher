from fastapi.testclient import TestClient

from tests.conftest import TEST_TOKEN


def test_liveness_is_public(client: TestClient) -> None:
    res = client.get("/health")
    assert res.status_code == 200
    assert res.json()["status"] == "ok"
    assert res.json()["service"] == "ai-service"


def test_readiness_is_public(client: TestClient) -> None:
    assert client.get("/health/ready").status_code == 200


def test_internal_status_requires_token(client: TestClient) -> None:
    res = client.get("/v1/status")
    assert res.status_code == 401
    assert res.json()["detail"]["code"] == "INVALID_INTERNAL_TOKEN"


def test_internal_status_rejects_wrong_token(client: TestClient) -> None:
    res = client.get("/v1/status", headers={"X-Internal-Token": "wrong"})
    assert res.status_code == 401


def test_internal_status_accepts_valid_token(client: TestClient) -> None:
    res = client.get("/v1/status", headers={"X-Internal-Token": TEST_TOKEN})
    assert res.status_code == 200
    assert res.json()["status"] == "ok"


def test_request_id_is_echoed_or_generated(client: TestClient) -> None:
    assert (
        client.get("/health", headers={"X-Request-Id": "abc12345-x"}).headers["X-Request-Id"]
        == "abc12345-x"
    )
    generated = client.get("/health", headers={"X-Request-Id": "bad id\n"}).headers["X-Request-Id"]
    assert generated != "bad id\n"
    assert len(generated) == 36


def test_docs_disabled_by_default(client: TestClient) -> None:
    assert client.get("/docs").status_code == 404
    assert client.get("/openapi.json").status_code == 404
