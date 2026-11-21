"""Internal service-to-service authentication."""

import hmac
import json
from typing import Annotated

from fastapi import Depends, Header, HTTPException, status
from starlette.types import ASGIApp, Receive, Scope, Send

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


_UNAUTHORIZED = json.dumps(
    {"detail": {"code": "INVALID_INTERNAL_TOKEN", "message": "Unauthorized"}}
).encode()


class InternalTokenMiddleware:
    """Rejects /v1 requests without the internal token before any request body is read.

    The route dependency above still applies; this only stops unauthenticated callers from making
    the service receive and parse (potentially large) uploads.
    """

    def __init__(self, app: ASGIApp, token: str) -> None:
        self.app = app
        self.expected = token.encode()

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] == "http" and scope["path"].startswith("/v1/"):
            provided = b""
            for name, value in scope["headers"]:
                if name == INTERNAL_TOKEN_HEADER.lower().encode():
                    provided = value
                    break
            if not hmac.compare_digest(provided, self.expected):
                await send(
                    {
                        "type": "http.response.start",
                        "status": 401,
                        "headers": [(b"content-type", b"application/json")],
                    }
                )
                await send({"type": "http.response.body", "body": _UNAUTHORIZED})
                return
        await self.app(scope, receive, send)
