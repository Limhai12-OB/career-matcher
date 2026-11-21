"""FastAPI application factory for the internal AI service."""

import logging

from fastapi import FastAPI

from app.analysis.factory import build_provider
from app.analysis.providers.base import ExtractionProvider
from app.analysis.service import CvAnalysisService
from app.api.cv import router as cv_router
from app.api.explain import router as explain_router
from app.api.health import internal_router, public_router
from app.api.roadmap import router as roadmap_router
from app.config import Settings, get_settings
from app.explain.service import ExplanationProvider, ExplanationService, TemplateExplainer
from app.middleware import RequestIdMiddleware
from app.roadmap.service import RoadmapProvider, RoadmapService, TemplateRoadmapper
from app.security import InternalTokenMiddleware


def create_app(
    settings: Settings | None = None,
    provider: ExtractionProvider | None = None,
    explainer: ExplanationProvider | None = None,
    roadmapper: RoadmapProvider | None = None,
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
    app.add_middleware(InternalTokenMiddleware, token=settings.internal_token.get_secret_value())
    app.add_middleware(RequestIdMiddleware)
    app.include_router(public_router)
    app.include_router(internal_router)
    app.include_router(cv_router)
    app.state.settings = settings
    extraction_provider = provider or build_provider(settings)
    app.state.cv_service = CvAnalysisService(settings, extraction_provider)
    # The Anthropic provider also explains matches; the local one uses deterministic templates.
    default_explainer = (
        extraction_provider if hasattr(extraction_provider, "explain") else TemplateExplainer()
    )
    app.state.explanation_service = ExplanationService(explainer or default_explainer)
    app.include_router(explain_router)
    default_roadmapper = (
        extraction_provider if hasattr(extraction_provider, "roadmap") else TemplateRoadmapper()
    )
    app.state.roadmap_service = RoadmapService(roadmapper or default_roadmapper)
    app.include_router(roadmap_router)
    log = logging.getLogger(__name__)
    log.info("AI provider: %s", app.state.cv_service.provider.name)
    if settings.environment == "production" and settings.provider == "local":
        log.warning(
            "AI_PROVIDER=local in production: CV analysis runs in basic mode (skills and "
            "education only) and explanations/roadmaps use templates"
        )
    return app
