-- Skill gap analysis: curated learning resources and stored gap reports.

-- Curated, free learning resources (official documentation and courses). URLs were checked to
-- resolve on 2026-11-21. The AI never invents links: roadmaps may only reference these rows by id.
CREATE TABLE skill_resources (
    id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    skill_id       UUID          NOT NULL REFERENCES skills (id) ON DELETE CASCADE,
    title          VARCHAR(200)  NOT NULL,
    provider       VARCHAR(100)  NOT NULL,
    resource_type  VARCHAR(20)   NOT NULL,
    url            VARCHAR(500)  NOT NULL,
    verified_on    DATE          NOT NULL,
    CONSTRAINT uq_skill_resources UNIQUE (skill_id, url),
    CONSTRAINT ck_skill_resources_type CHECK (resource_type IN ('DOCUMENTATION', 'TUTORIAL', 'COURSE')),
    CONSTRAINT ck_skill_resources_url CHECK (url ~ '^https://')
);

CREATE INDEX ix_skill_resources_skill ON skill_resources (skill_id);

INSERT INTO skill_resources (skill_id, title, provider, resource_type, url, verified_on)
SELECT s.id, v.title, v.provider, v.resource_type, v.url, DATE '2026-11-21' FROM (VALUES
    ('java', 'Learn Java (dev.java)', 'Oracle', 'TUTORIAL', 'https://dev.java/learn/'),
    ('spring-boot', 'Building an Application with Spring Boot', 'Spring', 'TUTORIAL', 'https://spring.io/guides/gs/spring-boot/'),
    ('sql', 'The SQL Language (PostgreSQL tutorial)', 'PostgreSQL', 'TUTORIAL', 'https://www.postgresql.org/docs/current/tutorial-sql.html'),
    ('postgresql', 'PostgreSQL Tutorial', 'PostgreSQL', 'DOCUMENTATION', 'https://www.postgresql.org/docs/current/tutorial.html'),
    ('python', 'The Python Tutorial', 'Python Software Foundation', 'TUTORIAL', 'https://docs.python.org/3/tutorial/'),
    ('javascript', 'JavaScript: Dynamic client-side scripting', 'MDN Web Docs', 'COURSE', 'https://developer.mozilla.org/en-US/docs/Learn_web_development/Core/Scripting'),
    ('typescript', 'The TypeScript Handbook', 'Microsoft', 'DOCUMENTATION', 'https://www.typescriptlang.org/docs/handbook/intro.html'),
    ('html', 'Structuring content with HTML', 'MDN Web Docs', 'COURSE', 'https://developer.mozilla.org/en-US/docs/Learn_web_development/Core/Structuring_content'),
    ('css', 'CSS styling basics', 'MDN Web Docs', 'COURSE', 'https://developer.mozilla.org/en-US/docs/Learn_web_development/Core/Styling_basics'),
    ('react', 'Learn React', 'React', 'TUTORIAL', 'https://react.dev/learn'),
    ('next-js', 'Learn Next.js', 'Vercel', 'COURSE', 'https://nextjs.org/learn'),
    ('node-js', 'Introduction to Node.js', 'OpenJS Foundation', 'TUTORIAL', 'https://nodejs.org/en/learn/getting-started/introduction-to-nodejs'),
    ('docker', 'Get started with Docker', 'Docker', 'TUTORIAL', 'https://docs.docker.com/get-started/'),
    ('kubernetes', 'Learn Kubernetes Basics', 'Kubernetes', 'TUTORIAL', 'https://kubernetes.io/docs/tutorials/kubernetes-basics/'),
    ('git', 'Pro Git book', 'Git', 'DOCUMENTATION', 'https://git-scm.com/book/en/v2'),
    ('github', 'Get started with GitHub', 'GitHub', 'DOCUMENTATION', 'https://docs.github.com/en/get-started'),
    ('linux', 'The Linux command line for beginners', 'Canonical (Ubuntu)', 'TUTORIAL', 'https://ubuntu.com/tutorials/command-line-for-beginners'),
    ('amazon-web-services', 'Getting Started with AWS', 'Amazon Web Services', 'COURSE', 'https://aws.amazon.com/getting-started/'),
    ('microsoft-azure', 'Azure training', 'Microsoft Learn', 'COURSE', 'https://learn.microsoft.com/en-us/training/azure/'),
    ('google-cloud-platform', 'Get started with Google Cloud', 'Google Cloud', 'DOCUMENTATION', 'https://cloud.google.com/docs/get-started'),
    ('rest-apis', 'An overview of HTTP', 'MDN Web Docs', 'DOCUMENTATION', 'https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/Overview'),
    ('fastapi', 'FastAPI Tutorial - User Guide', 'FastAPI', 'TUTORIAL', 'https://fastapi.tiangolo.com/tutorial/'),
    ('django', 'Writing your first Django app', 'Django', 'TUTORIAL', 'https://docs.djangoproject.com/en/stable/intro/tutorial01/'),
    ('flask', 'Flask Tutorial', 'Pallets', 'TUTORIAL', 'https://flask.palletsprojects.com/en/stable/tutorial/'),
    ('kotlin', 'Get started with Kotlin', 'JetBrains', 'DOCUMENTATION', 'https://kotlinlang.org/docs/getting-started.html'),
    ('android', 'Android developer courses', 'Google', 'COURSE', 'https://developer.android.com/courses'),
    ('flutter', 'Write your first Flutter app', 'Google', 'TUTORIAL', 'https://docs.flutter.dev/get-started/codelab'),
    ('dart', 'Introduction to Dart', 'Google', 'DOCUMENTATION', 'https://dart.dev/language'),
    ('csharp', 'A tour of the C# language', 'Microsoft Learn', 'DOCUMENTATION', 'https://learn.microsoft.com/en-us/dotnet/csharp/tour-of-csharp/'),
    ('net', 'Get started with .NET', 'Microsoft Learn', 'DOCUMENTATION', 'https://learn.microsoft.com/en-us/dotnet/core/get-started'),
    ('go', 'A Tour of Go', 'Go', 'TUTORIAL', 'https://go.dev/tour/'),
    ('php', 'PHP manual: A simple tutorial', 'PHP', 'TUTORIAL', 'https://www.php.net/manual/en/tutorial.php'),
    ('mongodb', 'Getting started with MongoDB', 'MongoDB', 'DOCUMENTATION', 'https://www.mongodb.com/docs/manual/tutorial/getting-started/'),
    ('junit', 'JUnit User Guide', 'JUnit', 'DOCUMENTATION', 'https://docs.junit.org/current/user-guide/'),
    ('unit-testing', 'Testing your code (Python unittest docs)', 'Python Software Foundation', 'DOCUMENTATION', 'https://docs.python.org/3/library/unittest.html'),
    ('pandas', 'Getting started with pandas', 'pandas', 'TUTORIAL', 'https://pandas.pydata.org/docs/getting_started/index.html'),
    ('scikit-learn', 'Getting started with scikit-learn', 'scikit-learn', 'TUTORIAL', 'https://scikit-learn.org/stable/getting_started.html'),
    ('machine-learning', 'Machine Learning Crash Course', 'Google', 'COURSE', 'https://developers.google.com/machine-learning/crash-course'),
    ('selenium', 'Getting started with WebDriver', 'Selenium', 'DOCUMENTATION', 'https://www.selenium.dev/documentation/webdriver/getting_started/'),
    ('github-actions', 'Quickstart for GitHub Actions', 'GitHub', 'TUTORIAL', 'https://docs.github.com/en/actions/get-started/quickstart'),
    ('ci-cd', 'Continuous integration with GitHub Actions', 'GitHub', 'DOCUMENTATION', 'https://docs.github.com/en/actions/get-started/continuous-integration'),
    ('terraform', 'Terraform tutorials', 'HashiCorp', 'TUTORIAL', 'https://developer.hashicorp.com/terraform/tutorials'),
    ('power-bi', 'Power BI training', 'Microsoft Learn', 'COURSE', 'https://learn.microsoft.com/en-us/training/powerplatform/power-bi'),
    ('angular', 'Angular tutorials', 'Google', 'TUTORIAL', 'https://angular.dev/tutorials'),
    ('vue-js', 'Vue.js tutorial', 'Vue.js', 'TUTORIAL', 'https://vuejs.org/tutorial/'),
    ('tailwind-css', 'Tailwind CSS documentation', 'Tailwind Labs', 'DOCUMENTATION', 'https://tailwindcss.com/docs/installation'),
    ('scrum', 'The Scrum Guide', 'Scrum.org / Scrum Inc.', 'DOCUMENTATION', 'https://scrumguides.org/scrum-guide.html'),
    ('data-structures-and-algorithms', 'Algorithms, Part I (Princeton)', 'Coursera', 'COURSE', 'https://www.coursera.org/learn/algorithms-part1')
) AS v(slug, title, provider, resource_type, url) JOIN skills s ON s.slug = v.slug;

-- One row per analysis run (history is kept to show progress).
CREATE TABLE skill_gap_reports (
    id                       UUID           PRIMARY KEY,
    user_id                  UUID           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    algorithm_version        VARCHAR(20)    NOT NULL,
    match_algorithm_version  VARCHAR(20)    NOT NULL,
    target_job_count         INTEGER        NOT NULL,
    average_score            NUMERIC(4, 1)  NOT NULL,
    required_coverage        NUMERIC(4, 1)  NOT NULL,
    critical_count           INTEGER        NOT NULL,
    high_count               INTEGER        NOT NULL,
    -- Full GapReport (targets, gaps with counts and measured score gains, strengths).
    report                   JSONB          NOT NULL,
    -- Validated learning roadmap generated for this report (optional, generated on request).
    roadmap                  JSONB,
    roadmap_provider         VARCHAR(40),
    roadmap_model            VARCHAR(80),
    roadmap_prompt_version   VARCHAR(40),
    roadmap_created_at       TIMESTAMPTZ,
    created_at               TIMESTAMPTZ    NOT NULL,
    version                  BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT ck_gap_reports_scores CHECK (average_score BETWEEN 0 AND 100 AND required_coverage BETWEEN 0 AND 100),
    CONSTRAINT ck_gap_reports_counts CHECK (target_job_count >= 0 AND critical_count >= 0 AND high_count >= 0),
    CONSTRAINT ck_gap_reports_roadmap CHECK (roadmap IS NULL OR (roadmap_provider IS NOT NULL AND roadmap_created_at IS NOT NULL))
);

CREATE INDEX ix_skill_gap_reports_user ON skill_gap_reports (user_id, created_at DESC);
