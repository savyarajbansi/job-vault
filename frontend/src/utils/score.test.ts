import { describe, expect, it } from "vitest";

import { formatScore } from "./score";

describe("match score formatting", () => {
  it("clamps scores to a display percentage", () => {
    expect(formatScore(0.7)).toBe("70%");
    expect(formatScore(-1)).toBe("0%");
    expect(formatScore(2)).toBe("100%");
  });
});
