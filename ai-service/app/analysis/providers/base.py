"""Provider abstraction and typed provider failures."""

from __future__ import annotations

from dataclasses import dataclass
from enum import StrEnum
from typing import Protocol

from app.analysis.schemas import CvExtraction


class ProviderErrorCode(StrEnum):
    AI_NOT_CONFIGURED = "AI_NOT_CONFIGURED"
    AI_UNAVAILABLE = "AI_UNAVAILABLE"
    AI_RATE_LIMITED = "AI_RATE_LIMITED"
    AI_TIMEOUT = "AI_TIMEOUT"
    AI_REFUSED = "AI_REFUSED"
    AI_INVALID_OUTPUT = "AI_INVALID_OUTPUT"


class ProviderError(Exception):
    def __init__(self, code: ProviderErrorCode, detail: str = "") -> None:
        super().__init__(f"{code.value}: {detail}" if detail else code.value)
        self.code = code
        # Retrying makes sense only for output that failed validation (the model may succeed on
        # a second attempt); everything else is either retried by the SDK or not retryable.
        self.retryable = code == ProviderErrorCode.AI_INVALID_OUTPUT


@dataclass(frozen=True)
class ProviderResult:
    extraction: CvExtraction
    model: str


class ExtractionProvider(Protocol):
    name: str
    #: True if the provider extracts only part of the schema (surfaced as a warning).
    limited: bool

    async def extract(self, cv_text: str) -> ProviderResult: ...
