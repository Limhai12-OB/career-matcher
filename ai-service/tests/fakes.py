from __future__ import annotations

from app.analysis.providers.base import ProviderError, ProviderResult
from app.analysis.schemas import CvExtraction


class FakeProvider:
    """Scripted provider: returns queued extractions or raises queued errors; records inputs."""

    name = "fake"
    limited = False

    def __init__(self, *outcomes: CvExtraction | ProviderError) -> None:
        self.outcomes = list(outcomes)
        self.inputs: list[str] = []

    async def extract(self, cv_text: str) -> ProviderResult:
        self.inputs.append(cv_text)
        outcome = self.outcomes.pop(0) if len(self.outcomes) > 1 else self.outcomes[0]
        if isinstance(outcome, ProviderError):
            raise outcome
        return ProviderResult(extraction=outcome, model="fake-model")


def extraction(**overrides) -> CvExtraction:
    base = {
        "skills": [],
        "education": [],
        "experience": [],
        "projects": [],
        "certifications": [],
        "suspicious_content": False,
    }
    base.update(overrides)
    return CvExtraction.model_validate(base)
