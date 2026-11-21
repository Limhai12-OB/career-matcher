"""Roadmap validation: grounded in the gaps, curated resources only, no outcome promises.

Mirrored by the backend's RoadmapGuard, which re-checks every roadmap before storing it.
"""

from __future__ import annotations

import json
import math
import re

from app.roadmap.schemas import RoadmapFacts, RoadmapOut

_NUMBER = re.compile(r"\d+(?:[.,]\d+)*")
_MARKUP = re.compile(r"https?://|www\.|<[a-z/!]", re.I)

# Claims the platform must never make: employment outcomes, qualifications, certainty.
UNSUPPORTED_CLAIMS = [
    re.compile(r"\bguarantee(s|d)?\b", re.I),
    re.compile(r"\bpromise(s|d)?\b", re.I),
    re.compile(
        r"\b(will|going to)\s+(definitely\s+|surely\s+)?(get|land|secure|win|receive)\s+(you\s+)?"
        r"(a|an|the|your)?\s*(job|offer|position|role|interview|internship)s?\b",
        re.I,
    ),
    re.compile(r"\b(get|be|being)\s+hired\b", re.I),
    re.compile(r"\bjob\s+offers?\b", re.I),
    re.compile(r"\b(makes?|making)\s+you\s+(fully\s+)?(qualified|eligible|job[- ]ready)\b", re.I),
    re.compile(r"\byou\s+(are|'re|will\s+be)\s+(now\s+)?(fully\s+)?(qualified|certified)\b", re.I),
    re.compile(
        r"\b(certain\s+(to|that\s+you)|assured\s+(of|to)|100\s*%\s*(chance|sure|certain|guaranteed))",
        re.I,
    ),
]


def _numbers(text: str) -> set[str]:
    out: set[str] = set()
    for m in _NUMBER.findall(text):
        n = m.replace(",", "")
        out.add(n)
        if n.endswith(".0"):
            out.add(n[:-2])
    return out


def violations(facts: RoadmapFacts, r: RoadmapOut) -> list[str]:
    gap_skills = {g.skill for g in facts.gaps}
    resources = {res.id: res for res in facts.resources}
    # Resource ids are opaque: their digit runs ("…a95c…") must not become citable numbers.
    data = facts.model_dump(by_alias=True)
    for res in data["resources"]:
        res["id"] = ""
    allowed = _numbers(json.dumps(data, sort_keys=True))
    for d in (facts.average_score, facts.required_coverage):
        allowed |= {str(math.floor(d)), str(math.ceil(d))}
    allowed |= {str(s.estimated_weeks) for s in r.steps} | {str(len(r.steps))}

    problems: list[str] = []
    seen: set[str] = set()
    for s in r.steps:
        if s.skill not in gap_skills:
            problems.append(f"step for a skill that is not a gap: {s.skill}")
        if s.skill in seen:
            problems.append(f"duplicate step for {s.skill}")
        seen.add(s.skill)
        for rid in s.resource_ids:
            res = resources.get(rid)
            if res is None:
                problems.append(f"unknown resource {rid}")
            elif res.skill != s.skill:
                problems.append(f"resource {rid} is not for {s.skill}")

    texts = [r.summary, *(t for s in r.steps for t in (s.title, s.why, *s.activities))]
    for t in texts:
        if _MARKUP.search(t):
            problems.append("links or markup")
        for p in UNSUPPORTED_CLAIMS:
            if m := p.search(t):
                problems.append(f"unsupported claim: {m.group(0)}")
        problems.extend(f"unsupported number {n}" for n in sorted(_numbers(t) - allowed))
    return problems
