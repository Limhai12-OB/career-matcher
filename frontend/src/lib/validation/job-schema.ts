/** Admin job form validation (mirrors backend JobRules; the backend stays authoritative). */
import { z } from "zod";

import {
  EDUCATION_LEVELS,
  EMPLOYMENT_TYPES,
  JOB_REMOTE_TYPES,
  JOB_SENIORITIES,
  type JobDetail,
  type JobPayload,
} from "@/lib/api/types";

const RESERVED = /^(?:[a-z0-9-]+\.)*example\.(?:com|org|net)$/;

export function urlProblem(value: string, origin: "REAL" | "DEMO" | "", checkOrigin: boolean): string | null {
  if (!value) return null;
  let url: URL;
  try {
    url = new URL(value);
  } catch {
    return "Must be a valid https:// URL";
  }
  if (url.protocol !== "https:" || url.username || url.password) return "Must be a valid https:// URL";
  if (!checkOrigin) return null;
  const reserved = RESERVED.test(url.hostname.toLowerCase());
  if (origin === "DEMO" && !reserved) return "Demo listings must link to example.com/.org/.net";
  if (origin === "REAL" && reserved) return "Real listings must link to the actual employer or job board page";
  return null;
}

const text = (max: number) => z.string().trim().max(max, `At most ${max} characters`);
const intString = z.string().trim().refine((v) => v === "" || /^\d+$/.test(v), "Enter a whole number");

export const jobFormSchema = z
  .object({
    title: text(150).min(1, "Title is required"),
    companyName: text(120).min(1, "Company is required"),
    companyWebsite: text(500),
    description: z.string().trim().min(50, "At least 50 characters").max(20000),
    city: text(100),
    countryCode: z.string().regex(/^([A-Z]{2})?$/),
    remoteType: z.enum(JOB_REMOTE_TYPES),
    employmentType: z.enum(EMPLOYMENT_TYPES),
    seniority: z.enum(JOB_SENIORITIES),
    minExperienceMonths: intString,
    maxExperienceMonths: intString,
    educationLevel: z.union([z.enum(EDUCATION_LEVELS), z.literal("")]),
    educationFields: z.array(z.string()).max(10),
    salaryMin: intString,
    salaryMax: intString,
    salaryCurrency: z.string().regex(/^([A-Z]{3})?$/, "Select a currency"),
    salaryPeriod: z.union([z.enum(["MONTH", "YEAR"]), z.literal("")]),
    applicationUrl: text(500).min(1, "Application URL is required"),
    sourceName: text(120).min(1, "Source is required"),
    sourceUrl: text(500),
    dataOrigin: z.union([z.enum(["REAL", "DEMO"]), z.literal("")]),
    postedOn: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, "Choose a date"),
    expiresOn: z.string(),
    skills: z
      .array(z.object({ skillId: z.string(), requirement: z.enum(["REQUIRED", "PREFERRED"]) }))
      .min(1, "Add at least one skill")
      .max(30, "At most 30 skills")
      .refine((s) => s.some((x) => x.requirement === "REQUIRED"), "Mark at least one skill as required"),
  })
  .superRefine((v, ctx) => {
    const add = (path: string, message: string) => ctx.addIssue({ code: "custom", path: [path], message });
    if (v.dataOrigin === "") add("dataOrigin", "Choose whether this is a real or demo listing");
    const app = urlProblem(v.applicationUrl, v.dataOrigin, true);
    if (app) add("applicationUrl", app);
    const src = urlProblem(v.sourceUrl, v.dataOrigin, true);
    if (src) add("sourceUrl", src);
    const site = urlProblem(v.companyWebsite, v.dataOrigin, false);
    if (site) add("companyWebsite", site);
    if (v.minExperienceMonths !== "" && v.maxExperienceMonths !== "" && +v.maxExperienceMonths < +v.minExperienceMonths)
      add("maxExperienceMonths", "Must be at least the minimum");
    const anySalary = v.salaryMin !== "" || v.salaryMax !== "";
    if (anySalary && (!v.salaryCurrency || !v.salaryPeriod)) add("salaryCurrency", "Salary needs a currency and a period");
    if (v.salaryMin !== "" && v.salaryMax !== "" && +v.salaryMax < +v.salaryMin) add("salaryMax", "Must be at least the minimum");
    if (v.expiresOn && v.expiresOn < v.postedOn) add("expiresOn", "Must be on or after the posting date");
  });

export type JobFormValues = z.infer<typeof jobFormSchema>;

const today = () => new Date().toISOString().slice(0, 10);

export function emptyJobForm(): JobFormValues {
  return {
    title: "", companyName: "", companyWebsite: "", description: "", city: "", countryCode: "",
    remoteType: "HYBRID", employmentType: "FULL_TIME", seniority: "JUNIOR", minExperienceMonths: "0",
    maxExperienceMonths: "", educationLevel: "", educationFields: [], salaryMin: "", salaryMax: "",
    salaryCurrency: "", salaryPeriod: "", applicationUrl: "", sourceName: "", sourceUrl: "",
    dataOrigin: "", postedOn: today(), expiresOn: "", skills: [],
  };
}

export function jobToForm(j: JobDetail): JobFormValues {
  const s = (v: number | null) => (v == null ? "" : String(v));
  return {
    title: j.title, companyName: j.companyName, companyWebsite: j.companyWebsite ?? "", description: j.description,
    city: j.city ?? "", countryCode: j.countryCode ?? "", remoteType: j.remoteType, employmentType: j.employmentType,
    seniority: j.seniority, minExperienceMonths: String(j.minExperienceMonths), maxExperienceMonths: s(j.maxExperienceMonths),
    educationLevel: j.educationLevel ?? "", educationFields: j.educationFields, salaryMin: s(j.salaryMin),
    salaryMax: s(j.salaryMax), salaryCurrency: j.salaryCurrency ?? "", salaryPeriod: j.salaryPeriod ?? "",
    applicationUrl: j.applicationUrl, sourceName: j.sourceName, sourceUrl: j.sourceUrl ?? "", dataOrigin: j.dataOrigin,
    postedOn: j.postedOn, expiresOn: j.expiresOn ?? "",
    skills: j.skills.map((x) => ({ skillId: x.skillId, requirement: x.requirement })),
  };
}

export function formToPayload(v: JobFormValues): JobPayload {
  const n = (x: string) => (x.trim() === "" ? null : Number(x));
  const o = (x: string) => (x.trim() === "" ? null : x.trim());
  return {
    title: v.title.trim(), companyName: v.companyName.trim(), companyWebsite: o(v.companyWebsite),
    description: v.description.trim(), city: o(v.city), countryCode: o(v.countryCode), remoteType: v.remoteType,
    employmentType: v.employmentType, seniority: v.seniority, minExperienceMonths: n(v.minExperienceMonths) ?? 0,
    maxExperienceMonths: n(v.maxExperienceMonths), educationLevel: v.educationLevel === "" ? null : v.educationLevel,
    educationFields: v.educationFields, salaryMin: n(v.salaryMin), salaryMax: n(v.salaryMax),
    salaryCurrency: o(v.salaryCurrency), salaryPeriod: v.salaryPeriod === "" ? null : v.salaryPeriod,
    applicationUrl: v.applicationUrl.trim(), sourceName: v.sourceName.trim(), sourceUrl: o(v.sourceUrl),
    dataOrigin: v.dataOrigin as "REAL" | "DEMO", postedOn: v.postedOn, expiresOn: o(v.expiresOn), skills: v.skills,
  };
}
