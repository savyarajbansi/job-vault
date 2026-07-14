import { useState, useEffect, useCallback } from "react";
import { Link } from "react-router-dom";
import {
  getSeekerMatches,
  getSeekerSkillGaps,
  getMyApplications,
} from "../api/seeker";
import type {
  SeekerJobMatchResponse,
  SeekerMatchItem,
  SeekerApplicationItem,
} from "../api/seeker";
import { formatSalaryRange, EDUCATION_LABELS } from "../api/employer";
import { ApiResponseError } from "../api/client";
import { Button, Alert, Card, Badge, Spinner, ScoreBar, Icon } from "../components/ui";
import ApplicationStatusBadge from "../components/ApplicationStatusBadge";
import PageLoader from "../components/PageLoader";
import useApplicationAction from "../hooks/useApplicationAction";
import { formatScore } from "../utils/score";

const MATCHES_PER_PAGE = 10;

function scoreColor(score: number): string {
  if (score >= 0.7) return "var(--success)";
  if (score >= 0.4) return "#B07D20";
  return "var(--ink-muted)";
}

function MatchFactorBar({
  value,
  available,
  label,
}: {
  value: number;
  available: boolean;
  label: string;
}) {
  return available ? (
    <ScoreBar value={value} label={label} />
  ) : (
    <div style={{ color: "var(--ink-muted)", fontSize: "0.875rem" }}>
      {label}: <span style={{ color: "var(--ink-faint)" }}>Not applicable</span>
    </div>
  );
}

export default function SeekerMatches() {
  const [matches, setMatches] = useState<SeekerJobMatchResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);

  const [selected, setSelected] = useState<SeekerMatchItem | null>(null);
  const [gaps, setGaps] = useState<string[] | null>(null);
  const [gapsLoading, setGapsLoading] = useState(false);
  const [gapsError, setGapsError] = useState<string | null>(null);

  const [applicationsByJobId, setApplicationsByJobId] = useState<Record<string, SeekerApplicationItem>>({});

  const loadApplications = useCallback(async (): Promise<boolean> => {
    try {
      const items = await getMyApplications();
      const byJobId: Record<string, SeekerApplicationItem> = {};
      for (const item of items) {
        if (item.jobId) {
          byJobId[item.jobId] = item;
        }
      }
      setApplicationsByJobId(byJobId);
      return true;
    } catch {
      // Non-fatal — matches still render without application status.
      return false;
    }
  }, []);

  const loadMatches = useCallback(async (offset: number) => {
    setLoading(true);
    setError(null);
    try {
      const result = await getSeekerMatches({ limit: MATCHES_PER_PAGE, offset });
      setMatches(result);
      setSelected((current) => current ?? result.items[0] ?? null);
    } catch (err) {
      if (err instanceof Error && err.message.includes("ERR_AUTH_003")) {
        setError("Your session has ended. Please sign in again.");
      } else if (err instanceof ApiResponseError && err.response.status === 404) {
        setError("Upload a parsed resume first to see your matches.");
      } else {
        setError("Could not load matches. Please try again.");
      }
    } finally {
      setLoading(false);
    }
  }, []);

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
    void loadMatches(page * MATCHES_PER_PAGE);
  }, [loadMatches, page]);

  useEffect(() => {
    void loadApplications();
  }, [loadApplications]);

  const total = matches?.page.total ?? 0;
  const totalPages = Math.ceil(total / MATCHES_PER_PAGE);
  const hasNextPage =
    matches !== null && matches.items.length === MATCHES_PER_PAGE && page < totalPages - 1;
  const selectedSalaryLabel = selected
    ? formatSalaryRange(selected.job.salaryMin, selected.job.salaryMax)
    : null;
  const selectedApplication = selected ? applicationsByJobId[selected.jobId] : undefined;

  const { actionState, actionError, clearActionError, runAction } = useApplicationAction({
    jobId: selected?.jobId,
    application: selectedApplication,
    reloadApplications: loadApplications,
  });

  useEffect(() => {
    if (selected) void loadGaps(selected.jobId);
    clearActionError();
  }, [selected, clearActionError]);

  useEffect(() => {
    document.title = "Job Matches - JobVault";
  }, []);

  return (
    <main className="page seeker-page">
      <div className="page-header">
        <div className="page-header__copy">
          <h1>Job Matches</h1>
          <p className="page-header__subtitle">
            Ranked by resume content, required skills, experience, and location when those signals are available.
          </p>
        </div>
      </div>

      {error && (
        <Alert tone={error.includes("resume") ? "info" : "error"}>{error}</Alert>
      )}

      {loading && (
        <PageLoader />
      )}

      {!loading && !error && matches && (
        <div className="seeker-matches__content">
          {/* ── Match list ── */}
          <div style={{ display: "flex", flexDirection: "column", gap: "0.75rem" }}>
            {matches.items.length === 0 && (
              <p style={{ color: "var(--ink-muted)" }}>No matches found for your current resume.</p>
            )}

            {matches.items.map((item, idx) => {
              const isSelected = selected?.jobId === item.jobId;
              const salaryLabel = formatSalaryRange(item.job.salaryMin, item.job.salaryMax);
              const existingApplication = applicationsByJobId[item.jobId];
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
                      <div style={{ fontWeight: 500, color: "var(--ink)", fontSize: "0.9375rem", marginBottom: "0.125rem" }}>
                        {item.job.title}
                      </div>
                      {item.job.companyName && (
                        <div style={{ fontSize: "0.8125rem", color: "var(--ink-2)", marginBottom: "0.4rem" }}>
                          {item.job.companyName}
                        </div>
                      )}
                      <div style={{ display: "flex", flexWrap: "wrap", gap: "0.5rem", alignItems: "center" }}>
                        {existingApplication && (
                          <ApplicationStatusBadge status={existingApplication.status} />
                        )}
                        {item.job.location && <Badge tone="neutral">{item.job.location}</Badge>}
                        {item.job.remoteEligible && (
                          <Badge tone="accent">Remote eligible</Badge>
                        )}
                        {salaryLabel && <Badge tone="neutral">{salaryLabel}</Badge>}
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
                        fontWeight: 600,
                        color: scoreColor(item.score),
                        flexShrink: 0,
                      }}
                    >
                      {formatScore(item.score)}
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
                  disabled={!hasNextPage}
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
          <div
            className="seeker-matches__detail"
            style={{
              position: "sticky",
              top: 72,
              maxHeight: "calc(100vh - 88px)",
              overflowY: "auto",
            }}
          >
            {selected ? (
              <Card style={{ display: "flex", flexDirection: "column", gap: "1.25rem" }}>
                {/* Header */}
                <div>
                  <div style={{ fontWeight: 600, fontSize: "1.0625rem", color: "var(--ink)", marginBottom: "0.25rem" }}>
                    {selected.job.title}
                  </div>
                  {selected.job.companyName && (
                    <div style={{ fontSize: "0.875rem", color: "var(--ink-2)", marginBottom: "0.375rem" }}>
                      {selected.job.companyName}
                    </div>
                  )}
                  <Link
                    to={`/jobs/${selected.jobId}`}
                    style={{ display: "inline-block", fontSize: "0.8125rem", fontWeight: 500, marginBottom: "0.625rem" }}
                  >
                    View full job posting →
                  </Link>
                  <div style={{ display: "flex", flexWrap: "wrap", gap: "0.4rem", marginBottom: "0.75rem" }}>
                    {selected.job.location && <Badge tone="neutral">{selected.job.location}</Badge>}
                    {selectedSalaryLabel && <Badge tone="neutral">{selectedSalaryLabel}</Badge>}
                    {selected.job.educationRequirement && (
                      <Badge tone="neutral">{EDUCATION_LABELS[selected.job.educationRequirement]}</Badge>
                    )}
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
                        fontWeight: 600,
                        color: scoreColor(selected.score),
                      }}
                    >
                      {formatScore(selected.score)}
                    </span>
                    <span style={{ fontSize: "0.8125rem", color: "var(--ink-muted)" }}>overall fit</span>
                  </div>
                </div>

                {/* ── Application actions ── */}
                <div
                  style={{
                    display: "flex",
                    flexDirection: "column",
                    gap: "0.625rem",
                    padding: "0.875rem",
                    background: "var(--bg-subtle)",
                    borderRadius: "var(--radius-sm)",
                  }}
                >
                  {actionError && <Alert tone="error">{actionError}</Alert>}

                  {!selectedApplication && (
                    <>
                      <p style={{ fontSize: "0.8125rem", color: "var(--ink-muted)" }}>
                        You haven't applied to this job yet.
                      </p>
                      <div style={{ display: "flex", gap: "0.5rem", flexWrap: "wrap" }}>
                        <Button
                          size="sm"
                          loading={actionState === "applying"}
                          disabled={actionState !== "idle"}
                          onClick={() => void runAction("apply")}
                        >
                          Apply now
                        </Button>
                        <Button
                          size="sm"
                          variant="secondary"
                          loading={actionState === "drafting"}
                          disabled={actionState !== "idle"}
                          onClick={() => void runAction("draft")}
                        >
                          Save as draft
                        </Button>
                      </div>
                    </>
                  )}

                  {selectedApplication && selectedApplication.status === "DRAFT" && (
                    <>
                      <div style={{ display: "flex", alignItems: "center", gap: "0.5rem" }}>
                        <ApplicationStatusBadge status={selectedApplication.status} />
                        <span style={{ fontSize: "0.8125rem", color: "var(--ink-muted)" }}>
                          Not yet submitted to the employer.
                        </span>
                      </div>
                      <Button
                        size="sm"
                        loading={actionState === "applying"}
                        disabled={actionState !== "idle"}
                        onClick={() => void runAction("apply")}
                      >
                        Submit application
                      </Button>
                    </>
                  )}

                  {selectedApplication &&
                    (selectedApplication.status === "SUBMITTED" ||
                      selectedApplication.status === "UNDER_REVIEW") && (
                      <>
                        <div style={{ display: "flex", alignItems: "center", gap: "0.5rem" }}>
                          <ApplicationStatusBadge status={selectedApplication.status} />
                        </div>
                        <Button
                          size="sm"
                          variant="secondary"
                          loading={actionState === "withdrawing"}
                          disabled={actionState !== "idle"}
                          onClick={() => void runAction("withdraw")}
                        >
                          Withdraw application
                        </Button>
                      </>
                    )}

                  {selectedApplication &&
                    (selectedApplication.status === "ACCEPTED" ||
                      selectedApplication.status === "REJECTED" ||
                      selectedApplication.status === "WITHDRAWN") && (
                      <div style={{ display: "flex", alignItems: "center", gap: "0.5rem" }}>
                        <ApplicationStatusBadge status={selectedApplication.status} />
                        <span style={{ fontSize: "0.8125rem", color: "var(--ink-muted)" }}>
                          This application is closed.
                        </span>
                      </div>
                    )}
                </div>

                {/* Score breakdown */}
                <div style={{ display: "flex", flexDirection: "column", gap: "0.625rem" }}>
                  <h4 style={{ fontSize: "0.8125rem", textTransform: "uppercase", letterSpacing: "0.08em", color: "var(--ink-muted)", marginBottom: "0.125rem" }}>
                    Match factors
                  </h4>
                  <MatchFactorBar value={selected.factors.cosine} available={selected.factors.cosineAvailable} label="Content similarity" />
                  <MatchFactorBar value={selected.factors.skillsOverlap} available={selected.factors.skillsAvailable} label="Skills overlap" />
                  <MatchFactorBar value={selected.factors.experience} available={selected.factors.experienceAvailable} label="Experience" />
                  <MatchFactorBar value={selected.factors.location} available={selected.factors.locationAvailable} label="Location" />
                </div>

                {/* Required skills */}
                {selected.job.requiredSkills.length > 0 && (
                  <div>
                    <h4 style={{ fontSize: "0.8125rem", textTransform: "uppercase", letterSpacing: "0.08em", color: "var(--ink-muted)", marginBottom: "0.625rem" }}>
                      Required skills
                    </h4>
                    <div style={{ display: "flex", flexWrap: "wrap", gap: "0.375rem" }}>
                      {selected.job.requiredSkills.map((skill) => {
                        const isMissing = selected.missingSkills.includes(skill);
                        return (
                          <span
                            key={skill}
                            style={{
                              padding: "0.25rem 0.625rem",
                              background: isMissing ? "var(--warn-faint)" : "var(--success-faint)",
                              color: isMissing ? "var(--warn)" : "var(--success)",
                              borderRadius: "999px",
                              fontSize: "0.8125rem",
                              fontWeight: 500,
                            }}
                          >
                            {skill}
                          </span>
                        );
                      })}
                    </div>
                  </div>
                )}

                {/* Skill gaps */}
                <div>
                  <h4 style={{ fontSize: "0.8125rem", textTransform: "uppercase", letterSpacing: "0.08em", color: "var(--ink-muted)", marginBottom: "0.625rem" }}>
                    Skill gaps
                  </h4>
                  {gapsLoading && <Spinner size={18} />}
                  {gapsError && <p style={{ fontSize: "0.875rem", color: "var(--warn)" }}>{gapsError}</p>}
                  {gaps !== null && !gapsLoading && gaps.length === 0 && (
                    <div style={{ display: "flex", alignItems: "center", gap: "0.5rem" }}>
                      <span
                        style={{
                          width: 24,
                          height: 24,
                          borderRadius: "999px",
                          background: "var(--success-faint)",
                          color: "var(--success)",
                          display: "inline-flex",
                          alignItems: "center",
                          justifyContent: "center",
                          flexShrink: 0,
                        }}
                      >
                        <Icon name="check" size={14} />
                      </span>
                      <span style={{ fontSize: "0.875rem", color: "var(--success)" }}>
                        No skill gaps - strong match!
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
                    Remote eligible
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
