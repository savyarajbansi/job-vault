import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import {
  disableEmployerJob,
  formatSalaryRange,
  getEmployerJob,
  getEmployerJobs,
  publishEmployerJob,
  reactivateEmployerJob,
} from "../api/employer";
import type { JobDetail, JobStatus, JobSummary } from "../api/employer";
import { ApiResponseError } from "../api/client";
import { Alert, Badge, Button, Card, Spinner } from "../components/ui";

function statusTone(status: JobStatus): "neutral" | "success" | "warn" {
  if (status === "ACTIVE") return "success";
  if (status === "DISABLED") return "warn";
  return "neutral";
}

function formatRelative(iso: string): string {
  const diffMs = Date.now() - new Date(iso).getTime();
  const diffDays = Math.max(0, Math.round(diffMs / 86400000));
  if (diffDays === 0) return "today";
  if (diffDays === 1) return "1 day ago";
  return `${diffDays} days ago`;
}

function lifecycleMessage(
  action: "publish" | "disable" | "reactivate",
  error: unknown
): string {
  if (error instanceof Error && error.message.includes("ERR_AUTH_003")) {
    return "Your session has ended. Please sign in again.";
  }
  if (error instanceof ApiResponseError && error.response.status === 409) {
    if (action === "publish") return "This job is already published.";
    if (action === "disable") return "This job is already disabled.";
    return "This job cannot be reactivated right now. It may have been blocked by moderation.";
  }
  return "Action failed. Please try again.";
}

export default function EmployerDashboard() {
  const navigate = useNavigate();
  const [jobs, setJobs] = useState<JobSummary[]>([]);
  const [detailsById, setDetailsById] = useState<Record<string, JobDetail>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [actionLoadingId, setActionLoadingId] = useState<string | null>(null);

  const loadJobs = async () => {
    setLoading(true);
    setError(null);
    try {
      const items = await getEmployerJobs();
      setJobs(items);
      const loadedDetails = await Promise.all(
        items.map(async (job) => {
          try {
            return [job.id, await getEmployerJob(job.id)] as const;
          } catch {
            return null;
          }
        })
      );
      setDetailsById(
        Object.fromEntries(
          loadedDetails.filter(
            (entry): entry is readonly [string, JobDetail] => Boolean(entry)
          )
        )
      );
    } catch (err) {
      const msg = err instanceof Error ? err.message : "";
      setError(
        msg.includes("ERR_AUTH_003")
          ? "Your session has ended. Please sign in again."
          : "Could not load your jobs. Please try again."
      );
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadJobs();
  }, []);

  const activeCount = useMemo(
    () => jobs.filter((job) => job.status === "ACTIVE").length,
    [jobs]
  );

  const runAction = async (
    job: JobSummary,
    action: "publish" | "disable" | "reactivate"
  ) => {
    setActionLoadingId(job.id);
    setActionError(null);
    try {
      const updated =
        action === "publish"
          ? await publishEmployerJob(job.id)
          : action === "disable"
            ? await disableEmployerJob(job.id)
            : await reactivateEmployerJob(job.id);
      setJobs((current) =>
        current.map((item) =>
          item.id === job.id ? { ...item, status: updated.status } : item
        )
      );
      setDetailsById((current) => ({ ...current, [job.id]: updated }));
    } catch (err) {
      setActionError(lifecycleMessage(action, err));
    } finally {
      setActionLoadingId(null);
    }
  };

  return (
    <main style={{ maxWidth: 1180, margin: "0 auto", padding: "2rem 1.5rem 3rem" }}>
      {/* ── Page header ── */}
      <div
        style={{
          display: "flex",
          alignItems: "flex-end",
          justifyContent: "space-between",
          gap: "1rem",
          marginBottom: "1.5rem",
        }}
      >
        <div>
          <p
            style={{
              fontSize: "0.8125rem",
              letterSpacing: "0.08em",
              textTransform: "uppercase",
              color: "var(--ink-muted)",
              marginBottom: "0.35rem",
            }}
          >
            Employer workspace
          </p>
          <h1 style={{ marginBottom: "0.4rem" }}>Your jobs</h1>
          <p style={{ color: "var(--ink-muted)", maxWidth: 600 }}>
            Create a posting, publish when it is ready, and keep the active pipeline visible.
          </p>
        </div>
        <Button onClick={() => navigate("/employer/jobs/new")}>Post a job</Button>
      </div>

      {error && (
        <Alert tone={error.includes("session") ? "info" : "error"}>{error}</Alert>
      )}
      {actionError && (
        <div style={{ marginTop: "1rem" }}>
          <Alert tone="error">{actionError}</Alert>
        </div>
      )}

      {loading ? (
        <div style={{ display: "flex", justifyContent: "center", padding: "4rem 0" }}>
          <Spinner size={32} />
        </div>
      ) : jobs.length === 0 ? (
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "minmax(0, 1.4fr) minmax(280px, 0.8fr)",
            gap: "1.5rem",
            marginTop: "1.5rem",
          }}
        >
          <Card>
            <h2 style={{ marginBottom: "0.5rem" }}>You have not posted any jobs yet.</h2>
            <p style={{ color: "var(--ink-muted)", marginBottom: "1.25rem" }}>
              Draft a posting, review it, and publish when it is ready for candidates.
            </p>
            <Button onClick={() => navigate("/employer/jobs/new")}>
              Create your first job
            </Button>
          </Card>
          <Card>
            <h2 style={{ marginBottom: "0.75rem" }}>Status at a glance</h2>
            <div style={{ display: "grid", gap: "0.75rem" }}>
              <div style={{ display: "flex", justifyContent: "space-between", gap: "1rem" }}>
                <span style={{ color: "var(--ink-muted)" }}>Active jobs</span>
                <strong>{activeCount}</strong>
              </div>
              <div style={{ display: "flex", justifyContent: "space-between", gap: "1rem" }}>
                <span style={{ color: "var(--ink-muted)" }}>Total jobs</span>
                <strong>{jobs.length}</strong>
              </div>
              <p style={{ color: "var(--ink-muted)", fontSize: "0.875rem" }}>
                Notifications for applications and strong matches appear in the bell in the header.
              </p>
            </div>
          </Card>
        </div>
      ) : (
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "minmax(0, 1.45fr) minmax(280px, 0.75fr)",
            gap: "1.5rem",
            marginTop: "1.5rem",
          }}
        >
          {/* ── Job list ── */}
          <div style={{ display: "flex", flexDirection: "column", gap: "0.75rem" }}>
            {jobs.map((job) => {
              const detail = detailsById[job.id];
              const salaryLabel = formatSalaryRange(job.salaryMin, job.salaryMax);
              const publishedLabel =
                job.status === "DRAFT"
                  ? "Not published"
                  : detail?.publishedAt
                    ? `Published ${formatRelative(detail.publishedAt)}`
                    : `Created ${formatRelative(job.createdAt)}`;
              const actionLoading = actionLoadingId === job.id;

              return (
                <Card key={job.id}>
                  <div
                    style={{
                      display: "flex",
                      justifyContent: "space-between",
                      gap: "1rem",
                      alignItems: "flex-start",
                    }}
                  >
                    <div style={{ minWidth: 0, flex: 1 }}>
                      {/* Title row */}
                      <div
                        style={{
                          display: "flex",
                          alignItems: "center",
                          gap: "0.5rem",
                          marginBottom: "0.2rem",
                          flexWrap: "wrap",
                        }}
                      >
                        <h2
                          style={{
                            fontSize: "1.1rem",
                            margin: 0,
                            maxWidth: "36rem",
                            overflow: "hidden",
                            textOverflow: "ellipsis",
                            whiteSpace: "nowrap",
                          }}
                        >
                          {job.title}
                        </h2>
                        <Badge tone={statusTone(job.status)}>{job.status}</Badge>
                      </div>

                      {/* Company name */}
                      {job.companyName && (
                        <p
                          style={{
                            fontSize: "0.875rem",
                            color: "var(--ink-2)",
                            fontWeight: 500,
                            marginBottom: "0.35rem",
                          }}
                        >
                          {job.companyName}
                        </p>
                      )}

                      {/* Metadata row */}
                      <div
                        style={{
                          display: "flex",
                          flexWrap: "wrap",
                          gap: "0.75rem",
                          color: "var(--ink-muted)",
                          fontSize: "0.875rem",
                        }}
                      >
                        <span>{publishedLabel}</span>
                        {job.location ? (
                          <span>{job.location}</span>
                        ) : (
                          <span>Location not set</span>
                        )}
                        {job.remoteEligible ? (
                          <span>Remote eligible</span>
                        ) : (
                          <span>On-site</span>
                        )}
                        {salaryLabel && (
                          <span style={{ color: "var(--ink-2)", fontWeight: 500 }}>
                            {salaryLabel}
                          </span>
                        )}
                      </div>
                    </div>

                    {/* Action button */}
                    <div
                      style={{
                        display: "flex",
                        flexDirection: "column",
                        alignItems: "flex-end",
                        gap: "0.5rem",
                        flexShrink: 0,
                      }}
                    >
                      {job.status === "DRAFT" && (
                        <Button
                          loading={actionLoading}
                          onClick={() => void runAction(job, "publish")}
                        >
                          Publish
                        </Button>
                      )}
                      {job.status === "ACTIVE" && (
                        <Button
                          loading={actionLoading}
                          variant="secondary"
                          onClick={() => void runAction(job, "disable")}
                        >
                          Disable
                        </Button>
                      )}
                      {job.status === "DISABLED" && (
                        <Button
                          loading={actionLoading}
                          variant="secondary"
                          onClick={() => void runAction(job, "reactivate")}
                        >
                          Reactivate
                        </Button>
                      )}
                    </div>
                  </div>

                  {/* Card footer links */}
                  <div
                    style={{
                      display: "flex",
                      flexWrap: "wrap",
                      gap: "0.75rem",
                      marginTop: "1rem",
                    }}
                  >
                    <Link
                      to={`/jobs/${job.id}`}
                      style={{ color: "var(--accent)", fontSize: "0.875rem", fontWeight: 500 }}
                    >
                      View job page
                    </Link>
                    <Link
                      to={`/employer/jobs/${job.id}`}
                      style={{ color: "var(--accent)", fontSize: "0.875rem", fontWeight: 500 }}
                    >
                      Edit
                    </Link>
                    {job.status === "ACTIVE" && (
                      <Link
                        to={`/employer/jobs/${job.id}/matches`}
                        style={{ color: "var(--accent)", fontSize: "0.875rem", fontWeight: 500 }}
                      >
                        View matches
                      </Link>
                    )}
                    {(job.status === "ACTIVE" || job.status === "DISABLED") && (
                      <Link
                        to={`/employer/jobs/${job.id}/applications`}
                        style={{ color: "var(--accent)", fontSize: "0.875rem", fontWeight: 500 }}
                      >
                        View applications
                      </Link>
                    )}
                  </div>
                </Card>
              );
            })}
          </div>

          {/* ── Overview sidebar ── */}
          <Card>
            <h2 style={{ marginBottom: "1rem" }}>Overview</h2>
            <div style={{ display: "grid", gap: "0.85rem" }}>
              <div style={{ display: "flex", justifyContent: "space-between", gap: "1rem" }}>
                <span style={{ color: "var(--ink-muted)" }}>Active jobs</span>
                <strong>{activeCount}</strong>
              </div>
              <div style={{ display: "flex", justifyContent: "space-between", gap: "1rem" }}>
                <span style={{ color: "var(--ink-muted)" }}>Total jobs</span>
                <strong>{jobs.length}</strong>
              </div>
              <p style={{ color: "var(--ink-muted)", fontSize: "0.875rem", lineHeight: 1.6 }}>
                Candidate matches appear on active jobs only. Application review lives on each
                job's application view.
              </p>
              <Button variant="ghost" onClick={() => void loadJobs()}>
                Refresh jobs
              </Button>
            </div>
          </Card>
        </div>
      )}
    </main>
  );
}
