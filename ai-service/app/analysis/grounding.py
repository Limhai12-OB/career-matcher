"""
Deterministic grounding checks: every item the model returns must be supported by the actual CV
text. Items that cannot be verified are dropped (never "repaired" or kept), so model
hallucinations cannot reach the candidate's profile.
"""

from __future__ import annotations

import re
import unicodedata

from app.analysis.schemas import (
    CertificationOut,
    CvExtraction,
    DroppedCounts,
    EducationOut,
    ExperienceOut,
    ProjectOut,
    SkillOut,
)

_QUOTES = str.maketrans({"‘": "'", "’": "'", "“": '"', "”": '"', "–": "-", "—": "-", "•": " "})
_NON_ALNUM = re.compile(r"[^0-9a-z]+")
_NON_TERM = re.compile(r"[^0-9a-z+#.]+")


def _base(s: str) -> str:
    return unicodedata.normalize("NFKC", s).translate(_QUOTES).casefold()


def alnum(s: str) -> str:
    """Letters and digits only: tolerant to whitespace/punctuation differences in quotes."""
    return _NON_ALNUM.sub("", _base(s))


def term(s: str) -> str:
    """Technology-term form: keeps + # . so that C++, C#, Node.js stay distinct."""
    # Only trailing dots are sentence punctuation; a leading dot belongs to names like ".NET".
    return " ".join(w.rstrip(".") for w in _NON_TERM.sub(" ", _base(s)).split()).strip()


class Corpus:
    def __init__(self, text: str) -> None:
        self.alnum = alnum(text)
        self.term = f" {term(text)} "

    def contains_quote(self, quote: str) -> bool:
        q = alnum(quote)
        return len(q) >= 2 and q in self.alnum

    def contains_term(self, value: str, within: str | None = None) -> bool:
        """Whole-term match (C does not match inside C++; Java does not match JavaScript)."""
        t = term(value)
        if not t:
            return False
        haystack = f" {term(within)} " if within is not None else self.term
        pattern = r"(?<![0-9a-z+#.])" + re.escape(t) + r"(?![0-9a-z+#]|\.[0-9a-z])"
        return re.search(pattern, haystack) is not None

    def contains_text(self, value: str) -> bool:
        v = alnum(value)
        return bool(v) and v in self.alnum


def _date_ok(corpus: Corpus, value: str | None) -> str | None:
    """Keep a date only if its year appears in the document."""
    if value and value[:4] in corpus.alnum:
        return value
    return None


def ground(extraction: CvExtraction, text: str) -> tuple[dict[str, list], DroppedCounts]:
    corpus = Corpus(text)
    dropped = DroppedCounts()

    skills: list[SkillOut] = []
    seen: set[str] = set()
    for s in extraction.skills:
        key = term(s.name)
        if key in seen:
            continue
        if corpus.contains_quote(s.evidence) and corpus.contains_term(s.name, within=s.evidence):
            seen.add(key)
            skills.append(SkillOut(name=s.name, category=s.category, evidence=s.evidence))
        else:
            dropped.skills += 1

    def named_skills(names: list[str], evidence: str) -> list[str]:
        return [n for n in dict.fromkeys(names) if corpus.contains_term(n)]

    education: list[EducationOut] = []
    for e in extraction.education:
        if corpus.contains_quote(e.evidence) and corpus.contains_text(e.institution):
            education.append(
                EducationOut(
                    institution=e.institution,
                    degree=e.degree if e.degree and corpus.contains_text(e.degree) else None,
                    field_of_study=e.field_of_study
                    if e.field_of_study and corpus.contains_text(e.field_of_study)
                    else None,
                    level=e.level,
                    start_date=_date_ok(corpus, e.start_date),
                    end_date=_date_ok(corpus, e.end_date),
                    ongoing=e.ongoing,
                    evidence=e.evidence,
                )
            )
        else:
            dropped.education += 1

    experience: list[ExperienceOut] = []
    for x in extraction.experience:
        if (
            corpus.contains_quote(x.evidence)
            and corpus.contains_text(x.title)
            and corpus.contains_text(x.organization)
        ):
            experience.append(
                ExperienceOut(
                    title=x.title,
                    organization=x.organization,
                    employment_type=x.employment_type,
                    start_date=_date_ok(corpus, x.start_date),
                    end_date=_date_ok(corpus, x.end_date),
                    is_current=x.is_current,
                    skills=named_skills(x.skills, x.evidence),
                    evidence=x.evidence,
                )
            )
        else:
            dropped.experience += 1

    projects: list[ProjectOut] = []
    for p in extraction.projects:
        if corpus.contains_quote(p.evidence) and corpus.contains_text(p.name):
            projects.append(
                ProjectOut(
                    name=p.name, skills=named_skills(p.skills, p.evidence), evidence=p.evidence
                )
            )
        else:
            dropped.projects += 1

    certifications: list[CertificationOut] = []
    for c in extraction.certifications:
        if corpus.contains_quote(c.evidence) and corpus.contains_text(c.name):
            certifications.append(
                CertificationOut(
                    name=c.name,
                    issuer=c.issuer if c.issuer and corpus.contains_text(c.issuer) else None,
                    year=c.year if c.year and c.year in corpus.alnum else None,
                    evidence=c.evidence,
                )
            )
        else:
            dropped.certifications += 1

    return (
        {
            "skills": skills,
            "education": education,
            "experience": experience,
            "projects": projects,
            "certifications": certifications,
        },
        dropped,
    )
