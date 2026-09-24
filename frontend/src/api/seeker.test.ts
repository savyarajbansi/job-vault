import { beforeEach, describe, expect, it, vi } from "vitest";

import { authorizedRequest } from "./auth";
import {
  getSeekerMatches,
  getSeekerSkillGaps,
  updateSeekerProfile,
  uploadSeekerResume
} from "./seeker";
import { NEPAL_CITY_OPTIONS } from "./matching";

vi.mock("./auth", () => ({
  authorizedRequest: vi.fn()
}));

describe("seeker api client", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("uploads a seeker resume through authorizedRequest", async () => {
    const file = new File(["resume"], "resume.pdf", { type: "application/pdf" });
    vi.mocked(authorizedRequest).mockResolvedValue({
      resumeId: "2f43f4c4-03a9-4f42-a366-442f4fa7bd9f",
      status: "PARSED"
    });

    const result = await uploadSeekerResume(file);

    expect(authorizedRequest).toHaveBeenCalledTimes(1);
    const [path, options] = vi.mocked(authorizedRequest).mock.calls[0] as [
      string,
      RequestInit
    ];
    expect(path).toBe("/api/seeker/resumes/upload");
    expect(options.method).toBe("POST");
    expect(options.body).toBeInstanceOf(FormData);
    expect(result.status).toBe("PARSED");
  });

  it("loads seeker matches with limit and offset query params", async () => {
    vi.mocked(authorizedRequest).mockResolvedValue({
      items: [{
        strongMatch: true,
        factors: {
          bm25: 0.9,
          embedding: 0,
          experience: 1,
          location: 1,
          salary: 0,
          bm25Available: true,
          embeddingAvailable: false,
          experienceAvailable: true,
          locationAvailable: true,
          salaryAvailable: false,
          lexical: {
            requiredSkills: 1,
            title: 0.8,
            description: 0.7,
            requiredSkillsAvailable: true,
            titleAvailable: true,
            descriptionAvailable: true
          }
        }
      }],
      page: { limit: 5, offset: 10, total: 1 }
    });

    const result = await getSeekerMatches({ limit: 5, offset: 10 });

    expect(authorizedRequest).toHaveBeenCalledWith(
      "/api/seeker/matches/jobs?limit=5&offset=10",
      { method: "GET" }
    );
    expect(result.page.limit).toBe(5);
    expect(result.items[0].strongMatch).toBe(true);
    expect(result.items[0].factors.lexical.requiredSkills).toBe(1);
  });

  it("loads skill gaps for a selected job", async () => {
    vi.mocked(authorizedRequest).mockResolvedValue({
      jobId: "4f7026fc-6f3d-42c3-b09c-b7b491aabdfd",
      missingSkills: ["kubernetes"]
    });

    const result = await getSeekerSkillGaps("4f7026fc-6f3d-42c3-b09c-b7b491aabdfd");

    expect(authorizedRequest).toHaveBeenCalledWith(
      "/api/seeker/jobs/4f7026fc-6f3d-42c3-b09c-b7b491aabdfd/skill-gaps",
      { method: "GET" }
    );
    expect(result.missingSkills).toEqual(["kubernetes"]);
  });

  it("updates a seeker salary range through the profile endpoint", async () => {
    vi.mocked(authorizedRequest).mockResolvedValue({
      userId: "seeker-id",
      preferredLocation: "Kathmandu",
      preferredSalaryMin: 80000,
      preferredSalaryMax: 120000
    });

    await updateSeekerProfile({
      preferredLocation: "Kathmandu",
      preferredSalaryMin: 80000,
      preferredSalaryMax: 120000
    });

    expect(authorizedRequest).toHaveBeenCalledWith(
      "/api/seeker/profile",
      expect.objectContaining({
        method: "PATCH",
        body: JSON.stringify({
          preferredLocation: "Kathmandu",
          preferredSalaryMin: 80000,
          preferredSalaryMax: 120000
        })
      })
    );
  });

  it("keeps the location dropdown limited to the supported Nepal cities", () => {
    expect(NEPAL_CITY_OPTIONS.map((option) => option.value)).toEqual([
      "Kathmandu",
      "Lalitpur",
      "Bhaktapur",
      "Pokhara",
      "Bharatpur",
      "Biratnagar",
      "Birgunj",
      "Butwal",
      "Dharan",
      "Hetauda",
      "Janakpur",
      "Nepalgunj",
      "Dhangadhi",
      "Itahari",
      "Tulsipur"
    ]);
  });
});
