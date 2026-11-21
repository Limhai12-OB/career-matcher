"""Deterministic roadmap used in local mode (no AI): built only from the gap facts."""

from __future__ import annotations

from app.roadmap.schemas import RoadmapFacts, RoadmapOut, StepOut

_WEEKS = {"CRITICAL": 4, "HIGH": 3, "MEDIUM": 2, "LOW": 2}
_PRACTICE = {
    "PROGRAMMING_LANGUAGE": "Solve small exercises in {s} until the syntax feels familiar",
    "FRAMEWORK": "Follow the official getting-started guide and build a small {s} app",
    "DATABASE": "Design a small schema in {s} and practise queries against it",
    "CLOUD": "Deploy a small app using the free tier of {s}",
    "DEVOPS": "Set up {s} for one of your existing projects",
    "TESTING": "Add {s} tests to one of your existing projects",
}
_DEFAULT_PRACTICE = "Work through the official introduction to {s}"


def _why(g, total: int) -> str:
    parts = []
    if g.required_in:
        parts.append(f"required by {g.required_in} of your {total} best-matching jobs")
    if g.preferred_in:
        parts.append(f"preferred by {g.preferred_in}")
    text = f"{g.skill} is " + " and ".join(parts) + "."
    if g.average_gain > 0:
        text += (
            " Confirming it would raise your average compatibility estimate"
            f" by about {g.average_gain:g} points."
        )
    return text


def build(facts: RoadmapFacts) -> RoadmapOut:
    gaps = facts.gaps[:6]
    steps = []
    for g in gaps:
        practice = _PRACTICE.get(g.category, _DEFAULT_PRACTICE).format(s=g.skill)
        steps.append(
            StepOut(
                skill=g.skill,
                title=f"Learn {g.skill}",
                why=_why(g, facts.target_job_count),
                activities=[
                    practice,
                    f"Build a small project that uses {g.skill} and describe it on your CV",
                    f"Confirm {g.skill} in your profile once you can use it on your own",
                ],
                estimated_weeks=_WEEKS[g.priority],
                resource_ids=[r.id for r in facts.resources if r.skill == g.skill][:3],
            )
        )
    summary = (
        f"This plan covers the {len(steps)} skills that appear most in your best-matching jobs, "
        "most important first. Your compatibility scores are estimates and will update as you "
        "confirm new skills."
    )
    return RoadmapOut(summary=summary, steps=steps)
