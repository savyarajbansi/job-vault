function clamp01(value: number): number {
  if (value < 0) return 0;
  if (value > 1) return 1;
  return value;
}

/** Converts a 0.0-1.0 score to a display percentage string, e.g. "73%" */
export function formatScore(score: number): string {
  return `${Math.round(clamp01(score) * 100)}%`;
}
