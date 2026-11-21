"""Application settings loaded from environment variables."""

from functools import lru_cache
from typing import Literal

from pydantic import Field, SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    environment: Literal["dev", "test", "prod"] = Field(default="dev", alias="APP_ENV")
    log_level: Literal["DEBUG", "INFO", "WARNING", "ERROR"] = Field(
        default="INFO", alias="AI_LOG_LEVEL"
    )
    # Shared secret the backend must present on every /v1 request.
    internal_token: SecretStr = Field(alias="AI_INTERNAL_TOKEN", min_length=32)
    docs_enabled: bool = Field(default=False, alias="AI_DOCS_ENABLED")

    # ---- LLM provider ----
    # "anthropic": Claude via the Anthropic API (production).
    # "local": deterministic rule-based extractor (skills + education only; no network, no key).
    provider: Literal["anthropic", "local"] = Field(default="local", alias="AI_PROVIDER")
    anthropic_api_key: SecretStr | None = Field(default=None, alias="ANTHROPIC_API_KEY")
    model: str = Field(default="claude-opus-5-5", alias="AI_MODEL")
    effort: Literal["low", "medium", "high"] = Field(default="medium", alias="AI_EFFORT")
    request_timeout_seconds: float = Field(default=120.0, alias="AI_TIMEOUT_SECONDS", gt=0)
    max_retries: int = Field(default=2, alias="AI_MAX_RETRIES", ge=0, le=5)
    # Server-side refusal fallbacks (re-run on a fallback model if the primary declines).
    fallbacks_enabled: bool = Field(default=True, alias="AI_FALLBACKS_ENABLED")
    max_concurrent_analyses: int = Field(default=4, alias="AI_MAX_CONCURRENT", ge=1, le=64)

    # ---- Document limits ----
    max_upload_bytes: int = Field(default=5 * 1024 * 1024, alias="MAX_UPLOAD_BYTES")
    max_pdf_pages: int = Field(default=20, alias="MAX_PDF_PAGES")
    max_text_chars: int = Field(default=40_000, alias="MAX_TEXT_CHARS")
    min_text_chars: int = Field(default=80, alias="MIN_TEXT_CHARS")
    extraction_timeout_seconds: float = Field(default=20.0, alias="EXTRACTION_TIMEOUT_SECONDS")


@lru_cache
def get_settings() -> Settings:
    return Settings()  # type: ignore[call-arg]
