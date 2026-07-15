/** Minimum raw 0.0-1.0 score required for a "Strong match" label. */
export const STRONG_MATCH_THRESHOLD = 0.7;

function clamp01(value: number): number {
  if (value < 0) return 0;
  if (value > 1) return 1;
  return value;
}

export function isStrongMatch(score: number): boolean {
  return score >= STRONG_MATCH_THRESHOLD;
}

/** Converts a 0.0-1.0 score to a display percentage string, e.g. "73%" */
export function formatScore(score: number): string {
  return `${Math.round(clamp01(score) * 100)}%`;
}

/** Converts a 0.0-1.0 score to the 0-100 integer expected by notify endpoints */
export function scoreToBackendScale(score: number): number {
  return Math.max(0, Math.min(100, Math.round(score * 100)));
}
