"""Orchestrates explanation generation with grounding checks."""

from __future__ import annotations

import json
import logging
from typing import Protocol

from app.analysis.providers.base import ProviderError, ProviderErrorCode
from app.explain import template
from app.explain.guard import violations
from app.explain.prompt import PROMPT_VERSION
from app.explain.schemas import ExplanationOut, ExplanationResponse, MatchFacts

logger = logging.getLogger(__name__)


class ExplanationProvider(Protocol):
    name: str

    async def explain(
        self, facts: MatchFacts, feedback: list[str] | None = None
    ) -> tuple[ExplanationOut, str]: ...


class TemplateExplainer:
    """Offline provider: deterministic sentences built from factor codes."""

    name = "local"
    model = "template-v1"

    async def explain(
        self, facts: MatchFacts, feedback: list[str] | None = None
    ) -> tuple[ExplanationOut, str]:
        return template.build(facts), self.model


class ExplanationService:
    def __init__(self, provider: ExplanationProvider) -> None:
        self.provider = provider

    async def explain(self, facts: MatchFacts) -> ExplanationResponse:
        feedback: list[str] | None = None
        for attempt in (1, 2):
            out, model = await self.provider.explain(facts, feedback=feedback)
            problems = violations(facts, out)
            if not problems:
                return ExplanationResponse(
                    provider=self.provider.name,
                    model=model,
                    prompt_version=PROMPT_VERSION,
                    summary=out.summary,
                    strengths=out.strengths,
                    gaps=out.gaps,
                    next_steps=out.next_steps,
                )
            logger.warning("Ungrounded explanation (attempt %d): %s", attempt, problems)
            feedback = problems
        raise ProviderError(ProviderErrorCode.AI_INVALID_OUTPUT, "ungrounded explanation")


def facts_json(facts: MatchFacts) -> str:
    return json.dumps(facts.model_dump(by_alias=True), sort_keys=True)
