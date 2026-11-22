"""Local-model provider: an Ollama server (e.g. on a developer Mac) with schema-constrained JSON.

Ollama turns the JSON schema into a sampling grammar, so the model can only emit JSON of the right
shape; Pydantic then enforces the remaining constraints (lengths, patterns). Small local models are
less accurate than Claude, but every result still passes the same grounding and claim checks.
Intended for local development and offline testing, not production.
"""

from __future__ import annotations

import logging

import httpx2
import pydantic

from app.analysis.prompt import SYSTEM_PROMPT, build_user_message
from app.analysis.providers.base import ProviderError, ProviderErrorCode, ProviderResult
from app.analysis.schemas import CvExtraction
from app.config import Settings
from app.explain import prompt as explain_prompt
from app.explain.schemas import ExplanationOut, MatchFacts
from app.roadmap import prompt as roadmap_prompt
from app.roadmap.schemas import RoadmapFacts, RoadmapOut

logger = logging.getLogger(__name__)


class OllamaProvider:
    name = "ollama"
    limited = False

    def __init__(self, settings: Settings, client: httpx2.AsyncClient | None = None) -> None:
        self._settings = settings
        self._model = settings.ollama_model
        self._client = client or httpx2.AsyncClient(
            base_url=str(settings.ollama_base_url).rstrip("/"),
            timeout=httpx2.Timeout(settings.request_timeout_seconds, connect=5.0),
        )

    async def extract(self, cv_text: str) -> ProviderResult:
        parsed = await self._chat(SYSTEM_PROMPT, build_user_message(cv_text), CvExtraction)
        return ProviderResult(extraction=parsed, model=self._model)

    async def explain(
        self, facts: MatchFacts, feedback: list[str] | None = None
    ) -> tuple[ExplanationOut, str]:
        from app.explain.service import facts_json

        user = explain_prompt.build_user_message(facts_json(facts), feedback)
        return await self._chat(explain_prompt.SYSTEM_PROMPT, user, ExplanationOut), self._model

    async def roadmap(
        self, facts: RoadmapFacts, feedback: list[str] | None = None
    ) -> tuple[RoadmapOut, str]:
        from app.roadmap.service import facts_json

        user = roadmap_prompt.build_user_message(facts_json(facts), feedback)
        return await self._chat(roadmap_prompt.SYSTEM_PROMPT, user, RoadmapOut), self._model

    async def _chat[T: pydantic.BaseModel](self, system: str, user: str, schema: type[T]) -> T:
        s = self._settings
        body = {
            "model": self._model,
            "messages": [
                {"role": "system", "content": system},
                {"role": "user", "content": user},
            ],
            "format": schema.model_json_schema(),
            "stream": False,
            "options": {
                "temperature": 0,
                "num_ctx": s.ollama_num_ctx,
                "num_predict": s.ollama_num_predict,
            },
        }
        try:
            response = await self._client.post("/api/chat", json=body)
        except httpx2.TimeoutException as exc:
            raise ProviderError(ProviderErrorCode.AI_TIMEOUT) from exc
        except httpx2.HTTPError as exc:
            raise ProviderError(ProviderErrorCode.AI_UNAVAILABLE, "connection") from exc

        if response.status_code == 404:
            # Ollama answers 404 when the model hasn't been pulled.
            raise ProviderError(ProviderErrorCode.AI_NOT_CONFIGURED, "model not found")
        if response.status_code != 200:
            raise ProviderError(ProviderErrorCode.AI_UNAVAILABLE, f"status {response.status_code}")

        try:
            data = response.json()
            content = data["message"]["content"]
        except (ValueError, KeyError, TypeError) as exc:
            raise ProviderError(ProviderErrorCode.AI_UNAVAILABLE, "malformed response") from exc

        logger.info(
            "Ollama call done model=%s done_reason=%s in=%s out=%s seconds=%.1f",
            self._model,
            data.get("done_reason"),
            data.get("prompt_eval_count"),
            data.get("eval_count"),
            (data.get("total_duration") or 0) / 1e9,
        )
        if data.get("done_reason") == "length":
            raise ProviderError(ProviderErrorCode.AI_INVALID_OUTPUT, "truncated")
        try:
            return schema.model_validate_json(content)
        except pydantic.ValidationError as exc:
            # Never log the content itself: it can contain CV text.
            raise ProviderError(ProviderErrorCode.AI_INVALID_OUTPUT, "schema") from exc
