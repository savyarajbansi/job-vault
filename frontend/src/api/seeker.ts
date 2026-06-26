import { authorizedRequest } from "./auth";
import { ApplicationStatus, EducationRequirement } from "./employer";

export type ResumeUploadResult = {
  resumeId: string;
  status: string;
};

export type ResumeHistoryItem = {
  resumeId: string;
  originalFilename: string;
  status: string;
  failureCode: string | null;
  createdAt: string;
  parsedAt: string | null;
  inferredSkills: string[];
};

export type MatchPage = {
  limit: number;
  offset: number;
  total: number;
};

export type ResumeHistoryResponse = {
  items: ResumeHistoryItem[];
  page: MatchPage;
};

export type MatchFactors = {
  cosine: number;
  skillsOverlap: number;
  experience: number;
  location: number;
};

// Mirrors SeekerJobMatchResponse.JobInfo on the backend. Keep in sync with
// MatchingFacade#toJobInfo and SeekerJobMatchResponse.java.
export type JobInfo = {
  title: string;
  companyName: string | null;
  location: string | null;
  remoteEligible: boolean | null;
  salaryMin: number | null;
  salaryMax: number | null;
  educationRequirement: EducationRequirement | null;
  requiredSkills: string[];
};

export type SeekerMatchItem = {
  jobId: string;
  score: number;
  factors: MatchFactors;
  job: JobInfo;
  missingSkills: string[];
};

export type SeekerJobMatchResponse = {
  items: SeekerMatchItem[];
  page: MatchPage;
};

export type SkillGapResponse = {
  jobId: string;
  missingSkills: string[];
};

export type SeekerResumeHistoryQuery = {
  limit: number;
  offset: number;
};

export type SeekerMatchesQuery = {
  limit: number;
  offset: number;
};

export type SeekerProfile = {
  userId: string;
  preferredSector: string | null;
  preferredLocation: string | null;
  remoteOk: boolean | null;
  yearsExperience: number | null;
};

export type SeekerProfileUpdate = {
  preferredSector?: string | null;
  preferredLocation?: string | null;
  remoteOk?: boolean | null;
  yearsExperience?: number | null;
};

function queryString(params: Record<string, string | number | undefined>): string {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined) continue;
    query.set(key, String(value));
  }
  const serialized = query.toString();
  return serialized ? `?${serialized}` : "";
}

export async function uploadSeekerResume(file: File): Promise<ResumeUploadResult> {
  const formData = new FormData();
  formData.set("file", file);
  return authorizedRequest<ResumeUploadResult>("/api/seeker/resumes/upload", {
    method: "POST",
    body: formData,
  });
}

export async function getSeekerResumeHistory(
  params: SeekerResumeHistoryQuery
): Promise<ResumeHistoryResponse> {
  return authorizedRequest<ResumeHistoryResponse>(
    `/api/seeker/resumes${queryString(params)}`,
    { method: "GET" }
  );
}

export async function getSeekerMatches(
  params: SeekerMatchesQuery
): Promise<SeekerJobMatchResponse> {
  return authorizedRequest<SeekerJobMatchResponse>(
    `/api/seeker/matches/jobs${queryString(params)}`,
    { method: "GET" }
  );
}

export async function getSeekerSkillGaps(jobId: string): Promise<SkillGapResponse> {
  return authorizedRequest<SkillGapResponse>(
    `/api/seeker/jobs/${jobId}/skill-gaps`,
    { method: "GET" }
  );
}

export const getSeekerSkillGap = getSeekerSkillGaps;

export async function getSeekerProfile(): Promise<SeekerProfile> {
  return authorizedRequest<SeekerProfile>("/api/seeker/profile", { method: "GET" });
}

export async function updateSeekerProfile(
  data: SeekerProfileUpdate
): Promise<SeekerProfile> {
  return authorizedRequest<SeekerProfile>("/api/seeker/profile", {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(data),
  });
}

// Applications

// Mirrors JobApplicationResponse.java - returned by draft/apply/withdraw.
export type ApplicationActionResult = {
  id: string;
  jobId: string;
  seekerId: string;
  seekerName: string | null;
  status: ApplicationStatus;
  submittedAt: string | null;
  reviewedAt: string | null;
  decidedAt: string | null;
};

// Mirrors SeekerApplicationResponse.java - returned by the list endpoint.
export type SeekerApplicationItem = {
  id: string;
  jobId: string | null;
  jobTitle: string | null;
  companyName: string | null;
  status: ApplicationStatus;
  submittedAt: string | null;
  reviewedAt: string | null;
  decidedAt: string | null;
};

export async function draftApplication(jobId: string): Promise<ApplicationActionResult> {
  return authorizedRequest<ApplicationActionResult>(`/api/seeker/jobs/${jobId}/draft`, {
    method: "POST",
  });
}

export async function applyToJob(jobId: string): Promise<ApplicationActionResult> {
  return authorizedRequest<ApplicationActionResult>(`/api/seeker/jobs/${jobId}/apply`, {
    method: "POST",
  });
}

export async function getMyApplications(): Promise<SeekerApplicationItem[]> {
  return authorizedRequest<SeekerApplicationItem[]>("/api/seeker/applications", {
    method: "GET",
  });
}

export async function withdrawApplication(
  applicationId: string
): Promise<ApplicationActionResult> {
  return authorizedRequest<ApplicationActionResult>(
    `/api/seeker/applications/${applicationId}/withdraw`,
    { method: "PATCH" }
  );
}
