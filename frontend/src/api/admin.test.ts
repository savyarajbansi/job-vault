import { beforeEach, describe, expect, it, vi } from "vitest";

import { authorizedRequest } from "./auth";
import { getAdminMetrics } from "./admin";

vi.mock("./auth", () => ({
  authorizedRequest: vi.fn()
}));

describe("admin api client", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("loads admin metrics via authorizedRequest", async () => {
    vi.mocked(authorizedRequest).mockResolvedValue({
      parse: {
        totalAttempts: 0,
        successCount: 0,
        failureCount: 0,
        lastAttemptAt: null,
        failuresByCode: {}
      },
      match: {
        totalAttempts: 0,
        successCount: 0,
        failureCount: 0,
        lastAttemptAt: null,
        failuresByCode: {}
      }
    });

    const result = await getAdminMetrics();

    expect(authorizedRequest).toHaveBeenCalledWith("/api/admin/metrics", { method: "GET" });
    expect(result.parse.totalAttempts).toBe(0);
  });
});
