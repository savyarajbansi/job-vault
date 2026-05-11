import { authorizedRequest } from "./auth";

export type ResumeUploadResult = {
  resumeId: string;
  status: string;
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

export type SeekerMatchesQuery = {
  limit: number;
  offset: number;
};

export async function uploadSeekerResume(file: File): Promise<ResumeUploadResult> {
  const formData = new FormData();
  formData.set("file", file);

  return authorizedRequest<ResumeUploadResult>("/api/seeker/resumes/upload", {
    method: "POST",
    body: formData
  });
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
