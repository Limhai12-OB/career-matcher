"""Claude-backed extraction using structured outputs (schema-constrained JSON)."""

from __future__ import annotations

import logging

import anthropic
import pydantic

from app.analysis.prompt import SYSTEM_PROMPT, build_user_message
from app.analysis.providers.base import ProviderError, ProviderErrorCode, ProviderResult
from app.analysis.schemas import CvExtraction
from app.config import Settings

logger = logging.getLogger(__name__)

FALLBACK_BETA = "server-side-fallback-2026-07-01"


class AnthropicProvider:
    name = "anthropic"
    limited = False

    def __init__(self, settings: Settings, client: anthropic.AsyncAnthropic | None = None) -> None:
        self._settings = settings
        if client is not None:
            self._client = client
        else:
            key = (
                settings.anthropic_api_key.get_secret_value()
                if settings.anthropic_api_key
                else None
            )
            # With no explicit key the SDK resolves credentials from the environment.
            self._client = anthropic.AsyncAnthropic(
                api_key=key,
                timeout=settings.request_timeout_seconds,
                max_retries=settings.max_retries,
            )

    async def extract(self, cv_text: str) -> ProviderResult:
        s = self._settings
        kwargs: dict = {
            "model": s.model,
            "max_tokens": 16000,
            "system": SYSTEM_PROMPT,
            "messages": [{"role": "user", "content": build_user_message(cv_text)}],
            "output_format": CvExtraction,
            "output_config": {"effort": s.effort},
        }
        if s.fallbacks_enabled:
            # On a safety decline the API re-runs the request on a fallback model server-side.
            kwargs["betas"] = [FALLBACK_BETA]
            kwargs["fallbacks"] = "default"

        try:
            response = await self._client.beta.messages.parse(**kwargs)
        except (anthropic.AuthenticationError, anthropic.PermissionDeniedError) as exc:
            raise ProviderError(ProviderErrorCode.AI_NOT_CONFIGURED, type(exc).__name__) from exc
        except anthropic.RateLimitError as exc:
            raise ProviderError(ProviderErrorCode.AI_RATE_LIMITED) from exc
        except anthropic.APITimeoutError as exc:
            raise ProviderError(ProviderErrorCode.AI_TIMEOUT) from exc
        except anthropic.BadRequestError as exc:
            logger.error("Anthropic rejected the request: %s", exc.message)
            raise ProviderError(ProviderErrorCode.AI_UNAVAILABLE, "bad request") from exc
        except anthropic.APIStatusError as exc:
            raise ProviderError(
                ProviderErrorCode.AI_UNAVAILABLE, f"status {exc.status_code}"
            ) from exc
        except anthropic.APIConnectionError as exc:
            raise ProviderError(ProviderErrorCode.AI_UNAVAILABLE, "connection") from exc
        except pydantic.ValidationError as exc:
            # Output did not satisfy constraints the SDK validates client-side (lengths, patterns).
            raise ProviderError(ProviderErrorCode.AI_INVALID_OUTPUT, "schema") from exc

        logger.info(
            "Anthropic extraction done request_id=%s model=%s stop=%s in=%s out=%s",
            getattr(response, "_request_id", None),
            response.model,
            response.stop_reason,
            response.usage.input_tokens,
            response.usage.output_tokens,
        )
        if response.stop_reason == "refusal":
            raise ProviderError(ProviderErrorCode.AI_REFUSED)
        if response.stop_reason == "max_tokens":
            raise ProviderError(ProviderErrorCode.AI_INVALID_OUTPUT, "truncated")
        parsed = response.parsed_output
        if not isinstance(parsed, CvExtraction):
            raise ProviderError(ProviderErrorCode.AI_INVALID_OUTPUT, "no parsed output")
        return ProviderResult(extraction=parsed, model=response.model)
