"""Health and status endpoints."""

from typing import Literal

from fastapi import APIRouter, Depends, Request
from pydantic import BaseModel

from app.security import require_internal_token

SERVICE_NAME = "ai-service"


class HealthResponse(BaseModel):
    status: Literal["ok"]
    service: str = SERVICE_NAME
    provider: str | None = None


public_router = APIRouter(tags=["health"])
internal_router = APIRouter(
    prefix="/v1", tags=["internal"], dependencies=[Depends(require_internal_token)]
)


@public_router.get("/health", response_model=HealthResponse)
def liveness() -> HealthResponse:
    """Liveness probe: the process is up and serving requests."""
    return HealthResponse(status="ok")


@public_router.get("/health/ready", response_model=HealthResponse)
def readiness() -> HealthResponse:
    """Readiness probe. LLM provider checks are added when the provider is integrated."""
    return HealthResponse(status="ok")


@internal_router.get("/status", response_model=HealthResponse)
def internal_status(request: Request) -> HealthResponse:
    """Token-protected status, used by the backend to verify connectivity and credentials."""
    return HealthResponse(status="ok", provider=request.app.state.cv_service.provider.name)
