import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import {
  getEmployerJob,
  listEmployerJobApplications,
  updateEmployerApplicationStatus,
} from "../api/employer";
import type { JobApplication, JobDetail } from "../api/employer";
import { ApiResponseError } from "../api/client";
import { Alert, Badge, Button, Card, Spinner } from "../components/ui";
import PageLoader from "../components/PageLoader";

function statusTone(status: JobApplication["status"]): "neutral" | "success" | "warn" | "accent" {
  if (status === "ACCEPTED") return "success";
  if (status === "REJECTED") return "warn";
  if (status === "UNDER_REVIEW") return "accent";
  return "neutral";
}

function formatDate(iso: string | null): string {
  if (!iso) {
    return "—";
  }
  return new Date(iso).toLocaleString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

function conflictMessage(error: unknown): string {
  if (error instanceof Error && error.message.includes("ERR_AUTH_003")) {
    return "Your session has ended. Please sign in again.";
  }
  if (error instanceof ApiResponseError && error.response.status === 409) {
    return "This application's status has already changed. Reload to see the current state.";
  }
  return "Could not update this application. Please try again.";
}

function laneLabel(status: "draft" | "submitted" | "under-review" | "decided"): string {
  if (status === "draft") return "Draft (not yet submitted)";
  if (status === "submitted") return "Submitted";
  if (status === "under-review") return "Under review";
  return "Decided";
}

export default function ApplicationReview() {
  const { jobId } = useParams();
  const [job, setJob] = useState<JobDetail | null>(null);
  const [applications, setApplications] = useState<JobApplication[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [updateError, setUpdateError] = useState<string | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);
  const [busyId, setBusyId] = useState<string | null>(null);

  const loadApplications = async () => {
    if (!jobId) {
      setError("Job not found.");
      setLoading(false);
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const [jobResult, applicationResult] = await Promise.all([
        getEmployerJob(jobId),
        listEmployerJobApplications(jobId),
      ]);
      setJob(jobResult);
      setApplications(applicationResult);
    } catch (err) {
      if (err instanceof ApiResponseError && err.response.status === 404) {
        setError("This job could not be found.");
      } else if (err instanceof Error && err.message.includes("ERR_AUTH_003")) {
        setError("Your session has ended. Please sign in again.");
      } else {
        setError("Could not load applications for this job.");
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    document.title = "Application Review - JobVault";
  }, []);

  useEffect(() => {
    void loadApplications();
  }, [jobId, refreshKey]);

  const lanes = useMemo(() => {
    const draft = applications.filter((application) => application.status === "DRAFT");
    const submitted = applications.filter((application) => application.status === "SUBMITTED");
    const underReview = applications.filter((application) => application.status === "UNDER_REVIEW");
    const decided = applications.filter((application) =>
      application.status === "ACCEPTED" || application.status === "REJECTED" || application.status === "WITHDRAWN"
    );
    return { draft, submitted, underReview, decided };
  }, [applications]);

  const changeStatus = async (applicationId: string, status: "UNDER_REVIEW" | "ACCEPTED" | "REJECTED") => {
    setBusyId(applicationId);
    setUpdateError(null);
    try {
      const updated = await updateEmployerApplicationStatus(applicationId, status);
      setApplications((current) => current.map((application) => (application.id === applicationId ? updated : application)));
    } catch (err) {
      setUpdateError(conflictMessage(err));
    } finally {
      setBusyId(null);
    }
  };

  return (
    <main style={{ maxWidth: "var(--page-max-width)", margin: "0 auto", padding: "2rem 1.5rem 3rem" }}>
      <div style={{ display: "flex", alignItems: "flex-end", justifyContent: "space-between", gap: "1rem", marginBottom: "1.5rem" }}>
        <div>
          <Link to="/employer" style={{ color: "var(--ink-muted)", fontSize: "0.875rem" }}>
            ← Back to dashboard
          </Link>
          <h1 style={{ marginTop: "0.75rem" }}>Application review</h1>
          <p style={{ color: "var(--ink-muted)", maxWidth: 58 * 10 }}>
            Move applications through review and decision states. Cards update in place after each action.
          </p>
        </div>
      </div>

      {error && <Alert tone={error.includes("session") ? "info" : "error"}>{error}</Alert>}
      {updateError && (
        <div style={{ marginTop: "1rem", display: "flex", alignItems: "center", gap: "0.75rem", flexWrap: "wrap" }}>
          <Alert tone="error">{updateError}</Alert>
          <Button variant="secondary" size="sm" onClick={() => setRefreshKey((current) => current + 1)}>
            Reload
          </Button>
        </div>
      )}

      {loading ? (
        <PageLoader />
      ) : error ? null : (
        <div style={{ marginTop: "1.5rem" }}>
          <div
            style={{
              display: "grid",
              gridTemplateColumns: "repeat(auto-fit, minmax(240px, 1fr))",
              gap: "1rem",
              alignItems: "start",
            }}
          >
            {([
              ["draft", lanes.draft],
              ["submitted", lanes.submitted],
              ["under-review", lanes.underReview],
              ["decided", lanes.decided],
            ] as const).map(([laneId, laneItems]) => (
              <section key={laneId} aria-label={laneLabel(laneId)}>
                <Card>
                  <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: "0.75rem", marginBottom: "1rem" }}>
                    <h2 style={{ fontSize: "1.05rem", margin: 0 }}>{laneLabel(laneId)}</h2>
                    <Badge tone="neutral">{laneItems.length}</Badge>
                  </div>

                  <div style={{ display: "flex", flexDirection: "column", gap: "0.75rem" }}>
                    {laneItems.length === 0 ? (
                      <p style={{ color: "var(--ink-muted)", fontSize: "0.875rem" }}>No applications here.</p>
                    ) : (
                      laneItems.map((application) => (
                        <article
                          key={application.id}
                          style={{
                            border: "1px solid var(--border)",
                            borderRadius: "var(--radius-sm)",
                            padding: "0.85rem",
                            background: "var(--bg-card)",
                            display: "grid",
                            gap: "0.75rem",
                          }}
                        >
                          <div style={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between", gap: "1rem" }}>
                            <div>
                              <Badge tone={statusTone(application.status)}>{application.status}</Badge>
                              <p style={{ marginTop: "0.55rem", color: "var(--ink-muted)", fontSize: "0.875rem" }}>
                                {application.seekerName ?? `Candidate ${application.seekerId.slice(0, 8)}…`}
                              </p>
                            </div>
                            <span style={{ color: "var(--ink-muted)", fontSize: "0.75rem" }}>{formatDate(application.submittedAt)}</span>
                          </div>

                          {laneId === "draft" ? (
                            <p style={{ color: "var(--ink-muted)", fontSize: "0.8125rem" }}>
                              Saved by the candidate - not yet submitted.
                            </p>
                          ) : application.status === "SUBMITTED" ? (
                            <Button loading={busyId === application.id} variant="secondary" size="sm" onClick={() => void changeStatus(application.id, "UNDER_REVIEW")}>Move to Under Review</Button>
                          ) : application.status === "UNDER_REVIEW" ? (
                            <div style={{ display: "flex", gap: "0.5rem", flexWrap: "wrap" }}>
                              <Button loading={busyId === application.id} size="sm" onClick={() => void changeStatus(application.id, "ACCEPTED")}>Accept</Button>
                              <Button loading={busyId === application.id} size="sm" variant="secondary" onClick={() => void changeStatus(application.id, "REJECTED")}>Reject</Button>
                            </div>
                          ) : (
                            <div style={{ color: "var(--ink-muted)", fontSize: "0.8125rem" }}>
                              {application.status === "ACCEPTED" ? "Accepted" : application.status === "REJECTED" ? "Rejected" : "Withdrawn"} on {formatDate(application.decidedAt ?? application.reviewedAt)}
                            </div>
                          )}
                        </article>
                      ))
                    )}
                  </div>
                </Card>
              </section>
            ))}
          </div>
        </div>
      )}

      {job && (
        <div style={{ marginTop: "1.25rem", color: "var(--ink-muted)", fontSize: "0.875rem" }}>
          Job: {job.title}
        </div>
      )}
    </main>
  );
}
