"""Internal service-to-service authentication."""

import hmac
from typing import Annotated

from fastapi import Depends, Header, HTTPException, status

from app.config import Settings, get_settings

INTERNAL_TOKEN_HEADER = "X-Internal-Token"  # noqa: S105 - header name, not a secret


def require_internal_token(
    settings: Annotated[Settings, Depends(get_settings)],
    x_internal_token: Annotated[str | None, Header(alias=INTERNAL_TOKEN_HEADER)] = None,
) -> None:
    """Reject requests that do not carry the shared internal token (constant-time compare)."""
    expected = settings.internal_token.get_secret_value().encode()
    provided = (x_internal_token or "").encode()
    if not hmac.compare_digest(provided, expected):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail={"code": "INVALID_INTERNAL_TOKEN", "message": "Unauthorized"},
        )
