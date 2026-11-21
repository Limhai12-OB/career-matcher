"""
Deterministic, offline extractor used when no LLM provider is configured.

It only reports skills from a fixed lexicon and education lines it can literally find, each with
the line it came from as evidence. It never infers anything, so it is safe but limited
(experience, projects and certifications are not extracted).
"""

from __future__ import annotations

import re

from app.analysis.providers.base import ProviderResult
from app.analysis.schemas import (
    CvExtraction,
    EducationLevel,
    ExtractedEducation,
    ExtractedSkill,
    SkillCategory,
)

C = SkillCategory
# (canonical name as reported, category, case-insensitive term patterns)
LEXICON: list[tuple[str, SkillCategory, list[str]]] = [
    ("Java", C.PROGRAMMING_LANGUAGE, [r"java"]),
    ("JavaScript", C.PROGRAMMING_LANGUAGE, [r"javascript", r"js", r"ecmascript", r"es6"]),
    ("TypeScript", C.PROGRAMMING_LANGUAGE, [r"typescript"]),
    ("Python", C.PROGRAMMING_LANGUAGE, [r"python"]),
    ("C++", C.PROGRAMMING_LANGUAGE, [r"c\+\+", r"cpp"]),
    ("C#", C.PROGRAMMING_LANGUAGE, [r"c#", r"csharp"]),
    ("Go", C.PROGRAMMING_LANGUAGE, [r"golang"]),
    ("Kotlin", C.PROGRAMMING_LANGUAGE, [r"kotlin"]),
    ("Swift", C.PROGRAMMING_LANGUAGE, [r"swift"]),
    ("PHP", C.PROGRAMMING_LANGUAGE, [r"php"]),
    ("Ruby", C.PROGRAMMING_LANGUAGE, [r"ruby"]),
    ("Rust", C.PROGRAMMING_LANGUAGE, [r"rust"]),
    ("Dart", C.PROGRAMMING_LANGUAGE, [r"dart"]),
    ("SQL", C.PROGRAMMING_LANGUAGE, [r"sql"]),
    ("HTML", C.PROGRAMMING_LANGUAGE, [r"html5?"]),
    ("CSS", C.PROGRAMMING_LANGUAGE, [r"css3?"]),
    ("Spring Boot", C.FRAMEWORK_LIBRARY, [r"spring boot", r"springboot"]),
    ("Spring", C.FRAMEWORK_LIBRARY, [r"spring framework", r"spring mvc"]),
    ("React", C.FRAMEWORK_LIBRARY, [r"react", r"react\.js", r"reactjs"]),
    ("Next.js", C.FRAMEWORK_LIBRARY, [r"next\.js", r"nextjs"]),
    ("Angular", C.FRAMEWORK_LIBRARY, [r"angular"]),
    ("Vue.js", C.FRAMEWORK_LIBRARY, [r"vue", r"vue\.js", r"vuejs"]),
    ("Node.js", C.FRAMEWORK_LIBRARY, [r"node\.js", r"nodejs", r"node"]),
    ("Express", C.FRAMEWORK_LIBRARY, [r"express\.js", r"expressjs"]),
    ("Django", C.FRAMEWORK_LIBRARY, [r"django"]),
    ("Flask", C.FRAMEWORK_LIBRARY, [r"flask"]),
    ("FastAPI", C.FRAMEWORK_LIBRARY, [r"fastapi"]),
    (".NET", C.FRAMEWORK_LIBRARY, [r"\.net", r"asp\.net", r"dotnet"]),
    ("Laravel", C.FRAMEWORK_LIBRARY, [r"laravel"]),
    ("Flutter", C.FRAMEWORK_LIBRARY, [r"flutter"]),
    ("React Native", C.FRAMEWORK_LIBRARY, [r"react native"]),
    ("Tailwind CSS", C.FRAMEWORK_LIBRARY, [r"tailwind", r"tailwindcss", r"tailwind css"]),
    ("Bootstrap", C.FRAMEWORK_LIBRARY, [r"bootstrap"]),
    ("Hibernate", C.FRAMEWORK_LIBRARY, [r"hibernate"]),
    ("Pandas", C.FRAMEWORK_LIBRARY, [r"pandas"]),
    ("NumPy", C.FRAMEWORK_LIBRARY, [r"numpy"]),
    ("TensorFlow", C.FRAMEWORK_LIBRARY, [r"tensorflow"]),
    ("PyTorch", C.FRAMEWORK_LIBRARY, [r"pytorch"]),
    ("scikit-learn", C.FRAMEWORK_LIBRARY, [r"scikit-learn", r"sklearn"]),
    ("JUnit", C.FRAMEWORK_LIBRARY, [r"junit"]),
    ("Jest", C.FRAMEWORK_LIBRARY, [r"jest"]),
    ("PostgreSQL", C.DATABASE, [r"postgresql", r"postgres"]),
    ("MySQL", C.DATABASE, [r"mysql"]),
    ("MongoDB", C.DATABASE, [r"mongodb", r"mongo"]),
    ("Redis", C.DATABASE, [r"redis"]),
    ("SQLite", C.DATABASE, [r"sqlite"]),
    ("Oracle Database", C.DATABASE, [r"oracle db", r"oracle database"]),
    ("Microsoft SQL Server", C.DATABASE, [r"sql server", r"mssql"]),
    ("Firebase", C.DATABASE, [r"firebase"]),
    ("Docker", C.CLOUD_DEVOPS, [r"docker"]),
    ("Kubernetes", C.CLOUD_DEVOPS, [r"kubernetes", r"k8s"]),
    ("AWS", C.CLOUD_DEVOPS, [r"aws", r"amazon web services"]),
    ("Microsoft Azure", C.CLOUD_DEVOPS, [r"azure"]),
    ("Google Cloud", C.CLOUD_DEVOPS, [r"gcp", r"google cloud"]),
    ("Linux", C.CLOUD_DEVOPS, [r"linux", r"ubuntu"]),
    ("CI/CD", C.CLOUD_DEVOPS, [r"ci/cd", r"ci cd", r"continuous integration"]),
    ("GitHub Actions", C.CLOUD_DEVOPS, [r"github actions"]),
    ("Jenkins", C.CLOUD_DEVOPS, [r"jenkins"]),
    ("Terraform", C.CLOUD_DEVOPS, [r"terraform"]),
    ("Nginx", C.CLOUD_DEVOPS, [r"nginx"]),
    ("Git", C.TOOL, [r"git"]),
    ("GitHub", C.TOOL, [r"github"]),
    ("Jira", C.TOOL, [r"jira"]),
    ("Figma", C.TOOL, [r"figma"]),
    ("Postman", C.TOOL, [r"postman"]),
    ("Power BI", C.TOOL, [r"power bi", r"powerbi"]),
    ("Tableau", C.TOOL, [r"tableau"]),
    ("Microsoft Excel", C.TOOL, [r"excel"]),
    ("Maven", C.TOOL, [r"maven"]),
    ("REST APIs", C.METHODOLOGY, [r"rest", r"restful", r"rest api", r"rest apis"]),
    ("GraphQL", C.METHODOLOGY, [r"graphql"]),
    ("Microservices", C.METHODOLOGY, [r"microservices?"]),
    ("Agile", C.METHODOLOGY, [r"agile"]),
    ("Scrum", C.METHODOLOGY, [r"scrum"]),
    ("Object-Oriented Programming", C.METHODOLOGY, [r"oop", r"object[- ]oriented programming"]),
    ("Unit Testing", C.METHODOLOGY, [r"unit testing", r"unit tests"]),
    ("Machine Learning", C.METHODOLOGY, [r"machine learning"]),
    ("Data Structures and Algorithms", C.METHODOLOGY, [r"data structures"]),
    ("Communication", C.SOFT_SKILL, [r"communication"]),
    ("Teamwork", C.SOFT_SKILL, [r"teamwork", r"team player"]),
    ("Problem Solving", C.SOFT_SKILL, [r"problem[- ]solving"]),
    ("Leadership", C.SOFT_SKILL, [r"leadership"]),
]

_COMPILED = [
    (
        name,
        cat,
        # Term boundaries: a dot joins words ("Node.js" must not yield "js"), except a leading
        # dot that is part of the pattern itself (".NET").
        re.compile(r"(?<![0-9a-z+#.])(?:" + "|".join(pats) + r")(?![0-9a-z+#]|\.[a-z])", re.I),
    )
    for name, cat, pats in LEXICON
]

_DEGREES: list[tuple[EducationLevel, re.Pattern[str]]] = [
    (EducationLevel.DOCTORATE, re.compile(r"\b(ph\.?\s?d|doctorate|doctor of)\b", re.I)),
    (EducationLevel.MASTER, re.compile(r"\b(master'?s?|m\.?\s?sc|msc|m\.?\s?eng|mba)\b", re.I)),
    (
        EducationLevel.BACHELOR,
        re.compile(r"\b(bachelor'?s?|b\.?\s?sc|bsc|b\.?\s?eng|b\.?\s?a\.|b\.?\s?tech)\b", re.I),
    ),
    (EducationLevel.ASSOCIATE, re.compile(r"\bassociate'?s? degree\b", re.I)),
    (EducationLevel.BOOTCAMP, re.compile(r"\bbootcamp\b", re.I)),
    (EducationLevel.HIGH_SCHOOL, re.compile(r"\bhigh school\b", re.I)),
]
_INSTITUTION = re.compile(
    r"([A-Z][\w.&'-]*(?:\s+(?:of|and|for|the|[A-Z][\w.&'-]*))*\s+"
    r"(?:University|Institute|College|Academy|School)(?:\s+of(?:\s+[A-Z][\w.&'-]*)+)?"
    r"|(?:University|Institute|College|Academy)\s+of(?:\s+[A-Z][\w.&'-]*)+)"
)
_YEAR = re.compile(r"\b(19[5-9]\d|20\d\d)\b")


def _evidence(line: str) -> str:
    return line.strip()[:200]


_SEGMENT_SPLIT = re.compile(r"\s*(?:,|\||;|\s[-–—]\s)\s*")


def _segment_with(line: str, pattern: re.Pattern[str]) -> str:
    """The comma/pipe/dash-separated part of a line that contains the match."""
    for part in _SEGMENT_SPLIT.split(line):
        if pattern.search(part):
            return part.strip()[:200]
    return _evidence(line)


class LocalProvider:
    name = "local"
    limited = True
    model = "rule-based-v1"

    async def extract(self, cv_text: str) -> ProviderResult:
        lines = [ln for ln in cv_text.split("\n") if ln.strip()]
        skills: list[ExtractedSkill] = []
        for _name, cat, pattern in _COMPILED:
            for line in lines:
                m = pattern.search(line)
                if m:
                    # Report the skill as written in the CV so grounding can verify it.
                    start = max(0, m.start() - 80)
                    evidence = line[start : m.end() + 80].strip()[:200]
                    skills.append(ExtractedSkill(name=m.group(0), category=cat, evidence=evidence))
                    break

        education: list[ExtractedEducation] = []
        for i, line in enumerate(lines):
            for level, pattern in _DEGREES:
                if pattern.search(line):
                    window = " ".join(lines[max(0, i - 1) : i + 2])
                    inst = _INSTITUTION.search(line) or _INSTITUTION.search(window)
                    if inst:
                        years = _YEAR.findall(window)
                        education.append(
                            ExtractedEducation(
                                institution=inst.group(0).strip()[:200],
                                degree=_segment_with(line, pattern),
                                field_of_study=None,
                                level=level,
                                start_date=years[0] if len(years) > 1 else None,
                                end_date=years[-1] if years else None,
                                ongoing=bool(
                                    re.search(r"\b(present|expected|current)\b", window, re.I)
                                ),
                                evidence=_evidence(line),
                            )
                        )
                    break
            if len(education) >= 10:
                break

        return ProviderResult(
            extraction=CvExtraction(
                skills=skills[:150],
                education=education,
                experience=[],
                projects=[],
                certifications=[],
                suspicious_content=False,
            ),
            model=self.model,
        )
