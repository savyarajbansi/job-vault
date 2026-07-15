import { describe, expect, it } from "vitest";

import { isStrongMatch, STRONG_MATCH_THRESHOLD } from "./score";

describe("match score labels", () => {
  it("requires a score of at least 70 percent for a strong match", () => {
    expect(STRONG_MATCH_THRESHOLD).toBe(0.7);
    expect(isStrongMatch(0.7)).toBe(true);
    expect(isStrongMatch(0.699)).toBe(false);
  });
});
