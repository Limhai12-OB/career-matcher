import pytest
from pydantic import ValidationError

from app.config import Settings


def test_short_internal_token_is_rejected() -> None:
    with pytest.raises(ValidationError):
        Settings(AI_INTERNAL_TOKEN="too-short")


def test_token_is_not_exposed_in_repr() -> None:
    s = Settings(AI_INTERNAL_TOKEN="x" * 40)
    assert "x" * 40 not in repr(s)


TOKEN = "x" * 40


@pytest.mark.parametrize("value", ["production", "prod", "PRODUCTION"])
def test_production_aliases(value: str) -> None:
    assert Settings(AI_INTERNAL_TOKEN=TOKEN, APP_ENV=value).environment == "production"


@pytest.mark.parametrize(
    ("overrides", "message"),
    [
        ({"AI_DOCS_ENABLED": True}, "AI_DOCS_ENABLED"),
        ({"AI_LOG_LEVEL": "DEBUG"}, "AI_LOG_LEVEL"),
        ({"AI_PROVIDER": "anthropic"}, "ANTHROPIC_API_KEY"),
    ],
)
def test_unsafe_production_settings_are_rejected(
    overrides: dict, message: str, monkeypatch
) -> None:
    monkeypatch.delenv("ANTHROPIC_API_KEY", raising=False)
    with pytest.raises(ValidationError, match=message):
        Settings(AI_INTERNAL_TOKEN=TOKEN, APP_ENV="production", **overrides)


def test_safe_production_and_permissive_dev_settings() -> None:
    Settings(
        AI_INTERNAL_TOKEN=TOKEN,
        APP_ENV="production",
        AI_PROVIDER="anthropic",
        ANTHROPIC_API_KEY="k",
    )
    Settings(AI_INTERNAL_TOKEN=TOKEN, APP_ENV="production", AI_PROVIDER="local")
    Settings(AI_INTERNAL_TOKEN=TOKEN, APP_ENV="dev", AI_DOCS_ENABLED=True, AI_LOG_LEVEL="DEBUG")


def test_validation_errors_never_echo_secret_values() -> None:
    secret = "short-secret-value"
    with pytest.raises(ValidationError) as short:
        Settings(AI_INTERNAL_TOKEN=secret)
    assert secret not in str(short.value)
    with pytest.raises(ValidationError) as unsafe:
        Settings(AI_INTERNAL_TOKEN=TOKEN, APP_ENV="production", AI_DOCS_ENABLED=True)
    assert TOKEN not in str(unsafe.value)
