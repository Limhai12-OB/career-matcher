"""
Text extraction from PDF and DOCX.

Parsing untrusted documents is the riskiest step, so it runs in a separate process with a
wall-clock timeout (and, on Linux, a memory cap). A malicious file can at worst kill that
child process, never the service.
"""

from __future__ import annotations

import io
import multiprocessing as mp
import zipfile
from dataclasses import dataclass

from app.documents.errors import DocumentError, DocumentErrorCode
from app.documents.sniff import DocumentType

MAX_ZIP_ENTRIES = 1000
MAX_ZIP_UNCOMPRESSED = 50 * 1024 * 1024
MAX_ENTRY_RATIO = 200  # compressed -> uncompressed ratio above this is treated as a zip bomb
CHILD_MEMORY_LIMIT = 768 * 1024 * 1024


@dataclass(frozen=True)
class ExtractedText:
    text: str
    pages: int | None


# ---------------------------------------------------------------- DOCX


def validate_docx_package(data: bytes) -> None:
    """Structural and safety checks on the DOCX ZIP container, before any XML parsing."""
    try:
        zf = zipfile.ZipFile(io.BytesIO(data))
    except (zipfile.BadZipFile, ValueError, OSError) as exc:
        raise DocumentError(DocumentErrorCode.MALFORMED_DOCUMENT) from exc
    with zf:
        infos = zf.infolist()
        if len(infos) > MAX_ZIP_ENTRIES:
            raise DocumentError(DocumentErrorCode.UNSAFE_DOCUMENT)
        names = {i.filename for i in infos}
        if "word/document.xml" not in names or "[Content_Types].xml" not in names:
            # A ZIP that is not a Word document (e.g. .xlsx, .jar, plain archive).
            raise DocumentError(DocumentErrorCode.UNSUPPORTED_FORMAT)
        if any(n.lower().endswith("vbaproject.bin") for n in names):
            raise DocumentError(DocumentErrorCode.MACROS_NOT_ALLOWED)
        total = 0
        for info in infos:
            total += info.file_size
            if total > MAX_ZIP_UNCOMPRESSED:
                raise DocumentError(DocumentErrorCode.UNSAFE_DOCUMENT)
            if (
                info.compress_size
                and info.file_size / info.compress_size > MAX_ENTRY_RATIO
                and (info.file_size > 1024 * 1024)
            ):
                raise DocumentError(DocumentErrorCode.UNSAFE_DOCUMENT)
        try:
            content_types = zf.read("[Content_Types].xml")
            if b"macroEnabled" in content_types:
                raise DocumentError(DocumentErrorCode.MACROS_NOT_ALLOWED)
            # OOXML parts never legitimately declare DTDs; DTDs enable XXE / entity expansion.
            for info in infos:
                if info.filename.endswith((".xml", ".rels")):
                    with zf.open(info) as part:
                        chunk = part.read(MAX_ZIP_UNCOMPRESSED)
                    if b"<!DOCTYPE" in chunk or b"<!ENTITY" in chunk:
                        raise DocumentError(DocumentErrorCode.UNSAFE_DOCUMENT)
        except (zipfile.BadZipFile, EOFError, OSError, RuntimeError) as exc:
            # Truncated archives, bad CRCs, unsupported compression, encrypted entries.
            raise DocumentError(DocumentErrorCode.MALFORMED_DOCUMENT) from exc


def _docx_text(data: bytes) -> ExtractedText:
    from docx import Document  # imported in the child process only

    try:
        doc = Document(io.BytesIO(data))
    except Exception as exc:  # noqa: BLE001 - any parser failure means a malformed file
        raise DocumentError(DocumentErrorCode.MALFORMED_DOCUMENT) from exc

    lines: list[str] = []
    for section in doc.sections:
        for para in section.header.paragraphs:
            lines.append(para.text)
    for para in doc.paragraphs:
        lines.append(para.text)
    for table in doc.tables:
        for row in table.rows:
            cells: list[str] = []
            for cell in row.cells:
                text = cell.text.strip()
                if text and (not cells or cells[-1] != text):  # merged cells repeat text
                    cells.append(text)
            if cells:
                lines.append(" | ".join(cells))
    return ExtractedText(text="\n".join(lines), pages=None)


# ---------------------------------------------------------------- PDF


def _pdf_text(data: bytes, max_pages: int) -> ExtractedText:
    from pypdf import PdfReader
    from pypdf.errors import PyPdfError

    try:
        reader = PdfReader(io.BytesIO(data), strict=False)
        if reader.is_encrypted:
            # Many PDFs are "encrypted" with only an owner password; an empty user password opens
            # them. Anything else needs a password we don't have.
            try:
                if not reader.decrypt(""):
                    raise DocumentError(DocumentErrorCode.ENCRYPTED_DOCUMENT)
            except DocumentError:
                raise
            except Exception as exc:  # noqa: BLE001
                raise DocumentError(DocumentErrorCode.ENCRYPTED_DOCUMENT) from exc
        page_count = len(reader.pages)
        if page_count == 0:
            raise DocumentError(DocumentErrorCode.NO_EXTRACTABLE_TEXT)
        if page_count > max_pages:
            raise DocumentError(DocumentErrorCode.TOO_MANY_PAGES)
        texts = [page.extract_text() or "" for page in reader.pages]
    except DocumentError:
        raise
    except (
        PyPdfError,
        ValueError,
        KeyError,
        TypeError,
        IndexError,
        AttributeError,
        OSError,
    ) as exc:
        raise DocumentError(DocumentErrorCode.MALFORMED_DOCUMENT) from exc
    except RecursionError as exc:
        raise DocumentError(DocumentErrorCode.UNSAFE_DOCUMENT) from exc
    return ExtractedText(text="\n".join(texts), pages=page_count)


# ---------------------------------------------------------------- isolation


def _child(
    conn, data: bytes, doc_type: str, max_pages: int
) -> None:  # pragma: no cover - subprocess
    try:
        try:
            import resource

            resource.setrlimit(resource.RLIMIT_AS, (CHILD_MEMORY_LIMIT, CHILD_MEMORY_LIMIT))
        except (ImportError, ValueError, OSError):
            pass  # not available on this platform (e.g. macOS); timeout still applies
        result = _pdf_text(data, max_pages) if doc_type == DocumentType.PDF else _docx_text(data)
        conn.send(("ok", result.text, result.pages))
    except DocumentError as exc:
        conn.send(("error", exc.code.value, None))
    except MemoryError:
        conn.send(("error", DocumentErrorCode.UNSAFE_DOCUMENT.value, None))
    except Exception:  # noqa: BLE001
        conn.send(("error", DocumentErrorCode.MALFORMED_DOCUMENT.value, None))
    finally:
        conn.close()


def extract_text(
    data: bytes, doc_type: DocumentType, *, max_pages: int, timeout_seconds: float
) -> ExtractedText:
    """Blocking; call via a worker thread from async code."""
    if doc_type == DocumentType.DOCX:
        validate_docx_package(data)  # cheap checks in-process, no XML parsing

    ctx = mp.get_context("spawn")
    parent_conn, child_conn = ctx.Pipe(duplex=False)
    proc = ctx.Process(
        target=_child, args=(child_conn, data, doc_type.value, max_pages), daemon=True
    )
    proc.start()
    child_conn.close()
    try:
        if not parent_conn.poll(timeout_seconds):
            raise DocumentError(DocumentErrorCode.EXTRACTION_TIMEOUT)
        try:
            status, value, pages = parent_conn.recv()
        except EOFError as exc:  # child died (e.g. killed by memory limit)
            raise DocumentError(DocumentErrorCode.UNSAFE_DOCUMENT) from exc
    finally:
        if proc.is_alive():
            proc.kill()
        proc.join(timeout=5)
        parent_conn.close()
    if status == "error":
        raise DocumentError(DocumentErrorCode(value))
    return ExtractedText(text=value, pages=pages)
