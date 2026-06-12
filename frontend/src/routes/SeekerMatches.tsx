import { useState, useEffect } from "react";
import {
  getSeekerMatches,
  getSeekerSkillGaps,
  SeekerJobMatchResponse,
  SeekerMatchItem,
} from "../api/seeker";
import { Button, Alert, Card, Badge, Spinner, ScoreBar } from "../components/ui";

function scoreColor(score: number): string {
  if (score >= 0.7) return "var(--success)";
  if (score >= 0.4) return "#B07D20";
  return "var(--ink-muted)";
 }

export default function SeekerMatches() {
  const [matches, setMatches] = useState<SeekerJobMatchResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const limit = 10;

  const [selected, setSelected] = useState<SeekerMatchItem | null>(null);
  const [gaps, setGaps] = useState<string[] | null>(null);
  const [gapsLoading, setGapsLoading] = useState(false);
  const [gapsError, setGapsError] = useState<string | null>(null);

  const loadMatches = async (offset: number) => {
    setLoading(true);
    setError(null);
    try {
      const result = await getSeekerMatches({ limit, offset });
      setMatches(result);
      if (result.items.length > 0 && !selected) {
        setSelected(result.items[0]);
      }
    } catch (err) {
      const msg = err instanceof Error ? err.message : "";
      if (msg.includes("ERR_AUTH_003")) {
        setError("Your session has ended. Please sign in again.");
      } else if (msg.includes("404") || msg.toLowerCase().includes("resume")) {
        setError("Upload a parsed resume first to see your matches.");
      } else {
        setError("Could not load matches. Please try again.");
      }
    } finally {
      setLoading(false);
    }
  };

  const loadGaps = async (jobId: string) => {
    setGapsLoading(true);
    setGapsError(null);
    setGaps(null);
    try {
      const result = await getSeekerSkillGaps(jobId);
      setGaps(result.missingSkills);
    } catch {
      setGapsError("Could not load skill gap data.");
    } finally {
      setGapsLoading(false);
    }
  };

  useEffect(() => {
    void loadMatches(page * limit);
  }, [page]);

  useEffect(() => {
    if (selected) void loadGaps(selected.jobId);
  }, [selected]);

  const total = matches?.page.total ?? 0;
  const totalPages = Math.ceil(total / limit);

  return (
    <main style={{ maxWidth: 1100, margin: "0 auto", padding: "2rem 1.5rem" }}>
      <div style={{ marginBottom: "1.75rem" }}>
        <h1 style={{ marginBottom: "0.375rem" }}>Job Matches</h1>
        <p style={{ color: "var(--ink-muted)" }}>
          Ranked by fit based on your resume, skills, experience, and preferences.
        </p>
      </div>

      {error && (
        <Alert tone={error.includes("resume") ? "info" : "error"}>{error}</Alert>
      )}

      {loading && (
        <div style={{ display: "flex", justifyContent: "center", padding: "4rem" }}>
          <Spinner size={32} />
        </div>
      )}

      {!loading && !error && matches && (
        <div style={{ display: "grid", gridTemplateColumns: "1fr 380px", gap: "1.5rem", alignItems: "start" }}>
          {/* ── Match list ── */}
          <div style={{ display: "flex", flexDirection: "column", gap: "0.75rem" }}>
            {matches.items.length === 0 && (
              <p style={{ color: "var(--ink-muted)" }}>No matches found for your current resume.</p>
            )}

            {matches.items.map((item, idx) => {
              const isSelected = selected?.jobId === item.jobId;
              return (
                <button
                  key={item.jobId}
                  onClick={() => setSelected(item)}
                  style={{
                    display: "block",
                    width: "100%",
                    textAlign: "left",
                    background: isSelected ? "var(--bg-card)" : "var(--bg-card)",
                    border: `1.5px solid ${isSelected ? "var(--accent)" : "var(--border)"}`,
                    borderRadius: "var(--radius)",
                    padding: "1rem 1.125rem",
                    cursor: "pointer",
                    boxShadow: isSelected ? "0 0 0 3px rgba(45,110,110,0.1)" : "var(--shadow-sm)",
                    transition: "border-color 0.15s, box-shadow 0.15s",
                    fontFamily: "var(--font-body)",
                    animation: `fadeUp 0.35s cubic-bezier(0.16,1,0.3,1) ${idx * 0.04}s both`,
                  }}
                >
                  <div style={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between", gap: "1rem" }}>
                    <div style={{ minWidth: 0 }}>
                      <div style={{ fontWeight: 600, color: "var(--ink)", fontSize: "0.9375rem", marginBottom: "0.25rem" }}>
                        {item.job.title}
                      </div>
                      <div style={{ display: "flex", flexWrap: "wrap", gap: "0.5rem", alignItems: "center" }}>
                        {item.job.remoteEligible && (
                          <Badge tone="accent">Remote eligible</Badge>
                        )}
                        {item.missingSkills.length === 0 ? (
                          <Badge tone="success">Strong match</Badge>
                        ) : (
                          <Badge tone="neutral">{item.missingSkills.length} skill gaps</Badge>
                        )}
                      </div>
                    </div>
                    <div
                      style={{
                        fontFamily: "var(--font-display)",
                        fontSize: "1.25rem",
                        fontWeight: 700,
                        color: scoreColor(item.score),
                        flexShrink: 0,
                      }}
                    >
                      {Math.round(item.score * 100)}%
                    </div>
                  </div>
                </button>
              );
            })}

            {/* Pagination */}
            {totalPages > 1 && (
              <div style={{ display: "flex", justifyContent: "center", gap: "0.5rem", marginTop: "0.5rem" }}>
                <Button
                  variant="ghost"
                  size="sm"
                  disabled={page === 0}
                  onClick={() => setPage((p) => p - 1)}
                >
                  ← Previous
                </Button>
                <span style={{ display: "flex", alignItems: "center", fontSize: "0.875rem", color: "var(--ink-muted)" }}>
                  {page + 1} / {totalPages}
                </span>
                <Button
                  variant="ghost"
                  size="sm"
                  disabled={page >= totalPages - 1}
                  onClick={() => setPage((p) => p + 1)}
                >
                  Next →
                </Button>
              </div>
            )}

            <p style={{ fontSize: "0.8125rem", color: "var(--ink-faint)", textAlign: "center" }}>
              {total} match{total !== 1 ? "es" : ""} found
            </p>
          </div>

          {/* ── Detail panel ── */}
          <div style={{ position: "sticky", top: 72 }}>
            {selected ? (
              <Card style={{ display: "flex", flexDirection: "column", gap: "1.25rem" }}>
                {/* Header */}
                <div>
                  <div style={{ fontWeight: 700, fontSize: "1.0625rem", color: "var(--ink)", marginBottom: "0.5rem" }}>
                    {selected.job.title}
                  </div>
                  <div
                    style={{
                      display: "inline-flex",
                      alignItems: "center",
                      gap: "0.375rem",
                      padding: "0.375rem 0.75rem",
                      background: "var(--bg-subtle)",
                      borderRadius: "var(--radius-sm)",
                    }}
                  >
                    <span
                      style={{
                        fontFamily: "var(--font-display)",
                        fontSize: "1.5rem",
                        fontWeight: 700,
                        color: scoreColor(selected.score),
                      }}
                    >
                      {Math.round(selected.score * 100)}%
                    </span>
                    <span style={{ fontSize: "0.8125rem", color: "var(--ink-muted)" }}>overall fit</span>
                  </div>
                </div>

                {/* Score breakdown */}
                <div style={{ display: "flex", flexDirection: "column", gap: "0.625rem" }}>
                  <h4 style={{ fontSize: "0.8125rem", textTransform: "uppercase", letterSpacing: "0.08em", color: "var(--ink-muted)", marginBottom: "0.125rem" }}>
                    Match factors
                  </h4>
                  <ScoreBar value={selected.factors.cosine} label="Content similarity" />
                  <ScoreBar value={selected.factors.skillsOverlap} label="Skills overlap" />
                  <ScoreBar value={selected.factors.experience} label="Experience" />
                  <ScoreBar value={selected.factors.location} label="Location" />
                </div>

                {/* Skill gaps */}
                <div>
                  <h4 style={{ fontSize: "0.8125rem", textTransform: "uppercase", letterSpacing: "0.08em", color: "var(--ink-muted)", marginBottom: "0.625rem" }}>
                    Skill gaps
                  </h4>
                  {gapsLoading && <Spinner size={18} />}
                  {gapsError && <p style={{ fontSize: "0.875rem", color: "var(--warn)" }}>{gapsError}</p>}
                  {gaps !== null && !gapsLoading && gaps.length === 0 && (
                    <div style={{ display: "flex", alignItems: "center", gap: "0.5rem" }}>
                      <span style={{ fontSize: "1.125rem" }}>✅</span>
                      <span style={{ fontSize: "0.875rem", color: "var(--success)", fontWeight: 500 }}>
                        No skill gaps — strong match!
                      </span>
                    </div>
                  )}
                  {gaps !== null && !gapsLoading && gaps.length > 0 && (
                    <div style={{ display: "flex", flexWrap: "wrap", gap: "0.375rem" }}>
                      {gaps.map((skill) => (
                        <span
                          key={skill}
                          style={{
                            padding: "0.25rem 0.625rem",
                            background: "var(--warn-faint)",
                            color: "var(--warn)",
                            borderRadius: "999px",
                            fontSize: "0.8125rem",
                            fontWeight: 500,
                          }}
                        >
                          {skill}
                        </span>
                      ))}
                    </div>
                  )}
                </div>

                {selected.job.remoteEligible && (
                  <div
                    style={{
                      padding: "0.625rem 0.875rem",
                      background: "var(--accent-faint)",
                      borderRadius: "var(--radius-sm)",
                      fontSize: "0.8125rem",
                      color: "var(--accent)",
                      fontWeight: 500,
                    }}
                  >
                    🌐 Remote eligible
                  </div>
                )}
              </Card>
            ) : (
              <Card>
                <p style={{ color: "var(--ink-muted)", fontSize: "0.875rem" }}>
                  Select a job to view details and skill gap analysis.
                </p>
              </Card>
            )}
          </div>
        </div>
      )}
    </main>
  );
}
