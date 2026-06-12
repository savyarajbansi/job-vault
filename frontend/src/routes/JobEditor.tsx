import { FormEvent, useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import {
  createEmployerJob,
  getEmployerJob,
  JobDetail,
  JobStatus,
  publishEmployerJob,
  disableEmployerJob,
  reactivateEmployerJob,
  updateEmployerJob,
} from "../api/employer";
import { ApiResponseError } from "../api/client";
import { Alert, Badge, Button, Card, Divider, Input, Spinner } from "../components/ui";

type Props = {
  mode: "create" | "edit";
};

function statusTone(status: JobStatus): "neutral" | "success" | "warn" {
  if (status === "ACTIVE") return "success";
  if (status === "DISABLED") return "warn";
  return "neutral";
}

function lifecycleMessage(action: "publish" | "disable" | "reactivate", error: unknown): string {
  if (error instanceof Error && error.message.includes("ERR_AUTH_003")) {
    return "Your session has ended. Please sign in again.";
  }
  if (error instanceof ApiResponseError && error.response.status === 409) {
    if (action === "publish") {
      return "This job is already published.";
    }
    if (action === "disable") {
      return "This job is already disabled.";
    }
    return "This job cannot be reactivated right now. It may have been blocked by moderation.";
  }
  return "Action failed. Please try again.";
}

function formError(error: unknown): string {
  if (error instanceof Error && error.message.includes("ERR_AUTH_003")) {
    return "Your session has ended. Please sign in again.";
  }
  if (error instanceof ApiResponseError && error.response.status === 409) {
    return "This job is disabled. Reactivate it to make edits.";
  }
  return "Could not save the job. Please try again.";
}

export default function JobEditor({ mode }: Props) {
  const navigate = useNavigate();
  const { jobId } = useParams();
  const isCreate = mode === "create";
  const [job, setJob] = useState<JobDetail | null>(null);
  const [loading, setLoading] = useState(!isCreate);
  const [saving, setSaving] = useState(false);
  const [statusLoading, setStatusLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [statusError, setStatusError] = useState<string | null>(null);
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [location, setLocation] = useState("");
  const [remoteEligible, setRemoteEligible] = useState(false);
  const [minExperienceYears, setMinExperienceYears] = useState("");

  const isDisabled = job?.status === "DISABLED";

  useEffect(() => {
    if (isCreate) {
      return;
    }
    if (!jobId) {
      setError("Job not found.");
      setLoading(false);
      return;
    }

    let cancelled = false;
    const loadJob = async () => {
      setLoading(true);
      setError(null);
      try {
        const detail = await getEmployerJob(jobId);
        if (cancelled) {
          return;
        }
        setJob(detail);
        setTitle(detail.title);
        setDescription(detail.description);
        setLocation(detail.location ?? "");
        setRemoteEligible(Boolean(detail.remoteEligible));
        setMinExperienceYears(detail.minExperienceYears == null ? "" : String(detail.minExperienceYears));
      } catch (err) {
        if (!cancelled) {
          const msg = err instanceof Error && err.message.includes("ERR_AUTH_003")
            ? "Your session has ended. Please sign in again."
            : "Could not load this job.";
          setError(msg);
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    };

    void loadJob();
    return () => {
      cancelled = true;
    };
  }, [isCreate, jobId]);

  const header = useMemo(() => (isCreate ? "Create job" : "Edit job"), [isCreate]);

  const saveJob = async (event: FormEvent) => {
    event.preventDefault();
    setStatusError(null);
    setError(null);

    const trimmedTitle = title.trim();
    const trimmedDescription = description.trim();
    const trimmedLocation = location.trim();
    const parsedExperience = minExperienceYears.trim() === "" ? null : Number(minExperienceYears);

    if (!trimmedTitle) {
      setError("Job title is required.");
      return;
    }
    if (trimmedTitle.length > 200) {
      setError("Job title must be 200 characters or fewer.");
      return;
    }
    if (!trimmedDescription) {
      setError("Job description is required.");
      return;
    }
    if (trimmedLocation.length > 150) {
      setError("Location must be 150 characters or fewer.");
      return;
    }
    if (parsedExperience !== null && (Number.isNaN(parsedExperience) || parsedExperience < 0 || parsedExperience > 60)) {
      setError("Minimum years of experience must be between 0 and 60.");
      return;
    }

    setSaving(true);
    try {
      const payload = {
        title: trimmedTitle,
        description: trimmedDescription,
        location: trimmedLocation || null,
        remoteEligible,
        minExperienceYears: parsedExperience,
      };
      const result = isCreate
        ? await createEmployerJob(payload)
        : await updateEmployerJob(jobId!, payload);

      setJob(result);
      setTitle(result.title);
      setDescription(result.description);
      setLocation(result.location ?? "");
      setRemoteEligible(Boolean(result.remoteEligible));
      setMinExperienceYears(result.minExperienceYears == null ? "" : String(result.minExperienceYears));

      if (isCreate) {
        navigate(`/employer/jobs/${result.id}`, { replace: true });
      }
    } catch (err) {
      setError(formError(err));
    } finally {
      setSaving(false);
    }
  };

  const runLifecycleAction = async (action: "publish" | "disable" | "reactivate") => {
    if (!job) {
      return;
    }
    setStatusLoading(true);
    setStatusError(null);
    try {
      const updated =
        action === "publish"
          ? await publishEmployerJob(job.id)
          : action === "disable"
            ? await disableEmployerJob(job.id)
            : await reactivateEmployerJob(job.id);
      setJob(updated);
      setTitle(updated.title);
      setDescription(updated.description);
      setLocation(updated.location ?? "");
      setRemoteEligible(Boolean(updated.remoteEligible));
      setMinExperienceYears(updated.minExperienceYears == null ? "" : String(updated.minExperienceYears));
    } catch (err) {
      setStatusError(lifecycleMessage(action, err));
    } finally {
      setStatusLoading(false);
    }
  };

  return (
    <main style={{ maxWidth: 980, margin: "0 auto", padding: "2rem 1.5rem 3rem" }}>
      <div style={{ display: "flex", alignItems: "flex-end", justifyContent: "space-between", gap: "1rem", marginBottom: "1.5rem" }}>
        <div>
          <Link to="/employer" style={{ color: "var(--ink-muted)", fontSize: "0.875rem" }}>
            ← Back to dashboard
          </Link>
          <h1 style={{ marginTop: "0.75rem" }}>{header}</h1>
          <p style={{ color: "var(--ink-muted)", maxWidth: 60 * 10 }}>
            Keep the description concrete. Candidates only see what you write here.
          </p>
        </div>
        {!isCreate && job && <Badge tone={statusTone(job.status)}>{job.status}</Badge>}
      </div>

      {loading ? (
        <div style={{ display: "flex", justifyContent: "center", padding: "4rem 0" }}>
          <Spinner size={32} />
        </div>
      ) : error ? (
        <Alert tone={error.includes("session") ? "info" : "error"}>{error}</Alert>
      ) : (
        <Card>
          {isDisabled && (
            <div style={{ marginBottom: "1.25rem" }}>
              <Alert tone="info">This job is disabled. Reactivate it to make edits.</Alert>
            </div>
          )}

          <form onSubmit={(event) => void saveJob(event)} noValidate>
            <div style={{ display: "grid", gap: "1rem" }}>
              {error && <Alert tone="error">{error}</Alert>}

              <Input
                label="Job title"
                value={title}
                onChange={(event) => setTitle(event.target.value)}
                maxLength={200}
                required
                disabled={isDisabled}
                placeholder="Senior account manager"
              />

              <div style={{ display: "flex", flexDirection: "column", gap: "0.375rem" }}>
                <label htmlFor="job-description" style={{ fontSize: "0.8125rem", fontWeight: 500, color: "var(--ink-2)", letterSpacing: "0.02em" }}>
                  Job description
                </label>
                <textarea
                  id="job-description"
                  value={description}
                  onChange={(event) => setDescription(event.target.value)}
                  rows={8}
                  required
                  disabled={isDisabled}
                  maxLength={6000}
                  placeholder="Describe the role, responsibilities, and the kind of person who will do well here."
                  style={{
                    padding: "0.75rem 0.875rem",
                    borderRadius: "var(--radius-sm)",
                    border: "1.5px solid var(--border)",
                    background: "var(--bg-card)",
                    color: "var(--ink)",
                    fontSize: "0.9375rem",
                    resize: "vertical",
                    outline: "none",
                  }}
                />
              </div>

              <Input
                label="Location"
                value={location}
                onChange={(event) => setLocation(event.target.value)}
                maxLength={150}
                disabled={isDisabled}
                placeholder="Chicago, IL"
              />

              <label style={{ display: "flex", alignItems: "center", gap: "0.75rem", fontSize: "0.9375rem", color: "var(--ink-2)" }}>
                <input
                  type="checkbox"
                  checked={remoteEligible}
                  onChange={(event) => setRemoteEligible(event.target.checked)}
                  disabled={isDisabled}
                />
                Remote eligible
              </label>

              <Input
                label="Minimum years of experience"
                type="number"
                min={0}
                max={60}
                value={minExperienceYears}
                onChange={(event) => setMinExperienceYears(event.target.value)}
                disabled={isDisabled}
                placeholder="3"
              />
            </div>

            <div style={{ display: "flex", justifyContent: "flex-end", gap: "0.75rem", marginTop: "1.5rem" }}>
              <Link to="/employer" style={{ alignSelf: "center", color: "var(--ink-muted)" }}>
                Cancel
              </Link>
              <Button type="submit" loading={saving} disabled={isDisabled}>
                {isCreate ? "Create job" : "Save changes"}
              </Button>
            </div>
          </form>

          {!isCreate && (
            <>
              <Divider label="Status actions" />
              <div style={{ display: "flex", flexDirection: "column", gap: "0.75rem", marginTop: "1rem" }}>
                {statusError && <Alert tone="error">{statusError}</Alert>}
                <div style={{ display: "flex", flexWrap: "wrap", gap: "0.75rem" }}>
                  {job?.status === "DRAFT" && (
                    <Button loading={statusLoading} onClick={() => void runLifecycleAction("publish")}>
                      Publish
                    </Button>
                  )}
                  {job?.status === "ACTIVE" && (
                    <Button loading={statusLoading} variant="secondary" onClick={() => void runLifecycleAction("disable")}>
                      Disable
                    </Button>
                  )}
                  {job?.status === "DISABLED" && (
                    <Button loading={statusLoading} variant="secondary" onClick={() => void runLifecycleAction("reactivate")}>
                      Reactivate
                    </Button>
                  )}
                </div>
              </div>
            </>
          )}
        </Card>
      )}
    </main>
  );
}