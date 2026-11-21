import pytest
from pydantic import ValidationError

from app.config import Settings


def test_short_internal_token_is_rejected() -> None:
    with pytest.raises(ValidationError):
        Settings(AI_INTERNAL_TOKEN="too-short")


def test_token_is_not_exposed_in_repr() -> None:
    s = Settings(AI_INTERNAL_TOKEN="x" * 40)
    assert "x" * 40 not in repr(s)
