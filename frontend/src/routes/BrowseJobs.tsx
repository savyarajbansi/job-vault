import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";

import { getPublicJobs, getTrendingSkills } from "../api/jobs";
import type { TrendingSkill } from "../api/jobs";
import { formatSalaryRange } from "../api/employer";
import type { JobSummary } from "../api/employer";
import { useAuth } from "../api/authContext";
import { Alert, Badge, Card, Input, Spinner } from "../components/ui";

function formatRelative(iso: string): string {
  const diffMs = Date.now() - new Date(iso).getTime();
  const diffDays = Math.max(0, Math.round(diffMs / 86400000));
  if (diffDays === 0) return "today";
  if (diffDays === 1) return "1 day ago";
  return `${diffDays} days ago`;
}

function matchesQuery(job: JobSummary, query: string): boolean {
  if (!query.trim()) return true;
  const haystack = [job.title, job.companyName, job.location]
    .filter((value): value is string => Boolean(value))
    .join(" ")
    .toLowerCase();
  return haystack.includes(query.trim().toLowerCase());
}

function TrendingSkillsCard({
  skills,
  loading,
}: {
  skills: TrendingSkill[];
  loading: boolean;
}) {
  return (
    <Card>
      <h2 style={{ fontSize: "1rem", marginBottom: "0.25rem" }}>Trending skills</h2>
      <p style={{ fontSize: "0.8125rem", color: "var(--ink-muted)", marginBottom: "1rem" }}>
        Most in-demand skills across active postings right now.
      </p>
      {loading ? (
        <div style={{ display: "flex", justifyContent: "center", padding: "1rem 0" }}>
          <Spinner size={20} />
        </div>
      ) : skills.length === 0 ? (
        <p style={{ fontSize: "0.875rem", color: "var(--ink-muted)" }}>Not enough data yet.</p>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: "0.5rem" }}>
          {skills.map((skill, index) => (
            <div
              key={skill.skillId}
              style={{
                display: "flex",
                alignItems: "center",
                justifyContent: "space-between",
                gap: "0.75rem",
                padding: "0.5rem 0.625rem",
                background: "var(--bg-subtle)",
                borderRadius: "var(--radius-sm)",
              }}
            >
              <span
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: "0.5rem",
                  fontSize: "0.875rem",
                  color: "var(--ink-2)",
                  minWidth: 0,
                }}
              >
                <span style={{ color: "var(--ink-faint)", fontSize: "0.75rem", fontWeight: 600, flexShrink: 0 }}>
                  {index + 1}
                </span>
                <span style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                  {skill.skillName}
                </span>
              </span>
              <Badge tone="accent">{skill.score.toFixed(1)}</Badge>
            </div>
          ))}
        </div>
      )}
    </Card>
  );
}

export default function BrowseJobs() {
  const { isAuthenticated, isSessionReady, roles } = useAuth();
  const [jobs, setJobs] = useState<JobSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState("");

  const [trendingSkills, setTrendingSkills] = useState<TrendingSkill[]>([]);
  const [trendingLoading, setTrendingLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      setLoading(true);
      setError(null);
      try {
        const result = await getPublicJobs();
        if (!cancelled) {
          setJobs(result);
        }
      } catch {
        if (!cancelled) {
          setError("Could not load open positions. Please try again.");
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
  }, []);

  useEffect(() => {
    let cancelled = false;
    const loadTrending = async () => {
      try {
        const result = await getTrendingSkills();
        if (!cancelled) {
          setTrendingSkills(result);
        }
      } catch {
        // Non-fatal — the listing still works without trending data.
      } finally {
        if (!cancelled) {
          setTrendingLoading(false);
        }
      }
    };
    void loadTrending();
    return () => {
      cancelled = true;
    };
  }, []);

  const filteredJobs = useMemo(
    () => jobs.filter((job) => matchesQuery(job, query)),
    [jobs, query]
  );

  const isSeeker = isAuthenticated && roles.includes("JOB_SEEKER");

  return (
    <main style={{ maxWidth: 1180, margin: "0 auto", padding: "2rem 1.5rem 3rem" }}>
      <div style={{ marginBottom: "1.5rem" }}>
        <h1 style={{ marginBottom: "0.375rem" }}>Open positions</h1>
        <p style={{ color: "var(--ink-muted)" }}>
          Every active role posted on JobVault.{" "}
          {isSessionReady && !isAuthenticated && (
            <>
              <Link to="/auth">Sign in</Link> to get ranked matches and skill-gap analysis from
              your resume.
            </>
          )}
          {isSessionReady && isSeeker && (
            <>
              Want these ranked to your resume? <Link to="/seeker/matches">See your matches</Link>.
            </>
          )}
        </p>
      </div>

      <div
        style={{
          display: "grid",
          gridTemplateColumns: "minmax(0, 1fr) 280px",
          gap: "1.5rem",
          alignItems: "start",
        }}
      >
        <div>
          <div style={{ marginBottom: "1.5rem", maxWidth: 360 }}>
            <Input
              label="Search by title, company, or location"
              type="text"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="e.g. Backend, Acme, Austin"
            />
          </div>

          {error && <Alert tone="error">{error}</Alert>}

          {loading ? (
            <div style={{ display: "flex", justifyContent: "center", padding: "4rem 0" }}>
              <Spinner size={32} />
            </div>
          ) : !error && filteredJobs.length === 0 ? (
            <Card>
              <p style={{ color: "var(--ink-muted)" }}>
                {jobs.length === 0
                  ? "No open positions right now. Check back soon."
                  : "No positions match your search."}
              </p>
            </Card>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: "0.75rem" }}>
              {filteredJobs.map((job) => {
                const salaryLabel = formatSalaryRange(job.salaryMin, job.salaryMax);
                return (
                  <Link
                    key={job.id}
                    to={`/jobs/${job.id}`}
                    style={{
                      display: "block",
                      textDecoration: "none",
                      background: "var(--bg-card)",
                      border: "1.5px solid var(--border)",
                      borderRadius: "var(--radius)",
                      boxShadow: "var(--shadow-sm)",
                      padding: "1.25rem 1.5rem",
                      transition: "border-color 0.15s, box-shadow 0.15s",
                    }}
                    onMouseEnter={(e) => {
                      e.currentTarget.style.borderColor = "var(--accent)";
                      e.currentTarget.style.boxShadow = "var(--shadow)";
                    }}
                    onMouseLeave={(e) => {
                      e.currentTarget.style.borderColor = "var(--border)";
                      e.currentTarget.style.boxShadow = "var(--shadow-sm)";
                    }}
                  >
                    <div style={{ display: "flex", justifyContent: "space-between", gap: "1rem", alignItems: "flex-start" }}>
                      <div style={{ minWidth: 0 }}>
                        <h2 style={{ fontSize: "1.0625rem", margin: 0, color: "var(--ink)" }}>
                          {job.title}
                        </h2>
                        {job.companyName && (
                          <p style={{ fontSize: "0.875rem", color: "var(--ink-2)", fontWeight: 500, margin: "0.2rem 0 0" }}>
                            {job.companyName}
                          </p>
                        )}
                        <div style={{ display: "flex", flexWrap: "wrap", gap: "0.5rem", marginTop: "0.6rem" }}>
                          <Badge tone="neutral">{job.location ?? "Location not set"}</Badge>
                          {job.remoteEligible && <Badge tone="accent">Remote eligible</Badge>}
                          {salaryLabel && <Badge tone="neutral">{salaryLabel}</Badge>}
                          {job.minExperienceYears != null && (
                            <Badge tone="neutral">{job.minExperienceYears}+ yrs experience</Badge>
                          )}
                        </div>
                      </div>
                      <span style={{ color: "var(--ink-muted)", fontSize: "0.8125rem", flexShrink: 0, whiteSpace: "nowrap" }}>
                        Posted {formatRelative(job.createdAt)}
                      </span>
                    </div>
                  </Link>
                );
              })}
            </div>
          )}

          <p style={{ marginTop: "1.5rem", fontSize: "0.8125rem", color: "var(--ink-faint)" }}>
            {filteredJobs.length} of {jobs.length} open position{jobs.length !== 1 ? "s" : ""} shown
          </p>
        </div>

        <TrendingSkillsCard skills={trendingSkills} loading={trendingLoading} />
      </div>
    </main>
  );
}