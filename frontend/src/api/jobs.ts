import { request } from "./client";
import type { JobDetail } from "./employer";

export type { JobDetail } from "./employer";

export async function getPublicJob(jobId: string): Promise<JobDetail> {
  return request<JobDetail>(`/api/jobs/${jobId}`, { method: "GET" });
}
