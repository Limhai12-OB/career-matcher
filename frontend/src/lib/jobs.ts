/** Job search filters, URL (de)serialization and display helpers (pure, unit-tested). */
import type {
  DataOrigin,
  EducationLevel,
  EmploymentType,
  JobRemoteType,
  JobStatus,
  SalaryPeriod,
  Seniority,
} from "@/lib/api/types";
import { EMPLOYMENT_TYPES, JOB_REMOTE_TYPES, JOB_SENIORITIES } from "@/lib/api/types";
import { countryName } from "@/lib/countries";

export interface JobFilters {
  q: string;
  country: string;
  remote: JobRemoteType | "";
  type: EmploymentType | "";
  seniority: Seniority | "";
  skill: string;
  origin: DataOrigin | "";
  page: number;
}

export const EMPTY_FILTERS: JobFilters = {
  q: "",
  country: "",
  remote: "",
  type: "",
  seniority: "",
  skill: "",
  origin: "",
  page: 0,
};

const pick = <T extends string>(value: string | null, allowed: readonly T[]): T | "" =>
  value && (allowed as readonly string[]).includes(value) ? (value as T) : "";

/** Parses untrusted URL parameters into valid filters (invalid values are dropped). */
export function parseFilters(params: URLSearchParams): JobFilters {
  const page = Number(params.get("page"));
  const country = params.get("country") ?? "";
  const skill = params.get("skill") ?? "";
  return {
    q: (params.get("q") ?? "").slice(0, 100),
    country: /^[A-Z]{2}$/.test(country) ? country : "",
    remote: pick(params.get("remote"), JOB_REMOTE_TYPES),
    type: pick(params.get("type"), EMPLOYMENT_TYPES),
    seniority: pick(params.get("seniority"), JOB_SENIORITIES),
    skill: /^[a-z0-9-]{1,80}$/.test(skill) ? skill : "",
    origin: pick(params.get("origin"), ["REAL", "DEMO"] as const),
    page: Number.isInteger(page) && page > 0 && page < 1000 ? page : 0,
  };
}

/** Serializes filters, omitting empty values (used for both the page URL and the API call). */
export function toSearchParams(f: JobFilters): URLSearchParams {
  const p = new URLSearchParams();
  if (f.q.trim()) p.set("q", f.q.trim());
  if (f.country) p.set("country", f.country);
  if (f.remote) p.set("remote", f.remote);
  if (f.type) p.set("type", f.type);
  if (f.seniority) p.set("seniority", f.seniority);
  if (f.skill) p.set("skill", f.skill);
  if (f.origin) p.set("origin", f.origin);
  if (f.page > 0) p.set("page", String(f.page));
  return p;
}

export const REMOTE_TYPE_LABELS: Record<JobRemoteType, string> = {
  ONSITE: "On-site",
  HYBRID: "Hybrid",
  REMOTE: "Remote",
};

export const SENIORITY_LABELS: Record<Seniority, string> = {
  INTERNSHIP: "Internship",
  ENTRY: "Entry level",
  JUNIOR: "Junior",
};

export const EDUCATION_LABELS: Record<EducationLevel, string> = {
  HIGH_SCHOOL: "High school",
  VOCATIONAL: "Vocational / diploma",
  ASSOCIATE: "Associate degree",
  BACHELOR: "Bachelor's degree",
  MASTER: "Master's degree",
};

export const STATUS_LABELS: Record<JobStatus, string> = {
  DRAFT: "Draft",
  PUBLISHED: "Published",
  CLOSED: "Closed",
};

export function formatLocation(job: {
  city: string | null;
  countryCode: string | null;
  remoteType: JobRemoteType;
}): string {
  const place = [job.city, countryName(job.countryCode)].filter(Boolean).join(", ");
  if (job.remoteType === "REMOTE") return place ? `Remote (${place})` : "Remote";
  return place || "Location not specified";
}

export function formatSalary(job: {
  salaryMin: number | null;
  salaryMax: number | null;
  salaryCurrency: string | null;
  salaryPeriod: SalaryPeriod | null;
}): string | null {
  const { salaryMin: min, salaryMax: max, salaryCurrency: cur, salaryPeriod: per } = job;
  if (!cur || (min == null && max == null)) return null;
  const fmt = (n: number) =>
    new Intl.NumberFormat("en", { style: "currency", currency: cur, maximumFractionDigits: 0 }).format(n);
  const amount =
    min != null && max != null && min !== max
      ? `${fmt(min)} – ${fmt(max)}`
      : min != null && max == null
        ? `From ${fmt(min)}`
        : max != null && min == null
          ? `Up to ${fmt(max)}`
          : fmt((min ?? max)!);
  return `${amount} / ${per === "YEAR" ? "year" : "month"}`;
}

export function formatExperience(min: number, max: number | null): string {
  const years = (m: number) => (m % 12 === 0 ? `${m / 12} year${m === 12 ? "" : "s"}` : `${m} months`);
  if (min === 0 && (max == null || max === 0)) return "No experience required";
  if (min === 0) return `Up to ${years(max!)}`;
  return max != null ? `${years(min)} – ${years(max)}` : `${years(min)}+`;
}

export function postedAgo(isoDate: string, now = new Date()): string {
  const days = Math.floor((now.getTime() - new Date(`${isoDate}T00:00:00Z`).getTime()) / 86_400_000);
  if (days <= 0) return "Posted today";
  if (days === 1) return "Posted yesterday";
  if (days < 30) return `Posted ${days} days ago`;
  return `Posted on ${new Date(isoDate).toLocaleDateString("en", { dateStyle: "medium" })}`;
}

/** Only http(s) links are ever rendered as clickable application links. */
export function isSafeExternalUrl(url: string | null | undefined): url is string {
  if (!url) return false;
  try {
    return new URL(url).protocol === "https:";
  } catch {
    return false;
  }
}
