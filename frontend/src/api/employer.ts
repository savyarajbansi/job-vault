import { authorizedRequest } from "./auth";
import type { NepalCity, SectorCode, WorkMode } from "./matching";

export type JobStatus = "DRAFT" | "ACTIVE" | "DISABLED";

export type EducationRequirement =
  | "HIGH_SCHOOL"
  | "BACHELORS"
  | "MASTERS"
  | "PHD";

export const EDUCATION_LABELS: Record<EducationRequirement, string> = {
  HIGH_SCHOOL: "High school",
  BACHELORS: "Bachelor's degree",
  MASTERS: "Master's degree",
  PHD: "PhD",
};

export type JobSummary = {
  id: string;
  title: string;
  companyName: string | null;
  sectorTags: SectorCode[];
  location: NepalCity | string | null;
  workMode: WorkMode | null;
  minExperienceYears: number | null;
  salaryMin: number | null;
  salaryMax: number | null;
  status: JobStatus;
  createdAt: string;
};

export type JobDetail = {
  id: string;
  title: string;
  description: string;
  companyName: string | null;
  sectorTags: SectorCode[];
  location: NepalCity | string | null;
  workMode: WorkMode | null;
  minExperienceYears: number | null;
  salaryMin: number | null;
  salaryMax: number | null;
  educationRequirement: EducationRequirement | null;
  requiredSkills: string[];
  status: JobStatus;
  createdAt: string;
  updatedAt: string;
  publishedAt: string | null;
  disabledAt: string | null;
};

export type JobCreateRequest = {
  title: string;
  description: string;
  companyName?: string | null;
  sectorTags?: SectorCode[] | null;
  location?: NepalCity | null;
  workMode?: WorkMode | null;
  minExperienceYears?: number | null;
  salaryMin?: number | null;
  salaryMax?: number | null;
  educationRequirement?: EducationRequirement | null;
};

export type JobUpdateRequest = JobCreateRequest;

export type ApplicationStatus =
  | "DRAFT"
  | "SUBMITTED"
  | "UNDER_REVIEW"
  | "REJECTED"
  | "ACCEPTED"
  | "WITHDRAWN";

export type JobApplication = {
  id: string;
  jobId: string;
  seekerId: string;
  seekerName: string | null;
  status: ApplicationStatus;
  submittedAt: string | null;
  reviewedAt: string | null;
  decidedAt: string | null;
};

export type ApplicationStatusUpdateRequest = {
  status: "UNDER_REVIEW" | "REJECTED" | "ACCEPTED";
};

// ── Salary formatting helper ───────────────────────────────────────────────
export function formatSalaryRange(
  salaryMin: number | null | undefined,
  salaryMax: number | null | undefined
): string | null {
  const fmt = (n: number) =>
    "$" + n.toLocaleString("en-US", { maximumFractionDigits: 0 });
  if (salaryMin != null && salaryMax != null) {
    return `${fmt(salaryMin)} – ${fmt(salaryMax)}`;
  }
  if (salaryMin != null) {
    return `From ${fmt(salaryMin)}`;
  }
  if (salaryMax != null) {
    return `Up to ${fmt(salaryMax)}`;
  }
  return null;
}

// ── API functions ──────────────────────────────────────────────────────────
export async function getEmployerJobs(): Promise<JobSummary[]> {
  return authorizedRequest<JobSummary[]>("/api/employer/jobs", { method: "GET" });
}

export async function getEmployerJob(jobId: string): Promise<JobDetail> {
  return authorizedRequest<JobDetail>(`/api/employer/jobs/${jobId}`, { method: "GET" });
}

export async function createEmployerJob(data: JobCreateRequest): Promise<JobDetail> {
  return authorizedRequest<JobDetail>("/api/employer/jobs", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(data),
  });
}

export async function updateEmployerJob(
  jobId: string,
  data: JobUpdateRequest
): Promise<JobDetail> {
  return authorizedRequest<JobDetail>(`/api/employer/jobs/${jobId}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(data),
  });
}

export async function publishEmployerJob(jobId: string): Promise<JobDetail> {
  return authorizedRequest<JobDetail>(`/api/employer/jobs/${jobId}/publish`, {
    method: "POST",
  });
}

export async function disableEmployerJob(jobId: string): Promise<JobDetail> {
  return authorizedRequest<JobDetail>(`/api/employer/jobs/${jobId}/disable`, {
    method: "POST",
  });
}

export async function reactivateEmployerJob(jobId: string): Promise<JobDetail> {
  return authorizedRequest<JobDetail>(`/api/employer/jobs/${jobId}/reactivate`, {
    method: "POST",
  });
}

export async function addEmployerJobSkill(jobId: string, name: string): Promise<JobDetail> {
  return authorizedRequest<JobDetail>(`/api/employer/jobs/${jobId}/skills`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ name }),
  });
}

export async function removeEmployerJobSkill(jobId: string, skillName: string): Promise<JobDetail> {
  return authorizedRequest<JobDetail>(
    `/api/employer/jobs/${jobId}/skills/${encodeURIComponent(skillName)}`,
    { method: "DELETE" }
  );
}

export async function listEmployerJobApplications(
  jobId: string
): Promise<JobApplication[]> {
  return authorizedRequest<JobApplication[]>(
    `/api/employer/jobs/${jobId}/applications`,
    { method: "GET" }
  );
}

export async function updateEmployerApplicationStatus(
  applicationId: string,
  status: ApplicationStatusUpdateRequest["status"]
): Promise<JobApplication> {
  return authorizedRequest<JobApplication>(
    `/api/employer/applications/${applicationId}/status`,
    {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ status }),
    }
  );
}
