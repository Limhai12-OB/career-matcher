/** CV upload rules and user-facing text for analysis states (pure, unit-tested). */
import type { AnalysisWarning, SkillCategory } from "@/lib/api/types";

export const MAX_CV_BYTES = 5 * 1024 * 1024;
export const ACCEPTED_CV_TYPES =
  ".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document";

/** Client-side pre-check; the server re-validates content and type. */
export function validateCvFile(file: { name: string; size: number }): string | null {
  const name = file.name.toLowerCase();
  if (!name.endsWith(".pdf") && !name.endsWith(".docx")) {
    return "Please upload a PDF or Word (.docx) file.";
  }
  if (file.size === 0) return "This file is empty.";
  if (file.size > MAX_CV_BYTES) return "The file is larger than 5 MB.";
  return null;
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/** Why an analysis failed, in plain language, and what the user can do. */
export const ANALYSIS_ERROR_MESSAGES: Record<string, string> = {
  NO_EXTRACTABLE_TEXT:
    "We couldn't find any readable text. If your CV is a scanned image, export it as a text-based PDF or Word file.",
  ENCRYPTED_DOCUMENT: "The document is password-protected. Remove the password and upload it again.",
  MALFORMED_DOCUMENT: "The file appears to be damaged. Try exporting it again from your editor.",
  UNSAFE_DOCUMENT: "The file was rejected for safety reasons. Try exporting a fresh copy.",
  MACROS_NOT_ALLOWED: "Documents with macros aren't allowed. Save it as a regular .docx file.",
  UNSUPPORTED_FORMAT: "This file type isn't supported. Upload a PDF or .docx file.",
  TOO_MANY_PAGES: "The document has too many pages for a CV (maximum 20).",
  DOCUMENT_TOO_LONG: "The document contains more text than a typical CV. Upload a shorter version.",
  DOCUMENT_TOO_LARGE: "The file is too large.",
  EXTRACTION_TIMEOUT: "Reading the document took too long. Try a simpler export of your CV.",
  AI_NOT_CONFIGURED: "CV analysis is temporarily unavailable. Please try again later.",
  AI_UNAVAILABLE: "Our analysis service is temporarily unavailable. Please try again in a few minutes.",
  AI_RATE_LIMITED: "Our analysis service is busy. Please try again in a few minutes.",
  AI_TIMEOUT: "The analysis took too long. Please try again.",
  AI_REFUSED: "The analysis service declined to process this document.",
  AI_INVALID_OUTPUT: "The analysis produced a result we couldn't verify, so nothing was saved. Please try again.",
  STORAGE_ERROR: "We couldn't read your stored file. Please upload it again.",
  SERVICE_BUSY: "We're processing many CVs right now. Please try again shortly.",
  STALE: "The analysis didn't finish in time. Please try again.",
  INTERRUPTED: "The analysis was interrupted. Please try again.",
  INTERNAL_ERROR: "Something went wrong while analyzing your CV. Please try again.",
};

/** Errors caused by the file itself: re-running the same file won't help. */
export const DOCUMENT_ERRORS = new Set([
  "NO_EXTRACTABLE_TEXT",
  "ENCRYPTED_DOCUMENT",
  "MALFORMED_DOCUMENT",
  "UNSAFE_DOCUMENT",
  "MACROS_NOT_ALLOWED",
  "UNSUPPORTED_FORMAT",
  "TOO_MANY_PAGES",
  "DOCUMENT_TOO_LONG",
  "DOCUMENT_TOO_LARGE",
]);

export function analysisErrorMessage(code: string | null): string {
  return (code && ANALYSIS_ERROR_MESSAGES[code]) || ANALYSIS_ERROR_MESSAGES.INTERNAL_ERROR;
}

export const WARNING_MESSAGES: Record<AnalysisWarning, string> = {
  INSTRUCTION_LIKE_TEXT_REMOVED:
    "Some text in your CV looked like instructions to an AI system. It was ignored and not used in the analysis.",
  MODEL_FLAGGED_SUSPICIOUS_CONTENT:
    "The analysis flagged text addressed to an AI system. It was not followed.",
  UNVERIFIED_ITEMS_REMOVED:
    "Some items were left out because they couldn't be matched to text in your CV. We only show what your CV actually says.",
  LIMITED_EXTRACTION:
    "Basic analysis mode: only skills and education were detected. Experience, projects and certifications need the full AI analysis.",
};

export const SKILL_CATEGORY_LABELS: Record<SkillCategory, string> = {
  PROGRAMMING_LANGUAGE: "Languages",
  FRAMEWORK_LIBRARY: "Frameworks & libraries",
  DATABASE: "Databases",
  CLOUD_DEVOPS: "Cloud & DevOps",
  TOOL: "Tools",
  METHODOLOGY: "Practices",
  SOFT_SKILL: "Soft skills",
  OTHER: "Other",
};

export const SKILL_CATEGORY_ORDER = Object.keys(SKILL_CATEGORY_LABELS) as SkillCategory[];

export function groupByCategory<T extends { category: SkillCategory }>(items: T[]) {
  return SKILL_CATEGORY_ORDER.map((category) => ({
    category,
    label: SKILL_CATEGORY_LABELS[category],
    items: items.filter((i) => i.category === category),
  })).filter((g) => g.items.length > 0);
}

export function providerLabel(provider: string | null, model: string | null): string {
  if (provider === "anthropic") return `AI analysis (${model ?? "Claude"})`;
  if (provider === "local") return "Basic analysis (rule-based, no AI)";
  return provider ?? "Unknown";
}

/** "2024-06" -> "Jun 2024"; "2024" -> "2024". */
export function formatPartialDate(value: string | null): string | null {
  if (!value) return null;
  const [y, m] = value.split("-");
  if (!m) return y;
  const d = new Date(Date.UTC(Number(y), Number(m) - 1, 1));
  return d.toLocaleDateString("en", { month: "short", year: "numeric", timeZone: "UTC" });
}

export function dateRange(start: string | null, end: string | null, current: boolean): string | null {
  const s = formatPartialDate(start);
  const e = current ? "Present" : formatPartialDate(end);
  if (!s && !e) return null;
  return [s, e].filter(Boolean).join(" – ");
}
