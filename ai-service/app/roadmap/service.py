"""Orchestrates roadmap generation with grounding and claim checks."""

from __future__ import annotations

import json
import logging
from typing import Protocol

from app.analysis.providers.base import ProviderError, ProviderErrorCode
from app.roadmap import template
from app.roadmap.guard import violations
from app.roadmap.prompt import PROMPT_VERSION
from app.roadmap.schemas import RoadmapFacts, RoadmapOut, RoadmapResponse, StepResponse

logger = logging.getLogger(__name__)


class RoadmapProvider(Protocol):
    name: str

    async def roadmap(self, facts: RoadmapFacts) -> tuple[RoadmapOut, str]: ...


class TemplateRoadmapper:
    """Offline provider: deterministic roadmap from the gap facts."""

    name = "local"
    model = "template-v1"

    async def roadmap(self, facts: RoadmapFacts) -> tuple[RoadmapOut, str]:
        return template.build(facts), self.model


class RoadmapService:
    def __init__(self, provider: RoadmapProvider) -> None:
        self.provider = provider

    async def roadmap(self, facts: RoadmapFacts) -> RoadmapResponse:
        for attempt in (1, 2):
            out, model = await self.provider.roadmap(facts)
            problems = violations(facts, out)
            if not problems:
                return RoadmapResponse(
                    provider=self.provider.name,
                    model=model,
                    prompt_version=PROMPT_VERSION,
                    summary=out.summary,
                    steps=[StepResponse(**s.model_dump()) for s in out.steps],
                )
            logger.warning("Rejected roadmap (attempt %d): %s", attempt, problems)
        raise ProviderError(ProviderErrorCode.AI_INVALID_OUTPUT, "unsupported roadmap")


def facts_json(facts: RoadmapFacts) -> str:
    return json.dumps(facts.model_dump(by_alias=True), sort_keys=True)
