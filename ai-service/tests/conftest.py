import pytest
from fastapi.testclient import TestClient

from app.config import Settings, get_settings
from app.main import create_app

TEST_TOKEN = "test-internal-token-0123456789abcdef"


@pytest.fixture
def settings() -> Settings:
    return Settings(AI_INTERNAL_TOKEN=TEST_TOKEN, APP_ENV="test")


@pytest.fixture
def client(settings: Settings) -> TestClient:
    app = create_app(settings)
    app.dependency_overrides[get_settings] = lambda: settings
    return TestClient(app)
