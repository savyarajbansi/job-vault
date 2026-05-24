import { authorizedRequest } from "./auth";

export type AdminMetrics = {
  parse: {
    totalAttempts: number;
    successCount: number;
    failureCount: number;
    lastAttemptAt: string | null;
    failuresByCode: Record<string, number>;
  };
  match: {
    totalAttempts: number;
    successCount: number;
    failureCount: number;
    lastAttemptAt: string | null;
    failuresByCode: Record<string, number>;
  };
};

export async function getAdminMetrics(): Promise<AdminMetrics> {
  return authorizedRequest<AdminMetrics>("/api/admin/metrics", { method: "GET" });
}
