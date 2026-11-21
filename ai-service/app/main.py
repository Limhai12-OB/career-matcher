"""FastAPI application factory for the internal AI service."""

import logging

from fastapi import FastAPI

from app.analysis.factory import build_provider
from app.analysis.providers.base import ExtractionProvider
from app.analysis.service import CvAnalysisService
from app.api.cv import router as cv_router
from app.api.health import internal_router, public_router
from app.config import Settings, get_settings
from app.middleware import RequestIdMiddleware


def create_app(
    settings: Settings | None = None, provider: ExtractionProvider | None = None
) -> FastAPI:
    settings = settings or get_settings()
    logging.basicConfig(
        level=settings.log_level,
        format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
    )
    docs = settings.docs_enabled
    app = FastAPI(
        title="AI Career Matcher - AI Service",
        version="0.1.0",
        docs_url="/docs" if docs else None,
        redoc_url=None,
        openapi_url="/openapi.json" if docs else None,
    )
    app.add_middleware(RequestIdMiddleware)
    app.include_router(public_router)
    app.include_router(internal_router)
    app.include_router(cv_router)
    app.state.settings = settings
    app.state.cv_service = CvAnalysisService(settings, provider or build_provider(settings))
    logging.getLogger(__name__).info("AI provider: %s", app.state.cv_service.provider.name)
    return app
