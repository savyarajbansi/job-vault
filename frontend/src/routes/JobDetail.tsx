import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";

import {
  EDUCATION_LABELS,
  formatSalaryRange,
  getEmployerJob,
} from "../api/employer";
import type { JobDetail as JobDetailData } from "../api/employer";
import { getPublicJob } from "../api/jobs";
import { ApiResponseError } from "../api/client";
import { useAuth } from "../api/authContext";
import { Alert, Badge, Button, Card, Divider, Spinner } from "../components/ui";
import JobSkillsEditor from "../components/JobSkillsEditor";
import JobApplyPanel from "../components/JobApplyPanel";

function statusTone(status: JobDetailData["status"]): "neutral" | "success" | "warn" {
  if (status === "ACTIVE") return "success";
  if (status === "DISABLED") return "warn";
  return "neutral";
}

function loadErrorMessage(error: unknown): string {
  if (error instanceof Error && error.message.includes("ERR_AUTH_003")) {
    return "Your session has ended. Please sign in again.";
  }
  if (error instanceof ApiResponseError && error.response.status === 404) {
    return "This job could not be found. It may have been removed or is no longer active.";
  }
  return "Could not load this job. Please try again.";
}

export default function JobDetailPage() {
  const { jobId } = useParams();
  const { isAuthenticated, isSessionReady, roles } = useAuth();
  const isEmployerViewer = isAuthenticated && roles.includes("EMPLOYER");
  const isSeekerViewer = isAuthenticated && roles.includes("JOB_SEEKER");

  const [job, setJob] = useState<JobDetailData | null>(null);
  const [owned, setOwned] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!isSessionReady) {
      return;
    }
    if (!jobId) {
      setError("Job not found.");
      setLoading(false);
      return;
    }

    let cancelled = false;
    const load = async () => {
      setLoading(true);
      setError(null);
      try {
        if (isEmployerViewer) {
          const detail = await getEmployerJob(jobId);
          if (!cancelled) {
            setJob(detail);
            setOwned(true);
          }
          return;
        }
        const detail = await getPublicJob(jobId);
        if (!cancelled) {
          setJob(detail);
          setOwned(false);
        }
      } catch (err) {
        if (!cancelled) {
          setError(loadErrorMessage(err));
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    };

    void load();
    return () => {
      cancelled = true;
    };
  }, [jobId, isSessionReady, isEmployerViewer]);

  const salaryLabel = job ? formatSalaryRange(job.salaryMin, job.salaryMax) : null;
  const backHref = owned
    ? "/employer"
    : isSeekerViewer
      ? "/seeker/matches"
      : "/jobs";

  return (
    <main style={{ maxWidth: 820, margin: "0 auto", padding: "2rem 1.5rem 3rem" }}>
      <div style={{ marginBottom: "1.5rem" }}>
        <Link to={backHref} style={{ color: "var(--ink-muted)", fontSize: "0.875rem" }}>
          ← Back
        </Link>
      </div>

      {loading || !isSessionReady ? (
        <div style={{ display: "flex", justifyContent: "center", padding: "4rem 0" }}>
          <Spinner size={32} />
        </div>
      ) : error || !job ? (
        <Alert tone="error">{error ?? "This job could not be found."}</Alert>
      ) : (
        <Card>
          <div
            style={{
              display: "flex",
              justifyContent: "space-between",
              alignItems: "flex-start",
              gap: "1rem",
              marginBottom: "0.5rem",
            }}
          >
            <div style={{ minWidth: 0 }}>
              <h1 style={{ marginBottom: "0.375rem" }}>{job.title}</h1>
              {job.companyName && (
                <p style={{ fontSize: "0.9375rem", color: "var(--ink-2)", fontWeight: 500 }}>
                  {job.companyName}
                </p>
              )}
            </div>
            {owned && <Badge tone={statusTone(job.status)}>{job.status}</Badge>}
          </div>

          <div style={{ display: "flex", flexWrap: "wrap", gap: "0.5rem", marginBottom: "0.75rem" }}>
            {job.location && <Badge tone="neutral">{job.location}</Badge>}
            {job.remoteEligible && <Badge tone="accent">Remote eligible</Badge>}
            {salaryLabel && <Badge tone="neutral">{salaryLabel}</Badge>}
            {job.minExperienceYears != null && (
              <Badge tone="neutral">{job.minExperienceYears}+ yrs experience</Badge>
            )}
            {job.educationRequirement && (
              <Badge tone="neutral">{EDUCATION_LABELS[job.educationRequirement]}</Badge>
            )}
          </div>

          {job.publishedAt && (
            <p style={{ fontSize: "0.8125rem", color: "var(--ink-muted)", marginBottom: "1rem" }}>
              Posted{" "}
              {new Date(job.publishedAt).toLocaleDateString("en-US", {
                month: "short",
                day: "numeric",
                year: "numeric",
              })}
            </p>
          )}

          {/* Apply/draft action — this is the only path a seeker who arrived
              via Browse (no parsed resume yet) has to act on a job, since
              /seeker/matches requires one. Not shown for employer-owned jobs. */}
          {!owned && (
            <div style={{ marginBottom: "1.5rem" }}>
              {isSeekerViewer ? (
                <JobApplyPanel jobId={job.id} />
              ) : !isAuthenticated ? (
                <div
                  style={{
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "space-between",
                    gap: "1rem",
                    flexWrap: "wrap",
                    padding: "0.875rem",
                    background: "var(--bg-subtle)",
                    borderRadius: "var(--radius-sm)",
                  }}
                >
                  <span style={{ fontSize: "0.875rem", color: "var(--ink-2)" }}>
                    Sign in as a job seeker to apply to this role.
                  </span>
                  <Link to="/auth">
                    <Button size="sm">Sign in</Button>
                  </Link>
                </div>
              ) : null}
            </div>
          )}

          <Divider label="Description" />
          <p
            style={{
              marginTop: "1rem",
              marginBottom: "1.5rem",
              color: "var(--ink-2)",
              lineHeight: 1.7,
              whiteSpace: "pre-wrap",
            }}
          >
            {job.description}
          </p>

          <Divider label="Required skills" />
          <div style={{ marginTop: "1rem" }}>
            {owned ? (
              <JobSkillsEditor job={job} onUpdate={setJob} />
            ) : job.requiredSkills.length === 0 ? (
              <p style={{ fontSize: "0.875rem", color: "var(--ink-muted)" }}>
                No specific skills were listed for this role.
              </p>
            ) : (
              <div style={{ display: "flex", flexWrap: "wrap", gap: "0.45rem" }}>
                {job.requiredSkills.map((skill) => (
                  <span
                    key={skill}
                    style={{
                      padding: "0.25rem 0.625rem",
                      background: "var(--accent-faint)",
                      color: "var(--accent)",
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

          {owned && (
            <div style={{ marginTop: "1.75rem" }}>
              <Link to={`/employer/jobs/${job.id}`}>
                <Button variant="secondary" size="sm">
                  Edit job
                </Button>
              </Link>
            </div>
          )}
        </Card>
      )}
    </main>
  );
}