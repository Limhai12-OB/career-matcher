"""Internal match explanation endpoint (backend-only, token protected)."""

from typing import Annotated

from fastapi import APIRouter, Depends, Request
from fastapi.responses import JSONResponse

from app.analysis.providers.base import ProviderError
from app.api.cv import _PROVIDER_MESSAGES, _PROVIDER_STATUS
from app.explain.schemas import ExplanationResponse, MatchFacts
from app.explain.service import ExplanationService
from app.security import require_internal_token

router = APIRouter(
    prefix="/v1/match", tags=["match"], dependencies=[Depends(require_internal_token)]
)


def get_service(request: Request) -> ExplanationService:
    return request.app.state.explanation_service


@router.post("/explain", response_model=ExplanationResponse, response_model_by_alias=True)
async def explain(facts: MatchFacts, service: Annotated[ExplanationService, Depends(get_service)]):
    try:
        return await service.explain(facts)
    except ProviderError as exc:
        return JSONResponse(
            status_code=_PROVIDER_STATUS[exc.code],
            content={"code": exc.code.value, "message": _PROVIDER_MESSAGES[exc.code]},
        )
