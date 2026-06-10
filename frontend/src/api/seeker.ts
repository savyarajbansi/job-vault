import { authorizedRequest } from "./auth";

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

export type MatchPage = {
  limit: number;
  offset: number;
  total: number;
};

export type SeekerMatchItem = {
  jobId: string;
  score: number;
  factors: MatchFactors;
  job: {
    title: string;
    remoteEligible: boolean;
  };
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

export async function uploadSeekerResume(file: File): Promise<ResumeUploadResult> {
  const formData = new FormData();
  formData.set("file", file);

  return authorizedRequest<ResumeUploadResult>("/api/seeker/resumes/upload", {
    method: "POST",
    body: formData
  });
}

export async function getSeekerResumeHistory(
  params: SeekerResumeHistoryQuery
): Promise<ResumeHistoryResponse> {
  const query = new URLSearchParams({
    limit: String(params.limit),
    offset: String(params.offset)
  });
  return authorizedRequest<ResumeHistoryResponse>(
    `/api/seeker/resumes?${query.toString()}`,
    { method: "GET" }
  );
}

export async function getSeekerMatches(
  params: SeekerMatchesQuery
): Promise<SeekerJobMatchResponse> {
  const query = new URLSearchParams({
    limit: String(params.limit),
    offset: String(params.offset)
  });
  return authorizedRequest<SeekerJobMatchResponse>(
    `/api/seeker/matches/jobs?${query.toString()}`,
    { method: "GET" }
  );
}

export async function getSeekerSkillGaps(jobId: string): Promise<SkillGapResponse> {
  return authorizedRequest<SkillGapResponse>(`/api/seeker/jobs/${jobId}/skill-gaps`, {
    method: "GET"
  });
}

export async function getSeekerProfile(): Promise<SeekerProfile> {
  return authorizedRequest<SeekerProfile>("/api/seeker/profile", { method: "GET" });
}

export async function updateSeekerProfile(data: SeekerProfileUpdate): Promise<SeekerProfile> {
  return authorizedRequest<SeekerProfile>("/api/seeker/profile", {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(data)
  });
}
