import { FormEvent, useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import {
  createEmployerJob,
  EDUCATION_LABELS,
  getEmployerJob,
  publishEmployerJob,
  disableEmployerJob,
  reactivateEmployerJob,
  updateEmployerJob,
} from "../api/employer";
import type { EducationRequirement, JobDetail, JobStatus } from "../api/employer";
import { ApiResponseError } from "../api/client";
import { Alert, Badge, Button, Card, Divider, Input, Spinner } from "../components/ui";
import JobSkillsEditor from "../components/JobSkillsEditor";
import PageLoader from "../components/PageLoader";

type Props = {
  mode: "create" | "edit";
};

const EDUCATION_OPTIONS: Array<{ value: EducationRequirement; label: string }> = [
  { value: "HIGH_SCHOOL", label: EDUCATION_LABELS.HIGH_SCHOOL },
  { value: "BACHELORS", label: EDUCATION_LABELS.BACHELORS },
  { value: "MASTERS", label: EDUCATION_LABELS.MASTERS },
  { value: "PHD", label: EDUCATION_LABELS.PHD },
];

function statusTone(status: JobStatus): "neutral" | "success" | "warn" {
  if (status === "ACTIVE") return "success";
  if (status === "DISABLED") return "warn";
  return "neutral";
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

  // Core fields
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [companyName, setCompanyName] = useState("");
  const [location, setLocation] = useState("");
  const [remoteEligible, setRemoteEligible] = useState(false);
  const [minExperienceYears, setMinExperienceYears] = useState("");

  // New fields
  const [salaryMin, setSalaryMin] = useState("");
  const [salaryMax, setSalaryMax] = useState("");
  const [educationRequirement, setEducationRequirement] = useState<
    EducationRequirement | ""
  >("");

  const isDisabled = job?.status === "DISABLED";

  // ── Load existing job ────────────────────────────────────────────────────
  useEffect(() => {
    if (isCreate) return;
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
        if (cancelled) return;
        setJob(detail);
        setTitle(detail.title);
        setDescription(detail.description);
        setCompanyName(detail.companyName ?? "");
        setLocation(detail.location ?? "");
        setRemoteEligible(Boolean(detail.remoteEligible));
        setMinExperienceYears(
          detail.minExperienceYears == null ? "" : String(detail.minExperienceYears)
        );
        setSalaryMin(detail.salaryMin == null ? "" : String(detail.salaryMin));
        setSalaryMax(detail.salaryMax == null ? "" : String(detail.salaryMax));
        setEducationRequirement(detail.educationRequirement ?? "");
      } catch (err) {
        if (!cancelled) {
          const msg =
            err instanceof Error && err.message.includes("ERR_AUTH_003")
              ? "Your session has ended. Please sign in again."
              : "Could not load this job.";
          setError(msg);
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    };

    void loadJob();
    return () => {
      cancelled = true;
    };
  }, [isCreate, jobId]);

  const header = useMemo(() => (isCreate ? "Create job" : "Edit job"), [isCreate]);

  useEffect(() => {
    document.title = isCreate ? "Create Job - JobVault" : "Edit Job - JobVault";
  }, [isCreate]);

  // ── Sync job state into form fields after lifecycle actions ──────────────
  const applyJobToForm = (detail: JobDetail) => {
    setJob(detail);
    setTitle(detail.title);
    setDescription(detail.description);
    setCompanyName(detail.companyName ?? "");
    setLocation(detail.location ?? "");
    setRemoteEligible(Boolean(detail.remoteEligible));
    setMinExperienceYears(
      detail.minExperienceYears == null ? "" : String(detail.minExperienceYears)
    );
    setSalaryMin(detail.salaryMin == null ? "" : String(detail.salaryMin));
    setSalaryMax(detail.salaryMax == null ? "" : String(detail.salaryMax));
    setEducationRequirement(detail.educationRequirement ?? "");
  };

  // ── Save ─────────────────────────────────────────────────────────────────
  const saveJob = async (event: FormEvent) => {
    event.preventDefault();
    setStatusError(null);
    setError(null);

    const trimmedTitle = title.trim();
    const trimmedDescription = description.trim();
    const trimmedCompanyName = companyName.trim();
    const trimmedLocation = location.trim();
    const parsedExperience =
      minExperienceYears.trim() === "" ? null : Number(minExperienceYears);
    const parsedSalaryMin = salaryMin.trim() === "" ? null : Number(salaryMin);
    const parsedSalaryMax = salaryMax.trim() === "" ? null : Number(salaryMax);

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
    if (trimmedCompanyName.length > 200) {
      setError("Company name must be 200 characters or fewer.");
      return;
    }
    if (trimmedLocation.length > 150) {
      setError("Location must be 150 characters or fewer.");
      return;
    }
    if (
      parsedExperience !== null &&
      (Number.isNaN(parsedExperience) || parsedExperience < 0 || parsedExperience > 60)
    ) {
      setError("Minimum years of experience must be between 0 and 60.");
      return;
    }
    if (parsedSalaryMin !== null && (Number.isNaN(parsedSalaryMin) || parsedSalaryMin < 0)) {
      setError("Minimum salary must be a positive number.");
      return;
    }
    if (parsedSalaryMax !== null && (Number.isNaN(parsedSalaryMax) || parsedSalaryMax < 0)) {
      setError("Maximum salary must be a positive number.");
      return;
    }
    if (
      parsedSalaryMin !== null &&
      parsedSalaryMax !== null &&
      parsedSalaryMax < parsedSalaryMin
    ) {
      setError("Maximum salary must be greater than or equal to minimum salary.");
      return;
    }

    setSaving(true);
    try {
      const payload = {
        title: trimmedTitle,
        description: trimmedDescription,
        companyName: trimmedCompanyName || null,
        location: trimmedLocation || null,
        remoteEligible,
        minExperienceYears: parsedExperience,
        salaryMin: parsedSalaryMin,
        salaryMax: parsedSalaryMax,
        educationRequirement: (educationRequirement || null) as EducationRequirement | null,
      };
      const result = isCreate
        ? await createEmployerJob(payload)
        : await updateEmployerJob(jobId!, payload);

      applyJobToForm(result);

      if (isCreate) {
        navigate(`/employer/jobs/${result.id}`, { replace: true });
      }
    } catch (err) {
      setError(formError(err));
    } finally {
      setSaving(false);
    }
  };

  // ── Lifecycle actions ────────────────────────────────────────────────────
  const runLifecycleAction = async (
    action: "publish" | "disable" | "reactivate"
  ) => {
    if (!job) return;
    setStatusLoading(true);
    setStatusError(null);
    try {
      const updated =
        action === "publish"
          ? await publishEmployerJob(job.id)
          : action === "disable"
            ? await disableEmployerJob(job.id)
            : await reactivateEmployerJob(job.id);
      applyJobToForm(updated);
    } catch (err) {
      setStatusError(lifecycleMessage(action, err));
    } finally {
      setStatusLoading(false);
    }
  };

  // ── Render ───────────────────────────────────────────────────────────────
  return (
    <main style={{ maxWidth: "var(--page-max-width)", margin: "0 auto", padding: "2rem 1.5rem 3rem" }}>
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
          <Link to="/employer" style={{ color: "var(--ink-muted)", fontSize: "0.875rem" }}>
            ← Back to dashboard
          </Link>
          <h1 style={{ marginTop: "0.75rem" }}>{header}</h1>
          <p style={{ color: "var(--ink-muted)", maxWidth: 600 }}>
            Keep the description concrete. Candidates only see what you write here.
          </p>
        </div>
        {!isCreate && job && (
          <div style={{ display: "flex", flexDirection: "column", alignItems: "flex-end", gap: "0.5rem" }}>
            <Badge tone={statusTone(job.status)}>{job.status}</Badge>
            <Link to={`/jobs/${job.id}`} style={{ color: "var(--ink-muted)", fontSize: "0.8125rem" }}>
              View job page →
            </Link>
          </div>
        )}
      </div>

      {loading ? (
        <PageLoader />
      ) : error && !job ? (
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

              {/* ── Core fields ── */}
              <Input
                label="Job title"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                maxLength={200}
                required
                disabled={isDisabled}
                placeholder="Senior backend engineer"
              />

              <Input
                label="Company name"
                value={companyName}
                onChange={(e) => setCompanyName(e.target.value)}
                maxLength={200}
                disabled={isDisabled}
                placeholder="Acme Corp"
              />

              <div style={{ display: "flex", flexDirection: "column", gap: "0.375rem" }}>
                <label
                  htmlFor="job-description"
                  style={{
                    fontSize: "0.8125rem",
                    fontWeight: 500,
                    color: "var(--ink-2)",
                    letterSpacing: "0.02em",
                  }}
                >
                  Job description
                </label>
                <textarea
                  id="job-description"
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
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
                    fontFamily: "var(--font-body)",
                  }}
                />
              </div>

              {/* ── Location & remote ── */}
              <Input
                label="Location"
                value={location}
                onChange={(e) => setLocation(e.target.value)}
                maxLength={150}
                disabled={isDisabled}
                placeholder="Chicago, IL"
              />

              <label
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: "0.75rem",
                  fontSize: "0.9375rem",
                  color: "var(--ink-2)",
                }}
              >
                <input
                  type="checkbox"
                  checked={remoteEligible}
                  onChange={(e) => setRemoteEligible(e.target.checked)}
                  disabled={isDisabled}
                  style={{ accentColor: "var(--accent)" }}
                />
                Remote eligible
              </label>

              {/* ── Salary range ── */}
              <div style={{ display: "flex", flexDirection: "column", gap: "0.375rem" }}>
                <span
                  style={{
                    fontSize: "0.8125rem",
                    fontWeight: 500,
                    color: "var(--ink-2)",
                    letterSpacing: "0.02em",
                  }}
                >
                  Salary range (USD)
                </span>
                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0.75rem" }}>
                  <div style={{ display: "flex", flexDirection: "column", gap: "0.25rem" }}>
                    <label
                      htmlFor="salary-min"
                      style={{ fontSize: "0.75rem", color: "var(--ink-muted)" }}
                    >
                      Minimum
                    </label>
                    <input
                      id="salary-min"
                      type="number"
                      min={0}
                      value={salaryMin}
                      onChange={(e) => setSalaryMin(e.target.value)}
                      disabled={isDisabled}
                      placeholder="80000"
                      style={{
                        padding: "0.625rem 0.875rem",
                        borderRadius: "var(--radius-sm)",
                        border: "1.5px solid var(--border)",
                        background: "var(--bg-card)",
                        color: "var(--ink)",
                        fontSize: "0.9375rem",
                        fontFamily: "var(--font-body)",
                        outline: "none",
                        width: "100%",
                      }}
                    />
                  </div>
                  <div style={{ display: "flex", flexDirection: "column", gap: "0.25rem" }}>
                    <label
                      htmlFor="salary-max"
                      style={{ fontSize: "0.75rem", color: "var(--ink-muted)" }}
                    >
                      Maximum
                    </label>
                    <input
                      id="salary-max"
                      type="number"
                      min={0}
                      value={salaryMax}
                      onChange={(e) => setSalaryMax(e.target.value)}
                      disabled={isDisabled}
                      placeholder="120000"
                      style={{
                        padding: "0.625rem 0.875rem",
                        borderRadius: "var(--radius-sm)",
                        border: "1.5px solid var(--border)",
                        background: "var(--bg-card)",
                        color: "var(--ink)",
                        fontSize: "0.9375rem",
                        fontFamily: "var(--font-body)",
                        outline: "none",
                        width: "100%",
                      }}
                    />
                  </div>
                </div>
                <span style={{ fontSize: "0.75rem", color: "var(--ink-muted)" }}>
                  Leave blank if you prefer not to disclose salary.
                </span>
              </div>

              {/* ── Experience & education ── */}
              <div
                style={{
                  display: "grid",
                  gridTemplateColumns: "1fr 1fr",
                  gap: "0.75rem",
                }}
              >
                <Input
                  label="Min. years of experience"
                  type="number"
                  min={0}
                  max={60}
                  value={minExperienceYears}
                  onChange={(e) => setMinExperienceYears(e.target.value)}
                  disabled={isDisabled}
                  placeholder="3"
                />

                <div style={{ display: "flex", flexDirection: "column", gap: "0.375rem" }}>
                  <label
                    htmlFor="education-requirement"
                    style={{
                      fontSize: "0.8125rem",
                      fontWeight: 500,
                      color: "var(--ink-2)",
                      letterSpacing: "0.02em",
                    }}
                  >
                    Education requirement
                  </label>
                  <select
                    id="education-requirement"
                    value={educationRequirement}
                    onChange={(e) =>
                      setEducationRequirement(e.target.value as EducationRequirement | "")
                    }
                    disabled={isDisabled}
                    style={{
                      padding: "0.625rem 0.875rem",
                      borderRadius: "var(--radius-sm)",
                      border: "1.5px solid var(--border)",
                      background: "var(--bg-card)",
                      color: educationRequirement ? "var(--ink)" : "var(--ink-faint)",
                      fontSize: "0.9375rem",
                      fontFamily: "var(--font-body)",
                      outline: "none",
                      width: "100%",
                      appearance: "none",
                      cursor: isDisabled ? "not-allowed" : "pointer",
                    }}
                  >
                    <option value="">Not specified</option>
                    {EDUCATION_OPTIONS.map((opt) => (
                      <option key={opt.value} value={opt.value}>
                        {opt.label}
                      </option>
                    ))}
                  </select>
                </div>
              </div>
            </div>

            <div
              style={{
                display: "flex",
                justifyContent: "flex-end",
                gap: "0.75rem",
                marginTop: "1.5rem",
              }}
            >
              <Link
                to="/employer"
                style={{ alignSelf: "center", color: "var(--ink-muted)" }}
              >
                Cancel
              </Link>
              <Button type="submit" loading={saving} disabled={isDisabled}>
                {isCreate ? "Create job" : "Save changes"}
              </Button>
            </div>
          </form>

          {/* ── Required skills (editable) ── */}
          {!isCreate && job && (
            <>
              <Divider label="Required skills" />
              <div style={{ marginTop: "1rem" }}>
                <JobSkillsEditor job={job} onUpdate={applyJobToForm} />
              </div>
            </>
          )}

          {!isCreate && (
            <>
              <Divider label="Status actions" />
              <div
                style={{
                  display: "flex",
                  flexDirection: "column",
                  gap: "0.75rem",
                  marginTop: "1rem",
                }}
              >
                {statusError && <Alert tone="error">{statusError}</Alert>}
                <div style={{ display: "flex", flexWrap: "wrap", gap: "0.75rem" }}>
                  {job?.status === "DRAFT" && (
                    <Button
                      loading={statusLoading}
                      onClick={() => void runLifecycleAction("publish")}
                    >
                      Publish
                    </Button>
                  )}
                  {job?.status === "ACTIVE" && (
                    <Button
                      loading={statusLoading}
                      variant="secondary"
                      onClick={() => void runLifecycleAction("disable")}
                    >
                      Disable
                    </Button>
                  )}
                  {job?.status === "DISABLED" && (
                    <Button
                      loading={statusLoading}
                      variant="secondary"
                      onClick={() => void runLifecycleAction("reactivate")}
                    >
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
