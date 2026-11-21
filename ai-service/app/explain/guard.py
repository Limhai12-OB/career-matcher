"""Grounding check: explanations may cite only skills and numbers present in the facts."""

from __future__ import annotations

import json
import math
import re

from app.explain.schemas import ExplanationOut, MatchFacts

_NUMBER = re.compile(r"\d+(?:[.,]\d+)*")
_FORBIDDEN = re.compile(r"https?://|www\.|<[a-z/!]", re.I)


def _numbers(text: str) -> set[str]:
    out: set[str] = set()
    for m in _NUMBER.findall(text):
        n = m.replace(",", "")
        out.add(n)
        if n.endswith(".0"):
            out.add(n[:-2])
    return out


def violations(facts: MatchFacts, e: ExplanationOut) -> list[str]:
    allowed_skills = {s for c in facts.components for f in c.factors for s in f.skills}
    serialized = json.dumps(facts.model_dump(by_alias=True), sort_keys=True)
    # Either integer rounding of the total is an honest way to state it.
    allowed_numbers = _numbers(serialized) | {
        str(math.floor(facts.total_score)),
        str(math.ceil(facts.total_score)),
    }
    allowed_numbers |= {str(len(f.skills)) for c in facts.components for f in c.factors}

    problems: list[str] = []
    points = [*e.strengths, *e.gaps, *e.next_steps]
    for text in [e.summary, *(p.text for p in points)]:
        if _FORBIDDEN.search(text):
            problems.append("links or markup")
        problems.extend(f"unsupported number {n}" for n in sorted(_numbers(text) - allowed_numbers))
    problems.extend(
        f"unknown skill {s}" for p in points for s in p.skills if s not in allowed_skills
    )
    return problems
