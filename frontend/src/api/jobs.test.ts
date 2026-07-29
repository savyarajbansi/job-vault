import { beforeEach, describe, expect, it, vi } from "vitest";

import { request } from "./client";
import { getPublicJob, getPublicJobs, getTrendingSkills } from "./jobs";

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
      sectorTags: ["SOFTWARE"],
      location: "Austin, TX",
      workMode: "REMOTE",
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

  it("loads the public job list via request", async () => {
    vi.mocked(request).mockResolvedValue([
      {
        id: "5d1d6b0a-2222-4f3a-9c3a-111111111111",
        title: "Backend Engineer",
        companyName: "Acme Corp",
        sectorTags: ["SOFTWARE"],
        location: "Austin, TX",
        workMode: "REMOTE",
        minExperienceYears: 2,
        salaryMin: 80000,
        salaryMax: 120000,
        status: "ACTIVE",
        createdAt: "2026-04-01T00:00:00Z"
      }
    ]);

    const result = await getPublicJobs();

    expect(request).toHaveBeenCalledWith("/api/jobs", { method: "GET" });
    expect(result).toHaveLength(1);
    expect(result[0].title).toBe("Backend Engineer");
  });

  it("loads trending skills via request", async () => {
    vi.mocked(request).mockResolvedValue([
      { skillId: "a1111111-1111-1111-1111-111111111111", skillName: "java", score: 10.5 },
      { skillId: "b2222222-2222-2222-2222-222222222222", skillName: "spring", score: 7 }
    ]);

    const result = await getTrendingSkills();

    expect(request).toHaveBeenCalledWith("/api/jobs/trending-skills", { method: "GET" });
    expect(result).toHaveLength(2);
    expect(result[0].skillName).toBe("java");
  });
});
