"""Builders for test documents (valid, malformed and malicious)."""

from __future__ import annotations

import io
import zipfile


def _pdf_escape(s: str) -> str:
    return s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")


def make_pdf(pages: list[list[str]]) -> bytes:
    """Minimal valid PDF with Helvetica text; one list of lines per page."""
    objects: list[bytes] = []
    n_pages = len(pages)
    font_id = 3 + 2 * n_pages
    kids = " ".join(f"{3 + 2 * i} 0 R" for i in range(n_pages))
    objects.append(b"<< /Type /Catalog /Pages 2 0 R >>")
    objects.append(f"<< /Type /Pages /Kids [{kids}] /Count {n_pages} >>".encode())
    for i, lines in enumerate(pages):
        content_id = 4 + 2 * i
        objects.append(
            f"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents {content_id} 0 R "
            f"/Resources << /Font << /F1 {font_id} 0 R >> >> >>".encode()
        )
        ops = ["BT", "/F1 11 Tf", "14 TL", "50 750 Td"]
        for line in lines:
            ops.append(f"({_pdf_escape(line)}) Tj T*")
        ops.append("ET")
        stream = "\n".join(ops).encode("latin-1")
        objects.append(b"<< /Length %d >>\nstream\n" % len(stream) + stream + b"\nendstream")
    objects.append(b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>")

    out = io.BytesIO()
    out.write(b"%PDF-1.4\n")
    offsets = []
    for idx, obj in enumerate(objects, start=1):
        offsets.append(out.tell())
        out.write(f"{idx} 0 obj\n".encode() + obj + b"\nendobj\n")
    xref = out.tell()
    out.write(f"xref\n0 {len(objects) + 1}\n0000000000 65535 f \n".encode())
    for off in offsets:
        out.write(f"{off:010d} 00000 n \n".encode())
    out.write(
        f"trailer\n<< /Size {len(objects) + 1} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n".encode()
    )
    return out.getvalue()


def make_docx(paragraphs: list[str], table: list[list[str]] | None = None) -> bytes:
    from docx import Document

    doc = Document()
    for p in paragraphs:
        doc.add_paragraph(p)
    if table:
        t = doc.add_table(rows=len(table), cols=len(table[0]))
        for r, row in enumerate(table):
            for c, value in enumerate(row):
                t.cell(r, c).text = value
    buf = io.BytesIO()
    doc.save(buf)
    return buf.getvalue()


def rezip(
    docx: bytes, extra: dict[str, bytes] | None = None, replace: dict[str, bytes] | None = None
) -> bytes:
    """Rebuild a DOCX package with added or replaced parts."""
    src = zipfile.ZipFile(io.BytesIO(docx))
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as dst:
        for item in src.infolist():
            data = (replace or {}).get(item.filename, src.read(item.filename))
            dst.writestr(item.filename, data)
        for name, data in (extra or {}).items():
            dst.writestr(name, data)
    return buf.getvalue()


SAMPLE_CV_LINES = [
    "Sokha Chan",
    "Junior Backend Developer",
    "EDUCATION",
    "Bachelor of Science in Computer Science, Royal University of Phnom Penh, 2021 - 2025",
    "EXPERIENCE",
    "Backend Intern, ABC Software Co., 2024-06 - 2024-12",
    "Built REST APIs with Java and Spring Boot backed by PostgreSQL.",
    "PROJECTS",
    "Campus Events App - a React and Node.js web app for student clubs.",
    "SKILLS",
    "Java, Spring Boot, PostgreSQL, Docker, Git, React, Teamwork",
    "CERTIFICATIONS",
    "AWS Certified Cloud Practitioner, Amazon Web Services, 2024",
]
