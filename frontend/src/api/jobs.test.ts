import { beforeEach, describe, expect, it, vi } from "vitest";

import { request } from "./client";
import { getPublicJob } from "./jobs";

vi.mock("./client", () => ({
  request: vi.fn()
}));

describe("public jobs api client", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("loads a public job via request", async () => {
    vi.mocked(request).mockResolvedValue({
      id: "5d1d6b0a-2222-4f3a-9c3a-111111111111",
      title: "Backend Engineer",
      description: "Build APIs",
      companyName: "Acme Corp",
      location: "Austin, TX",
      remoteEligible: true,
      minExperienceYears: 2,
      salaryMin: 80000,
      salaryMax: 120000,
      educationRequirement: null,
      requiredSkills: ["java", "spring"],
      status: "ACTIVE",
      createdAt: "2026-04-01T00:00:00Z",
      updatedAt: "2026-04-01T00:00:00Z",
      publishedAt: "2026-04-01T00:00:00Z",
      disabledAt: null
    });

    const result = await getPublicJob("5d1d6b0a-2222-4f3a-9c3a-111111111111");

    expect(request).toHaveBeenCalledWith(
      "/api/jobs/5d1d6b0a-2222-4f3a-9c3a-111111111111",
      { method: "GET" }
    );
    expect(result.title).toBe("Backend Engineer");
    expect(result.requiredSkills).toEqual(["java", "spring"]);
  });
});