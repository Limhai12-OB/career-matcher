"""Orchestrates document reading, screening, extraction and grounding."""

from __future__ import annotations

import asyncio
import logging

from app.analysis.grounding import ground
from app.analysis.prompt import PROMPT_VERSION
from app.analysis.providers.base import ExtractionProvider, ProviderError
from app.analysis.schemas import AnalysisWarning, CvAnalysisResponse, DocumentInfo
from app.config import Settings
from app.documents.errors import DocumentError, DocumentErrorCode
from app.documents.extract import extract_text
from app.documents.sniff import detect_type
from app.documents.text import clean_text, strip_injection_lines, visible_char_count

logger = logging.getLogger(__name__)


class CvAnalysisService:
    def __init__(self, settings: Settings, provider: ExtractionProvider) -> None:
        self._settings = settings
        self._provider = provider
        self._semaphore = asyncio.Semaphore(settings.max_concurrent_analyses)

    @property
    def provider(self) -> ExtractionProvider:
        return self._provider

    async def analyze(self, data: bytes) -> CvAnalysisResponse:
        s = self._settings
        if len(data) > s.max_upload_bytes:
            raise DocumentError(DocumentErrorCode.DOCUMENT_TOO_LARGE)
        doc_type = detect_type(data)
        extracted = await asyncio.to_thread(
            extract_text,
            data,
            doc_type,
            max_pages=s.max_pdf_pages,
            timeout_seconds=s.extraction_timeout_seconds,
        )
        cleaned = clean_text(extracted.text)
        if visible_char_count(cleaned) < s.min_text_chars:
            raise DocumentError(DocumentErrorCode.NO_EXTRACTABLE_TEXT)
        if len(cleaned) > s.max_text_chars:
            # Explicit rejection rather than silent truncation of the candidate's data.
            raise DocumentError(DocumentErrorCode.DOCUMENT_TOO_LONG)

        screened = strip_injection_lines(cleaned)
        if visible_char_count(screened.text) < s.min_text_chars:
            raise DocumentError(DocumentErrorCode.NO_EXTRACTABLE_TEXT)

        async with self._semaphore:
            result = await self._extract_with_retry(screened.text)

        items, dropped = ground(result.extraction, screened.text)
        warnings: list[AnalysisWarning] = []
        if screened.removed_lines:
            warnings.append(AnalysisWarning.INSTRUCTION_LIKE_TEXT_REMOVED)
        if result.extraction.suspicious_content:
            warnings.append(AnalysisWarning.MODEL_FLAGGED_SUSPICIOUS_CONTENT)
        if dropped.total:
            warnings.append(AnalysisWarning.UNVERIFIED_ITEMS_REMOVED)
        if self._provider.limited:
            warnings.append(AnalysisWarning.LIMITED_EXTRACTION)

        logger.info(
            "CV analyzed type=%s chars=%d provider=%s skills=%d dropped=%d removed_lines=%d",
            doc_type.value,
            len(screened.text),
            self._provider.name,
            len(items["skills"]),
            dropped.total,
            screened.removed_lines,
        )
        return CvAnalysisResponse(
            provider=self._provider.name,
            model=result.model,
            prompt_version=PROMPT_VERSION,
            document=DocumentInfo(
                type=doc_type.value, pages=extracted.pages, characters=len(screened.text)
            ),
            dropped_items=dropped,
            warnings=warnings,
            **items,
        )

    async def _extract_with_retry(self, text: str):
        try:
            return await self._provider.extract(text)
        except ProviderError as exc:
            if not exc.retryable:
                raise
            logger.warning("Provider output invalid (%s); retrying once", exc)
            return await self._provider.extract(text)
