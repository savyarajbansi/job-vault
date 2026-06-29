import { request } from "./client";
import type { JobDetail, JobSummary } from "./employer";

export type { JobDetail, JobSummary } from "./employer";

export type TrendingSkill = {
  skillId: string;
  skillName: string;
  score: number;
};

export async function getPublicJobs(): Promise<JobSummary[]> {
  return request<JobSummary[]>("/api/jobs", { method: "GET" });
}

export async function getPublicJob(jobId: string): Promise<JobDetail> {
  return request<JobDetail>(`/api/jobs/${jobId}`, { method: "GET" });
}

export async function getTrendingSkills(): Promise<TrendingSkill[]> {
  return request<TrendingSkill[]>("/api/jobs/trending-skills", { method: "GET" });
}