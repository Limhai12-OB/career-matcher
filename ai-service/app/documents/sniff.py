"""File type detection from content (never from the client-supplied name or MIME type)."""

from enum import StrEnum

from app.documents.errors import DocumentError, DocumentErrorCode


class DocumentType(StrEnum):
    PDF = "PDF"
    DOCX = "DOCX"


def detect_type(data: bytes) -> DocumentType:
    if not data:
        raise DocumentError(DocumentErrorCode.NO_EXTRACTABLE_TEXT)
    # PDF header may be preceded by a few junk bytes; the spec allows it within the first 1 KiB.
    if b"%PDF-" in data[:1024]:
        return DocumentType.PDF
    if data[:4] == b"PK\x03\x04":
        return DocumentType.DOCX  # validated as a real DOCX package during extraction
    raise DocumentError(DocumentErrorCode.UNSUPPORTED_FORMAT)
