"""Application settings loaded from environment variables."""

from functools import lru_cache
from typing import Literal

from pydantic import Field, SecretStr, field_validator, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    # hide_input_in_errors: validation errors must never echo secrets (tokens, API keys) into logs.
    model_config = SettingsConfigDict(
        env_file=".env", env_file_encoding="utf-8", extra="ignore", hide_input_in_errors=True
    )

    environment: Literal["dev", "test", "production"] = Field(default="dev", alias="APP_ENV")
    log_level: Literal["DEBUG", "INFO", "WARNING", "ERROR"] = Field(
        default="INFO", alias="AI_LOG_LEVEL"
    )
    # Shared secret the backend must present on every /v1 request.
    internal_token: SecretStr = Field(alias="AI_INTERNAL_TOKEN", min_length=32)
    docs_enabled: bool = Field(default=False, alias="AI_DOCS_ENABLED")

    # ---- LLM provider ----
    # "anthropic": Claude via the Anthropic API (production).
    # "ollama": a local model served by Ollama (development / offline testing; no key).
    # "local": deterministic rule-based extractor (skills + education only; no network, no key).
    provider: Literal["anthropic", "ollama", "local"] = Field(default="local", alias="AI_PROVIDER")
    anthropic_api_key: SecretStr | None = Field(default=None, alias="ANTHROPIC_API_KEY")
    model: str = Field(default="claude-opus-5-5", alias="AI_MODEL")
    effort: Literal["low", "medium", "high"] = Field(default="medium", alias="AI_EFFORT")
    request_timeout_seconds: float = Field(default=120.0, alias="AI_TIMEOUT_SECONDS", gt=0)
    max_retries: int = Field(default=2, alias="AI_MAX_RETRIES", ge=0, le=5)
    # Server-side refusal fallbacks (re-run on a fallback model if the primary declines).
    fallbacks_enabled: bool = Field(default=True, alias="AI_FALLBACKS_ENABLED")
    # ---- Ollama (AI_PROVIDER=ollama) ----
    ollama_base_url: str = Field(default="http://localhost:11434", alias="OLLAMA_BASE_URL")
    ollama_model: str = Field(default="qwen2.5:7b", alias="OLLAMA_MODEL")
    ollama_num_ctx: int = Field(default=16384, alias="OLLAMA_NUM_CTX", ge=2048)
    ollama_num_predict: int = Field(default=8192, alias="OLLAMA_NUM_PREDICT", ge=256)
    max_concurrent_analyses: int = Field(default=4, alias="AI_MAX_CONCURRENT", ge=1, le=64)

    # ---- Document limits ----
    max_upload_bytes: int = Field(default=5 * 1024 * 1024, alias="MAX_UPLOAD_BYTES")
    max_pdf_pages: int = Field(default=20, alias="MAX_PDF_PAGES")
    max_text_chars: int = Field(default=40_000, alias="MAX_TEXT_CHARS")
    min_text_chars: int = Field(default=80, alias="MIN_TEXT_CHARS")
    extraction_timeout_seconds: float = Field(default=20.0, alias="EXTRACTION_TIMEOUT_SECONDS")

    @field_validator("environment", mode="before")
    @classmethod
    def _normalize_environment(cls, v: object) -> object:
        return "production" if isinstance(v, str) and v.lower() in ("prod", "production") else v

    @model_validator(mode="after")
    def _production_safety(self) -> "Settings":
        """Refuse unsafe settings in production (the service must not start)."""
        if self.environment != "production":
            return self
        problems = []
        if self.docs_enabled:
            problems.append("AI_DOCS_ENABLED must be false")
        if self.log_level == "DEBUG":
            problems.append("AI_LOG_LEVEL must not be DEBUG")
        if self.provider == "anthropic" and not self.anthropic_api_key:
            problems.append("ANTHROPIC_API_KEY is required when AI_PROVIDER=anthropic")
        if self.provider == "ollama":
            problems.append("AI_PROVIDER=ollama is for local development only")
        if problems:
            raise ValueError("Unsafe production configuration: " + "; ".join(problems))
        return self


@lru_cache
def get_settings() -> Settings:
    return Settings()  # type: ignore[call-arg]
