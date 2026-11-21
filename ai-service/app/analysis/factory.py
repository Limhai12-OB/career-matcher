from app.analysis.providers.base import ExtractionProvider
from app.analysis.providers.local_provider import LocalProvider
from app.config import Settings


def build_provider(settings: Settings) -> ExtractionProvider:
    if settings.provider == "anthropic":
        from app.analysis.providers.anthropic_provider import AnthropicProvider

        return AnthropicProvider(settings)
    return LocalProvider()
