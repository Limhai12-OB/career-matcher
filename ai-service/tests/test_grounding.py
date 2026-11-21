from app.analysis.grounding import ground
from tests.docs import SAMPLE_CV_LINES
from tests.fakes import extraction

TEXT = "\n".join(SAMPLE_CV_LINES)


def skill(name, evidence, category="PROGRAMMING_LANGUAGE"):
    return {"name": name, "category": category, "evidence": evidence}


def test_supported_skills_are_kept_and_hallucinations_dropped():
    ex = extraction(
        skills=[
            skill("Java", "Java, Spring Boot, PostgreSQL, Docker"),
            skill("Kubernetes", "Java, Spring Boot, PostgreSQL, Docker"),  # not in evidence
            skill("Rust", "Expert in Rust and systems programming"),  # evidence not in CV
            skill("java", "Built REST APIs with Java"),  # duplicate
        ]
    )
    items, dropped = ground(ex, TEXT)
    assert [s.name for s in items["skills"]] == ["Java"]
    assert dropped.skills == 2


def test_evidence_matching_tolerates_whitespace_and_punctuation():
    ex = extraction(skills=[skill("Docker", "java,  spring boot, postgresql,docker")])
    items, _ = ground(ex, TEXT)
    assert len(items["skills"]) == 1


def test_whole_term_matching_distinguishes_similar_names():
    ex = extraction(
        skills=[skill("C", "Java, Spring Boot"), skill("Spring", "Java, Spring Boot, PostgreSQL")]
    )
    items, dropped = ground(ex, "C++ and Java, Spring Boot, PostgreSQL")
    # "C" is not a whole term in "C++"; Spring is a term inside "Spring Boot".
    assert [s.name for s in items["skills"]] == ["Spring"]
    assert dropped.skills == 1


def test_experience_requires_title_and_organization_in_text():
    ok = {
        "title": "Backend Intern",
        "organization": "ABC Software Co.",
        "employment_type": "INTERNSHIP",
        "start_date": "2024-06",
        "end_date": "2024-12",
        "is_current": False,
        "skills": ["Java", "Spring Boot", "Kafka"],
        "evidence": "Backend Intern, ABC Software Co., 2024-06 - 2024-12",
    }
    fake = {**ok, "organization": "Google", "evidence": "Senior Engineer at Google"}
    items, dropped = ground(extraction(experience=[ok, fake]), TEXT)
    assert [x.organization for x in items["experience"]] == ["ABC Software Co."]
    assert items["experience"][0].skills == ["Java", "Spring Boot"]  # Kafka not in CV
    assert dropped.experience == 1


def test_dates_whose_year_is_not_in_the_cv_are_nulled():
    edu = {
        "institution": "Royal University of Phnom Penh",
        "degree": "Bachelor of Science",
        "field_of_study": "Computer Science",
        "level": "BACHELOR",
        "start_date": "2019",
        "end_date": "2025",
        "ongoing": False,
        "evidence": "Bachelor of Science in Computer Science, Royal University of Phnom Penh",
    }
    items, _ = ground(extraction(education=[edu]), TEXT)
    assert items["education"][0].start_date is None
    assert items["education"][0].end_date == "2025"


def test_unsupported_degree_and_issuer_are_nulled_not_kept():
    cert = {
        "name": "AWS Certified Cloud Practitioner",
        "issuer": "Microsoft",
        "year": "2030",
        "evidence": "AWS Certified Cloud Practitioner, Amazon Web Services, 2024",
    }
    items, _ = ground(extraction(certifications=[cert]), TEXT)
    assert items["certifications"][0].issuer is None
    assert items["certifications"][0].year is None


def test_dotted_names_are_single_terms():
    # "js" inside "Node.js" is not a mention of JavaScript ("JS").
    ex = extraction(
        skills=[
            skill("JS", "a React and Node.js web app", "PROGRAMMING_LANGUAGE"),
            skill("Node.js", "a React and Node.js web app", "FRAMEWORK_LIBRARY"),
            skill(".NET", "Built services in .NET and C#.", "FRAMEWORK_LIBRARY"),
            skill("C#", "Built services in .NET and C#.", "PROGRAMMING_LANGUAGE"),
            skill("Node", "a React and Node.js web app", "FRAMEWORK_LIBRARY"),
        ]
    )
    text = "a React and Node.js web app\nBuilt services in .NET and C#."
    items, dropped = ground(ex, text)
    assert [s.name for s in items["skills"]] == ["Node.js", ".NET", "C#"]
    assert dropped.skills == 2
