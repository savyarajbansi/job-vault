import { describe, expect, it, vi } from "vitest";

import { buildApiUrl, createApiClient, resolveApiBaseUrl } from "./client";

describe("api client base url", () => {
  it("normalizes the base url from env", () => {
    expect(
      resolveApiBaseUrl({ VITE_API_BASE_URL: " https://api.example.com/ " })
    ).toBe("https://api.example.com");
    expect(resolveApiBaseUrl({})).toBe("");
  });

  it("builds urls with or without a base", () => {
    expect(buildApiUrl("/api/me", "https://api.example.com")).toBe(
      "https://api.example.com/api/me"
    );
    expect(buildApiUrl("api/me", "https://api.example.com")).toBe(
      "https://api.example.com/api/me"
    );
    expect(buildApiUrl("/api/me", "")).toBe("/api/me");
  });

  it("routes requests through the configured base url", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response("{}", {
        status: 200,
        headers: { "Content-Type": "application/json" }
      })
    );

    const client = createApiClient({
      baseUrl: "https://api.example.com",
      fetchImpl: fetchMock
    });

    await client.sendRequest("/api/me", { method: "GET" });

    expect(fetchMock).toHaveBeenCalledWith("https://api.example.com/api/me", {
      method: "GET"
    });
  });
});
