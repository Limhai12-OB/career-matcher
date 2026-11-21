"""Typed document-processing failures, mapped to HTTP responses by the API layer."""

from enum import StrEnum


class DocumentErrorCode(StrEnum):
    UNSUPPORTED_FORMAT = "UNSUPPORTED_FORMAT"
    DOCUMENT_TOO_LARGE = "DOCUMENT_TOO_LARGE"
    MALFORMED_DOCUMENT = "MALFORMED_DOCUMENT"
    UNSAFE_DOCUMENT = "UNSAFE_DOCUMENT"
    MACROS_NOT_ALLOWED = "MACROS_NOT_ALLOWED"
    ENCRYPTED_DOCUMENT = "ENCRYPTED_DOCUMENT"
    TOO_MANY_PAGES = "TOO_MANY_PAGES"
    NO_EXTRACTABLE_TEXT = "NO_EXTRACTABLE_TEXT"
    DOCUMENT_TOO_LONG = "DOCUMENT_TOO_LONG"
    EXTRACTION_TIMEOUT = "EXTRACTION_TIMEOUT"


MESSAGES: dict[DocumentErrorCode, str] = {
    DocumentErrorCode.UNSUPPORTED_FORMAT: "Only PDF and DOCX files are supported.",
    DocumentErrorCode.DOCUMENT_TOO_LARGE: "The file is too large.",
    DocumentErrorCode.MALFORMED_DOCUMENT: "The file is damaged or not a valid PDF/DOCX document.",
    DocumentErrorCode.UNSAFE_DOCUMENT: "The document contains unsafe content and was rejected.",
    DocumentErrorCode.MACROS_NOT_ALLOWED: "Documents containing macros are not allowed.",
    DocumentErrorCode.ENCRYPTED_DOCUMENT: "Password-protected documents are not supported.",
    DocumentErrorCode.TOO_MANY_PAGES: "The document has too many pages for a CV.",
    DocumentErrorCode.NO_EXTRACTABLE_TEXT: (
        "No readable text was found. The file may be empty or a scanned image."
    ),
    DocumentErrorCode.DOCUMENT_TOO_LONG: "The document contains too much text for a CV.",
    DocumentErrorCode.EXTRACTION_TIMEOUT: "Reading the document took too long.",
}


class DocumentError(Exception):
    def __init__(self, code: DocumentErrorCode) -> None:
        super().__init__(code.value)
        self.code = code

    @property
    def message(self) -> str:
        return MESSAGES[self.code]
