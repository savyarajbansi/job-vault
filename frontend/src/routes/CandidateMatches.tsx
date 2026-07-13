import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import {
  getEmployerCandidateMatches,
  getEmployerJob,
  notifyEmployerCandidate,
} from "../api/employer";
import type { CandidateMatchItem, JobDetail } from "../api/employer";
import { ApiResponseError } from "../api/client";
import { Alert, Badge, Button, Card, ScoreBar, Spinner } from "../components/ui";
import PageLoader from "../components/PageLoader";
import { formatScore } from "../utils/score";

function scoreColor(score: number): string {
  if (score >= 0.7) return "var(--success)";
  if (score >= 0.4) return "#B07D20";
  return "var(--ink-muted)";
}

function truncateId(value: string): string {
  return value.length <= 8 ? value : `${value.slice(0, 8)}…`;
}

function matchStateMessage(error: unknown): string {
  if (error instanceof Error && error.message.includes("ERR_AUTH_003")) {
    return "Your session has ended. Please sign in again.";
  }
  if (error instanceof ApiResponseError && error.response.status === 409) {
    return "This job is not ACTIVE yet. Publish it before reviewing candidate matches.";
  }
  if (error instanceof ApiResponseError && error.response.status === 404) {
    return "No parsed resumes are available for this job yet.";
  }
  return "Could not load candidate matches. Please try again.";
}

export default function CandidateMatches() {
  const navigate = useNavigate();
  const { jobId } = useParams();
  const [job, setJob] = useState<JobDetail | null>(null);
  const [items, setItems] = useState<CandidateMatchItem[]>([]);
  const [pageTotal, setPageTotal] = useState(0);
  const [selected, setSelected] = useState<CandidateMatchItem | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [notificationState, setNotificationState] = useState<Record<string, "idle" | "loading" | "notified" | "below">>({});

  const limit = 10;

  const loadMatches = async () => {
    if (!jobId) {
      setError("Job not found.");
      setLoading(false);
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const [jobResult, matchResult] = await Promise.all([
        getEmployerJob(jobId),
        getEmployerCandidateMatches(jobId, { limit, offset: page * limit }),
      ]);
      setJob(jobResult);
      setPageTotal(matchResult.page.total);
      setItems(matchResult.items);
      setSelected((current) => {
        if (current && matchResult.items.some((item) => item.seekerId === current.seekerId)) {
          return current;
        }
        return matchResult.items[0] ?? null;
      });
    } catch (err) {
      setError(matchStateMessage(err));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    document.title = "Candidate Matches - JobVault";
  }, []);

  useEffect(() => {
    void loadMatches();
  }, [jobId, page]);

  const totalPages = useMemo(() => Math.max(1, Math.ceil(pageTotal / limit)), [pageTotal]);

  const notifyCandidate = async (item: CandidateMatchItem) => {
    if (!jobId) {
      return;
    }
    setNotificationState((current) => ({ ...current, [item.seekerId]: "loading" }));
    try {
      const response = await notifyEmployerCandidate(jobId, item.seekerId, item.score);
      setNotificationState((current) => ({
        ...current,
        [item.seekerId]: response.notified ? "notified" : "below",
      }));
    } catch {
      setNotificationState((current) => ({ ...current, [item.seekerId]: "idle" }));
      setError("Could not notify this candidate. Please try again.");
    }
  };

  return (
    <main style={{ maxWidth: "var(--page-max-width)", margin: "0 auto", padding: "2rem 1.5rem 3rem" }}>
      <div style={{ display: "flex", alignItems: "flex-end", justifyContent: "space-between", gap: "1rem", marginBottom: "1.5rem" }}>
        <div>
          <Link to="/employer" style={{ color: "var(--ink-muted)", fontSize: "0.875rem" }}>
            ← Back to dashboard
          </Link>
          <h1 style={{ marginTop: "0.75rem" }}>Candidate matches</h1>
          <p style={{ color: "var(--ink-muted)", maxWidth: 58 * 10 }}>
            Ranked candidates for the active job. Scores are based on the same match factors used elsewhere in JobVault.
          </p>
        </div>
        {job && <Badge tone={job.status === "ACTIVE" ? "success" : job.status === "DISABLED" ? "warn" : "neutral"}>{job.status}</Badge>}
      </div>

      {loading ? (
        <PageLoader />
      ) : error ? (
        <Alert tone={error.includes("job") ? "info" : "error"}>{error}</Alert>
      ) : (
        <div style={{ display: "grid", gridTemplateColumns: "minmax(0, 1.2fr) minmax(320px, 0.8fr)", gap: "1.5rem" }}>
          <div style={{ display: "flex", flexDirection: "column", gap: "0.75rem" }}>
            {items.length === 0 ? (
              <Card>
                <p style={{ color: "var(--ink-muted)" }}>
                  This job has no active matches. Add a clearer description and more specific skill keywords.
                </p>
              </Card>
            ) : (
              items.map((item) => {
                const activeState = notificationState[item.seekerId] ?? "idle";
                const isSelected = selected?.seekerId === item.seekerId;
                return (
                  <button
                    key={item.seekerId}
                    type="button"
                    onClick={() => setSelected(item)}
                    style={{
                      textAlign: "left",
                      background: isSelected ? "var(--bg-card)" : "var(--bg-card)",
                      border: `1.5px solid ${isSelected ? "var(--accent)" : "var(--border)"}`,
                      borderRadius: "var(--radius)",
                      boxShadow: isSelected ? "0 0 0 3px rgba(45,110,110,0.1)" : "var(--shadow-sm)",
                      padding: "1rem 1.125rem",
                    }}
                  >
                    <div style={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between", gap: "1rem" }}>
                      <div style={{ minWidth: 0 }}>
                        <div style={{ display: "flex", alignItems: "center", gap: "0.5rem", marginBottom: "0.35rem", flexWrap: "wrap" }}>
                          <h2 style={{ fontSize: "0.975rem", margin: 0 }}>
                            {item.seekerName ?? `Candidate ${truncateId(item.seekerId)}`}
                          </h2>
                          <Badge tone={item.missingSkills.length === 0 ? "success" : "neutral"}>
                            {item.missingSkills.length === 0 ? "No gaps" : `${item.missingSkills.length} gaps`}
                          </Badge>
                          {activeState === "notified" && <Badge tone="success">Notified</Badge>}
                          {activeState === "below" && <Badge tone="warn">Below threshold</Badge>}
                        </div>
                        <p style={{ color: "var(--ink-muted)", fontSize: "0.875rem", marginBottom: "0.5rem" }}>
                          {truncateId(item.seekerId)}
                        </p>
                        <div style={{ display: "flex", gap: "0.75rem", flexWrap: "wrap", color: "var(--ink-muted)", fontSize: "0.8125rem" }}>
                          <span>Cosine {Math.round(item.factors.cosine * 100)}%</span>
                          <span>Skills {Math.round(item.factors.skillsOverlap * 100)}%</span>
                          <span>Experience {Math.round(item.factors.experience * 100)}%</span>
                          <span>Location {Math.round(item.factors.location * 100)}%</span>
                        </div>
                      </div>
                      <div style={{ flexShrink: 0, fontFamily: "var(--font-display)", fontSize: "1.35rem", fontWeight: 700, color: scoreColor(item.score) }}>
                        {formatScore(item.score)}
                      </div>
                    </div>

                    <div style={{ display: "flex", flexWrap: "wrap", gap: "0.45rem", marginTop: "0.85rem" }}>
                      {item.missingSkills.slice(0, 3).map((skill) => (
                        <span
                          key={skill}
                          style={{
                            padding: "0.25rem 0.6rem",
                            borderRadius: "999px",
                            background: "var(--warn-faint)",
                            color: "var(--warn)",
                            fontSize: "0.8rem",
                          }}
                        >
                          {skill}
                        </span>
                      ))}
                    </div>
                  </button>
                );
              })
            )}

            {items.length > 0 && (
              <div style={{ display: "flex", justifyContent: "center", alignItems: "center", gap: "0.5rem", marginTop: "0.5rem" }}>
                <Button variant="ghost" size="sm" disabled={page === 0} onClick={() => setPage((current) => Math.max(0, current - 1))}>
                  Previous
                </Button>
                <span style={{ fontSize: "0.875rem", color: "var(--ink-muted)" }}>
                  {page + 1} / {totalPages}
                </span>
                <Button variant="ghost" size="sm" disabled={items.length < limit} onClick={() => setPage((current) => current + 1)}>
                  Next
                </Button>
              </div>
            )}
          </div>

          <Card>
            {selected ? (
              <div style={{ display: "flex", flexDirection: "column", gap: "1.25rem" }}>
                <div>
                  <p style={{ color: "var(--ink-muted)", fontSize: "0.8125rem", marginBottom: "0.35rem" }}>Selected candidate</p>
                  <h2 style={{ marginBottom: "0.25rem" }}>
                    {selected.seekerName ?? `Candidate ${truncateId(selected.seekerId)}`}
                  </h2>
                  <p style={{ color: "var(--ink-muted)", fontSize: "0.8125rem", marginBottom: "0.5rem" }}>
                    {truncateId(selected.seekerId)}
                  </p>
                  <div style={{ display: "inline-flex", alignItems: "center", gap: "0.5rem", padding: "0.4rem 0.75rem", borderRadius: "var(--radius-sm)", background: "var(--bg-subtle)" }}>
                    <span style={{ fontFamily: "var(--font-display)", fontSize: "1.6rem", fontWeight: 700, color: scoreColor(selected.score) }}>
                      {formatScore(selected.score)}
                    </span>
                    <span style={{ color: "var(--ink-muted)" }}>overall fit</span>
                  </div>
                </div>

                <div style={{ display: "grid", gap: "0.75rem" }}>
                  <ScoreBar value={selected.factors.cosine} label="Content similarity" />
                  <ScoreBar value={selected.factors.skillsOverlap} label="Skills overlap" />
                  <ScoreBar value={selected.factors.experience} label="Experience" />
                  <ScoreBar value={selected.factors.location} label="Location" />
                </div>

                <div>
                  <h3 style={{ fontSize: "0.875rem", marginBottom: "0.65rem", color: "var(--ink-muted)", textTransform: "uppercase", letterSpacing: "0.08em" }}>
                    Missing skills
                  </h3>
                  {selected.missingSkills.length === 0 ? (
                    <p style={{ color: "var(--success)" }}>No skill gaps were found for this candidate.</p>
                  ) : (
                    <div style={{ display: "flex", flexWrap: "wrap", gap: "0.45rem" }}>
                      {selected.missingSkills.map((skill) => (
                        <span
                          key={skill}
                          style={{
                            padding: "0.25rem 0.6rem",
                            borderRadius: "999px",
                            background: "var(--warn-faint)",
                            color: "var(--warn)",
                            fontSize: "0.8rem",
                          }}
                        >
                          {skill}
                        </span>
                      ))}
                    </div>
                  )}
                </div>

                <div style={{ display: "flex", alignItems: "center", gap: "0.75rem", flexWrap: "wrap" }}>
                  <Button
                    onClick={() => void notifyCandidate(selected)}
                    disabled={(notificationState[selected.seekerId] ?? "idle") !== "idle"}
                    loading={(notificationState[selected.seekerId] ?? "idle") === "loading"}
                  >
                    Notify candidate
                  </Button>
                  {notificationState[selected.seekerId] === "below" && (
                    <span style={{ color: "var(--ink-muted)", fontSize: "0.875rem" }}>Score below notification threshold.</span>
                  )}
                  {notificationState[selected.seekerId] === "notified" && (
                    <span style={{ color: "var(--success)", fontSize: "0.875rem" }}>Notification sent.</span>
                  )}
                </div>

                {job && (
                  <div style={{ display: "flex", justifyContent: "space-between", gap: "1rem", borderTop: "1px solid var(--border)", paddingTop: "1rem", color: "var(--ink-muted)", fontSize: "0.875rem" }}>
                    <span>{job.title}</span>
                    <span>{job.status}</span>
                  </div>
                )}
              </div>
            ) : (
              <p style={{ color: "var(--ink-muted)" }}>Select a candidate to review the breakdown and send a notification.</p>
            )}
          </Card>
        </div>
      )}

      <div style={{ marginTop: "1rem" }}>
        <Button variant="ghost" size="sm" onClick={() => navigate("/employer")}>Back to dashboard</Button>
      </div>
    </main>
  );
}
