"""Learning roadmap contract: skill gap facts in (from the backend), roadmap out."""

from __future__ import annotations

from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field, StringConstraints
from pydantic.alias_generators import to_camel

Name = Annotated[str, StringConstraints(max_length=80)]


class _Camel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, extra="forbid")


class GapFacts(_Camel):
    skill: Name
    category: str = Field(max_length=40)
    priority: Literal["CRITICAL", "HIGH", "MEDIUM", "LOW"]
    required_in: int = Field(ge=0, le=100)
    preferred_in: int = Field(ge=0, le=100)
    average_gain: float = Field(ge=-100, le=100)
    example_jobs: list[Annotated[str, StringConstraints(max_length=150)]] = Field(max_length=3)


class ResourceFacts(_Camel):
    id: str = Field(max_length=40)
    skill: Name
    title: str = Field(max_length=200)
    provider: str = Field(max_length=100)
    type: str = Field(max_length=20)


class RoadmapFacts(_Camel):
    target_job_count: int = Field(ge=0, le=100)
    average_score: float = Field(ge=0, le=100)
    required_coverage: float = Field(ge=0, le=100)
    gaps: list[GapFacts] = Field(min_length=1, max_length=8)
    existing_skills: list[Name] = Field(max_length=40)
    resources: list[ResourceFacts] = Field(max_length=30)


Line = Annotated[str, StringConstraints(strip_whitespace=True, min_length=5, max_length=160)]


class StepOut(BaseModel):
    model_config = ConfigDict(extra="forbid")
    skill: Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=80)] = (
        Field(description="Exact skill name of one gap from the facts")
    )
    title: Annotated[str, StringConstraints(strip_whitespace=True, min_length=5, max_length=120)]
    why: Annotated[str, StringConstraints(strip_whitespace=True, min_length=10, max_length=300)]
    activities: list[Line] = Field(min_length=1, max_length=4)
    estimated_weeks: int = Field(ge=1, le=12)
    resource_ids: list[str] = Field(
        max_length=3, description="Ids of resources listed for this skill only"
    )


class RoadmapOut(BaseModel):
    """What the LLM must return."""

    model_config = ConfigDict(extra="forbid")
    summary: Annotated[str, StringConstraints(strip_whitespace=True, min_length=20, max_length=600)]
    steps: list[StepOut] = Field(min_length=1, max_length=6)


class StepResponse(_Camel):
    skill: str
    title: str
    why: str
    activities: list[str]
    estimated_weeks: int
    resource_ids: list[str]


class RoadmapResponse(_Camel):
    provider: str
    model: str
    prompt_version: str
    summary: str
    steps: list[StepResponse]
