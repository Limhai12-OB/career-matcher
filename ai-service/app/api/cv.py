"""Internal CV analysis endpoint (backend-only, token protected)."""

from typing import Annotated

from fastapi import APIRouter, Depends, File, Request, UploadFile
from fastapi.responses import JSONResponse

from app.analysis.providers.base import ProviderError, ProviderErrorCode
from app.analysis.schemas import CvAnalysisResponse, ErrorResponse
from app.analysis.service import CvAnalysisService
from app.documents.errors import DocumentError, DocumentErrorCode
from app.security import require_internal_token

router = APIRouter(prefix="/v1/cv", tags=["cv"], dependencies=[Depends(require_internal_token)])

_PROVIDER_STATUS = {
    ProviderErrorCode.AI_NOT_CONFIGURED: 503,
    ProviderErrorCode.AI_UNAVAILABLE: 503,
    ProviderErrorCode.AI_RATE_LIMITED: 503,
    ProviderErrorCode.AI_TIMEOUT: 504,
    ProviderErrorCode.AI_REFUSED: 502,
    ProviderErrorCode.AI_INVALID_OUTPUT: 502,
}
_PROVIDER_MESSAGES = {
    ProviderErrorCode.AI_NOT_CONFIGURED: "The AI provider is not configured.",
    ProviderErrorCode.AI_UNAVAILABLE: "The AI provider is unavailable.",
    ProviderErrorCode.AI_RATE_LIMITED: "The AI provider is rate limiting requests.",
    ProviderErrorCode.AI_TIMEOUT: "The AI provider timed out.",
    ProviderErrorCode.AI_REFUSED: "The AI provider declined to analyze this document.",
    ProviderErrorCode.AI_INVALID_OUTPUT: "The AI provider returned an invalid result.",
}


def get_service(request: Request) -> CvAnalysisService:
    return request.app.state.cv_service


@router.post(
    "/analyze",
    response_model=CvAnalysisResponse,
    response_model_by_alias=True,
    responses={
        413: {"model": ErrorResponse},
        422: {"model": ErrorResponse},
        502: {"model": ErrorResponse},
        503: {"model": ErrorResponse},
        504: {"model": ErrorResponse},
    },
)
async def analyze_cv(
    file: Annotated[UploadFile, File()],
    service: Annotated[CvAnalysisService, Depends(get_service)],
    request: Request,
):
    limit = request.app.state.settings.max_upload_bytes
    data = await file.read(limit + 1)
    try:
        if len(data) > limit:
            raise DocumentError(DocumentErrorCode.DOCUMENT_TOO_LARGE)
        return await service.analyze(data)
    except DocumentError as exc:
        status = 413 if exc.code == DocumentErrorCode.DOCUMENT_TOO_LARGE else 422
        return JSONResponse(
            status_code=status, content={"code": exc.code.value, "message": exc.message}
        )
    except ProviderError as exc:
        return JSONResponse(
            status_code=_PROVIDER_STATUS[exc.code],
            content={"code": exc.code.value, "message": _PROVIDER_MESSAGES[exc.code]},
        )
    finally:
        await file.close()
