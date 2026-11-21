"""Internal learning roadmap endpoint (backend-only, token protected)."""

from typing import Annotated

from fastapi import APIRouter, Depends, Request
from fastapi.responses import JSONResponse

from app.analysis.providers.base import ProviderError
from app.api.cv import _PROVIDER_MESSAGES, _PROVIDER_STATUS
from app.roadmap.schemas import RoadmapFacts, RoadmapResponse
from app.roadmap.service import RoadmapService
from app.security import require_internal_token

router = APIRouter(prefix="/v1", tags=["roadmap"], dependencies=[Depends(require_internal_token)])


def get_service(request: Request) -> RoadmapService:
    return request.app.state.roadmap_service


@router.post("/roadmap", response_model=RoadmapResponse, response_model_by_alias=True)
async def roadmap(facts: RoadmapFacts, service: Annotated[RoadmapService, Depends(get_service)]):
    try:
        return await service.roadmap(facts)
    except ProviderError as exc:
        return JSONResponse(
            status_code=_PROVIDER_STATUS[exc.code],
            content={"code": exc.code.value, "message": _PROVIDER_MESSAGES[exc.code]},
        )
