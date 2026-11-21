"""Match explanation contract: facts in (from the backend), explanation out."""

from __future__ import annotations

from typing import Annotated, Any

from pydantic import BaseModel, ConfigDict, Field, StringConstraints
from pydantic.alias_generators import to_camel


class _Camel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, extra="forbid")


class FactorFacts(_Camel):
    code: str = Field(max_length=60)
    skills: list[Annotated[str, StringConstraints(max_length=80)]] = Field(max_length=40)
    values: dict[str, Any]


class ComponentFacts(_Camel):
    component: str = Field(max_length=20)
    weight_percent: int = Field(ge=0, le=100)
    score_percent: int = Field(ge=0, le=100)
    status: str = Field(max_length=30)
    factors: list[FactorFacts] = Field(max_length=20)


class JobFacts(_Camel):
    title: str = Field(max_length=150)
    company: str = Field(max_length=120)
    employment_type: str
    remote_type: str
    seniority: str
    data_origin: str


class MatchFacts(_Camel):
    algorithm_version: str = Field(max_length=20)
    job: JobFacts
    total_score: float = Field(ge=0, le=100)
    components: list[ComponentFacts] = Field(min_length=1, max_length=10)
    missing_data: list[str] = Field(max_length=10)


Text = Annotated[str, StringConstraints(strip_whitespace=True, min_length=5, max_length=240)]
SkillRef = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=80)]


class Point(BaseModel):
    model_config = ConfigDict(extra="forbid")
    text: Text
    skills: list[SkillRef] = Field(
        max_length=10, description="Skills mentioned in text; only names from the facts"
    )


class ExplanationOut(BaseModel):
    """What the LLM must return."""

    model_config = ConfigDict(extra="forbid")
    summary: Annotated[str, StringConstraints(strip_whitespace=True, min_length=20, max_length=600)]
    strengths: list[Point] = Field(max_length=4)
    gaps: list[Point] = Field(max_length=4)
    next_steps: list[Point] = Field(max_length=4)


class ExplanationResponse(_Camel):
    provider: str
    model: str
    prompt_version: str
    summary: str
    strengths: list[Point]
    gaps: list[Point]
    next_steps: list[Point]
