"""Deterministic explanation built directly from factor codes (used by the local provider)."""

from __future__ import annotations

from decimal import ROUND_HALF_UP, Decimal

from app.explain.schemas import ExplanationOut, FactorFacts, MatchFacts, Point


def percent(score: float) -> int:
    """Half-up rounding, matching the backend and the UI (Python's round() is banker's)."""
    return int(Decimal(str(score)).quantize(Decimal("1"), rounding=ROUND_HALF_UP))


def _join(items: list[str]) -> str:
    return ", ".join(items)


def build(facts: MatchFacts) -> ExplanationOut:
    factors: dict[str, FactorFacts] = {f.code: f for c in facts.components for f in c.factors}
    strengths: list[Point] = []
    gaps: list[Point] = []
    steps: list[Point] = []

    req_ok = factors.get("REQUIRED_SKILLS_MATCHED")
    req_missing = factors.get("REQUIRED_SKILLS_MISSING")
    pref_ok = factors.get("PREFERRED_SKILLS_MATCHED")
    pref_missing = factors.get("PREFERRED_SKILLS_MISSING")

    if req_ok:
        strengths.append(
            Point(
                text=f"You have required skills for this role: {_join(req_ok.skills)}.",
                skills=req_ok.skills,
            )
        )
    if pref_ok:
        strengths.append(
            Point(
                text=f"You also have preferred skills: {_join(pref_ok.skills)}.",
                skills=pref_ok.skills,
            )
        )
    if (f := factors.get("EXPERIENCE_MEETS_MINIMUM")) is not None:
        strengths.append(
            Point(
                text=f"Your {f.values['candidateMonths']} months of experience meet the "
                f"{f.values['requiredMonths']}-month minimum.",
                skills=[],
            )
        )
    if (f := factors.get("PROJECT_SKILLS_MATCHED")) is not None and len(strengths) < 4:
        strengths.append(
            Point(
                text=f"Your projects use skills this role needs: {_join(f.skills)}.",
                skills=f.skills,
            )
        )
    if "EDUCATION_MEETS_REQUIREMENT" in factors and len(strengths) < 4:
        strengths.append(Point(text="Your education meets the requirement.", skills=[]))

    if req_missing:
        gaps.append(
            Point(
                text=f"Required skills you don't have yet: {_join(req_missing.skills)}.",
                skills=req_missing.skills,
            )
        )
        for skill in req_missing.skills[:2]:
            steps.append(
                Point(text=f"Learn {skill} first: it is required for this role.", skills=[skill])
            )
    if pref_missing:
        gaps.append(
            Point(
                text=f"Preferred skills you could add: {_join(pref_missing.skills)}.",
                skills=pref_missing.skills,
            )
        )
        if len(steps) < 3:
            steps.append(
                Point(
                    text=f"Then consider {pref_missing.skills[0]} to strengthen your application.",
                    skills=[pref_missing.skills[0]],
                )
            )
    if (f := factors.get("EXPERIENCE_BELOW_MINIMUM")) is not None:
        gaps.append(
            Point(
                text=f"The role asks for {f.values['requiredMonths']} months of experience; "
                f"your CV shows {f.values['candidateMonths']}.",
                skills=[],
            )
        )
    for code in ("EDUCATION_ONE_LEVEL_BELOW", "EDUCATION_BELOW_REQUIREMENT"):
        if code in factors and len(gaps) < 4:
            gaps.append(
                Point(text="Your education is below the level this role asks for.", skills=[])
            )
    if facts.missing_data and len(gaps) < 4:
        gaps.append(
            Point(
                text=(
                    "Some of your information is missing, so this score may be lower "
                    "than it should be."
                ),
                skills=[],
            )
        )
        if len(steps) < 4:
            steps.append(
                Point(
                    text="Upload your CV and confirm your skills to get a more accurate score.",
                    skills=[],
                )
            )

    summary = f"Your compatibility with {facts.job.title} is {percent(facts.total_score)}%. " + (
        f"You have {len(req_ok.skills) if req_ok else 0} of the required skills"
        + (f" and are missing {len(req_missing.skills)}." if req_missing else ".")
    )
    return ExplanationOut(
        summary=summary, strengths=strengths[:4], gaps=gaps[:4], next_steps=steps[:4]
    )
