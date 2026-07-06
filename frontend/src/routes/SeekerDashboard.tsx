import { useState, useEffect, useMemo } from "react";
import { Link } from "react-router-dom";
import { getSeekerMatches, getSeekerResumeHistory, getMyApplications } from "../api/seeker";
import type { SeekerMatchItem } from "../api/seeker";
import type { ApplicationStatus } from "../api/employer";
import { formatSalaryRange } from "../api/employer";
import { Alert, Badge, Card, Spinner } from "../components/ui";
import { useAuth } from "../api/authContext";

/* ── Helpers ─────────────────────────────────────────────────── */

function scoreColor(score: number): string {
  if (score >= 0.7) return "var(--success)";
  if (score >= 0.4) return "#B07D20";
  return "var(--ink-muted)";
}

function scoreTone(score: number): "success" | "warn" | "neutral" {
  if (score >= 0.7) return "success";
  if (score >= 0.4) return "warn";
  return "neutral";
}

function applicationStatusLabel(status: ApplicationStatus): string {
  const map: Record<ApplicationStatus, string> = {
    DRAFT: "Draft",
    SUBMITTED: "Applied",
    UNDER_REVIEW: "Under review",
    ACCEPTED: "Accepted",
    REJECTED: "Not selected",
    WITHDRAWN: "Withdrawn",
  };
  return map[status];
}

function applicationBadgeTone(
  status: ApplicationStatus
): "neutral" | "success" | "warn" | "accent" {
  if (status === "ACCEPTED") return "success";
  if (status === "REJECTED" || status === "WITHDRAWN") return "warn";
  if (status === "SUBMITTED" || status === "UNDER_REVIEW") return "accent";
  return "neutral";
}

/* ── Stat card ───────────────────────────────────────────────── */

function StatCard({
  label,
  value,
  sub,
}: {
  label: string;
  value: string | number;
  sub?: string;
}) {
  return (
    <div
      style={{
        background: "var(--bg-card)",
        border: "1px solid var(--border)",
        borderRadius: "var(--radius)",
        padding: "1.125rem 1.25rem",
        boxShadow: "var(--shadow-sm)",
        display: "flex",
        flexDirection: "column",
        gap: "0.25rem",
      }}
    >
      <span
        style={{
          fontSize: "0.7rem",
          fontWeight: 600,
          letterSpacing: "0.07em",
          textTransform: "uppercase",
          color: "var(--ink-muted)",
        }}
      >
        {label}
      </span>
      <span
        style={{
          fontFamily: "var(--font-display)",
          fontSize: "1.875rem",
          fontWeight: 700,
          lineHeight: 1.1,
          color: "var(--ink)",
        }}
      >
        {value}
      </span>
      {sub && (
        <span style={{ fontSize: "0.75rem", color: "var(--ink-muted)" }}>{sub}</span>
      )}
    </div>
  );
}

/* ── Match card (compact) ────────────────────────────────────── */

function MatchCard({ item }: { item: SeekerMatchItem }) {
  const salaryLabel = formatSalaryRange(item.job.salaryMin, item.job.salaryMax);
  const pct = Math.round(item.score * 100);

  return (
    <Link to={`/jobs/${item.jobId}`} style={{ textDecoration: "none" }}>
      <div
        style={{
          background: "var(--bg-card)",
          border: "1px solid var(--border)",
          borderRadius: "var(--radius)",
          padding: "1rem 1.125rem",
          boxShadow: "var(--shadow-sm)",
          transition: "border-color 0.15s, box-shadow 0.15s",
          cursor: "pointer",
          display: "flex",
          gap: "0.875rem",
          alignItems: "flex-start",
        }}
        onMouseEnter={(e) => {
          (e.currentTarget as HTMLDivElement).style.borderColor = "var(--accent)";
          (e.currentTarget as HTMLDivElement).style.boxShadow = "var(--shadow)";
        }}
        onMouseLeave={(e) => {
          (e.currentTarget as HTMLDivElement).style.borderColor = "var(--border)";
          (e.currentTarget as HTMLDivElement).style.boxShadow = "var(--shadow-sm)";
        }}
      >
        {/* Score block */}
        <div
          style={{
            flexShrink: 0,
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            justifyContent: "center",
            width: 48,
            height: 48,
            borderRadius: "var(--radius-sm)",
            background: "var(--bg-subtle)",
            border: "1px solid var(--border)",
          }}
        >
          <span
            style={{
              fontFamily: "var(--font-display)",
              fontSize: "1rem",
              fontWeight: 700,
              lineHeight: 1,
              color: scoreColor(item.score),
            }}
          >
            {pct}
          </span>
          <span style={{ fontSize: "0.5625rem", color: "var(--ink-faint)", letterSpacing: "0.04em" }}>
            %
          </span>
        </div>

        {/* Content */}
        <div style={{ minWidth: 0, flex: 1 }}>
          <div
            style={{
              fontWeight: 600,
              fontSize: "0.9375rem",
              color: "var(--ink)",
              marginBottom: "0.125rem",
              overflow: "hidden",
              textOverflow: "ellipsis",
              whiteSpace: "nowrap",
            }}
          >
            {item.job.title}
          </div>
          {item.job.companyName && (
            <div style={{ fontSize: "0.8125rem", color: "var(--ink-2)", fontWeight: 500, marginBottom: "0.35rem" }}>
              {item.job.companyName}
            </div>
          )}
          <div style={{ display: "flex", flexWrap: "wrap", gap: "0.35rem" }}>
            {item.job.location && <Badge tone="neutral">{item.job.location}</Badge>}
            {item.job.remoteEligible && <Badge tone="accent">Remote</Badge>}
            {salaryLabel && <Badge tone="neutral">{salaryLabel}</Badge>}
            <Badge tone={scoreTone(item.score)}>
              {item.missingSkills.length === 0
                ? "Strong match"
                : `${item.missingSkills.length} gap${item.missingSkills.length !== 1 ? "s" : ""}`}
            </Badge>
          </div>
        </div>
      </div>
    </Link>
  );
}

/* ── Main dashboard ───────────────────────────────────────────── */

export default function SeekerDashboard() {
  const { user } = useAuth();

  const [matchItems, setMatchItems] = useState<SeekerMatchItem[]>([]);
  const [matchTotal, setMatchTotal] = useState(0);
  const [matchLoading, setMatchLoading] = useState(true);
  const [matchError, setMatchError] = useState<string | null>(null);
  const [noResume, setNoResume] = useState(false);

  const [resumeStatus, setResumeStatus] = useState<string | null>(null);
  const [skillCount, setSkillCount] = useState<number | null>(null);
  // Skills extracted from the user's own resume
  const [resumeSkills, setResumeSkills] = useState<string[]>([]);
  const [historyLoading, setHistoryLoading] = useState(true);

  const [appCounts, setAppCounts] = useState({
    total: 0,
    submitted: 0,
    underReview: 0,
    accepted: 0,
  });
  const [recentApps, setRecentApps] = useState<
    {
      id: string;
      jobTitle: string | null;
      companyName: string | null;
      status: ApplicationStatus;
    }[]
  >([]);

  useEffect(() => {
    const loadMatches = async () => {
      setMatchLoading(true);
      setMatchError(null);
      try {
        const result = await getSeekerMatches({ limit: 3, offset: 0 });
        setMatchItems(result.items);
        setMatchTotal(result.page.total);
      } catch (err) {
        const msg = err instanceof Error ? err.message : "";
        if (msg.includes("404") || msg.includes("resume")) {
          setNoResume(true);
        } else if (!msg.includes("ERR_AUTH_003")) {
          setMatchError("Could not load matches.");
        }
      } finally {
        setMatchLoading(false);
      }
    };

    const loadHistory = async () => {
      setHistoryLoading(true);
      try {
        const result = await getSeekerResumeHistory({ limit: 20, offset: 0 });
        const parsed = result.items.find((i) => i.status === "PARSED");
        setResumeStatus(parsed ? "Ready" : result.items.length > 0 ? "Not parsed" : "None");
        if (parsed) {
          setSkillCount(parsed.inferredSkills.length);
          setResumeSkills(parsed.inferredSkills.slice(0, 8));
        } else {
          setSkillCount(0);
        }
      } catch {
        setResumeStatus("—");
        setSkillCount(0);
      } finally {
        setHistoryLoading(false);
      }
    };

    const loadApps = async () => {
      try {
        const items = await getMyApplications();
        setAppCounts({
          total: items.length,
          submitted: items.filter((i) => i.status === "SUBMITTED").length,
          underReview: items.filter((i) => i.status === "UNDER_REVIEW").length,
          accepted: items.filter((i) => i.status === "ACCEPTED").length,
        });
        setRecentApps(
          items.slice(0, 3).map((i) => ({
            id: i.id,
            jobTitle: i.jobTitle,
            companyName: i.companyName,
            status: i.status,
          }))
        );
      } catch {
        // non-fatal
      }
    };

    void loadMatches();
    void loadHistory();
    void loadApps();
  }, []);

  const greeting = useMemo(() => {
    const name = user?.displayName ?? user?.email?.split("@")[0] ?? "there";
    return `Welcome back, ${name}`;
  }, [user]);

  const dataLoading = matchLoading || historyLoading;

  return (
    <main style={{ maxWidth: 1100, margin: "0 auto", padding: "2rem 1.5rem 3rem" }}>
      {/* Header */}
      <div style={{ marginBottom: "1.75rem" }}>
        <h1 style={{ marginBottom: "0.25rem" }}>{greeting}</h1>
        <p style={{ color: "var(--ink-muted)", fontSize: "0.9375rem" }}>
          Here is your job search at a glance.
        </p>
      </div>

      {dataLoading ? (
        <div style={{ display: "flex", justifyContent: "center", padding: "4rem 0" }}>
          <Spinner size={32} />
        </div>
      ) : (
        <>
          {/* Stats row */}
          <div
            style={{
              display: "grid",
              gridTemplateColumns: "repeat(5, 1fr)",
              gap: "1rem",
              marginBottom: "2rem",
            }}
          >
            <StatCard label="Total matches" value={matchTotal} sub="across active roles" />
            <StatCard label="Applications" value={appCounts.total} sub={`${appCounts.submitted} submitted`} />
            <StatCard label="Under review" value={appCounts.underReview} sub="by employers" />
            <StatCard
              label="Resume"
              value={resumeStatus ?? "—"}
              sub={
                skillCount != null && skillCount > 0
                  ? `${skillCount} skills detected`
                  : "Upload on Profile page"
              }
            />
            <StatCard label="Skills detected" value={skillCount ?? 0} sub="from your resume" />
          </div>

          {/* No resume prompt */}
          {noResume && (
            <div style={{ marginBottom: "1.5rem" }}>
              <Alert tone="info">
                Upload a resume on your{" "}
                <Link to="/seeker/profile" style={{ color: "var(--accent)", fontWeight: 500 }}>
                  Profile page
                </Link>{" "}
                to unlock ranked job matches and skill gap analysis.
              </Alert>
            </div>
          )}

          {/*
            Main grid: left column takes 3/5, right column takes 2/5.
            This gives the content-heavy left side more room while keeping
            the sidebar panels comfortably readable.
          */}
          <div
            style={{
              display: "grid",
              gridTemplateColumns: "3fr 2fr",
              gap: "1.5rem",
              alignItems: "start",
            }}
          >
            {/* Left: top matches + recent applications */}
            <div style={{ display: "flex", flexDirection: "column", gap: "2rem" }}>

              {/* Top matches */}
              <section>
                <div
                  style={{
                    display: "flex",
                    alignItems: "baseline",
                    justifyContent: "space-between",
                    gap: "1rem",
                    marginBottom: "0.875rem",
                  }}
                >
                  <h2 style={{ fontSize: "1.0625rem" }}>Top matches</h2>
                  <Link
                    to="/seeker/matches"
                    style={{ fontSize: "0.875rem", color: "var(--accent)", fontWeight: 500 }}
                  >
                    View all{matchTotal > 0 ? ` (${matchTotal})` : ""}
                  </Link>
                </div>

                {matchError && <Alert tone="error">{matchError}</Alert>}

                {!matchError && !noResume && matchItems.length === 0 && (
                  <Card>
                    <p style={{ color: "var(--ink-muted)", fontSize: "0.875rem" }}>
                      No matches yet. Make sure your resume is uploaded and parsed.
                    </p>
                  </Card>
                )}

                {matchItems.length > 0 && (
                  <div style={{ display: "flex", flexDirection: "column", gap: "0.625rem" }}>
                    {matchItems.map((item) => (
                      <MatchCard key={item.jobId} item={item} />
                    ))}
                  </div>
                )}
              </section>

              {/* Recent applications */}
              <section>
                <div
                  style={{
                    display: "flex",
                    alignItems: "baseline",
                    justifyContent: "space-between",
                    gap: "1rem",
                    marginBottom: "0.875rem",
                  }}
                >
                  <h2 style={{ fontSize: "1.0625rem" }}>Recent applications</h2>
                  <Link
                    to="/seeker/applications"
                    style={{ fontSize: "0.875rem", color: "var(--accent)", fontWeight: 500 }}
                  >
                    View all
                  </Link>
                </div>

                {recentApps.length === 0 ? (
                  <Card>
                    <p style={{ color: "var(--ink-muted)", fontSize: "0.875rem" }}>
                      No applications yet.{" "}
                      <Link to="/seeker/matches" style={{ color: "var(--accent)" }}>
                        Browse matches
                      </Link>{" "}
                      to find roles to apply for.
                    </p>
                  </Card>
                ) : (
                  <Card padded={false}>
                    {recentApps.map((app, idx) => (
                      <div
                        key={app.id}
                        style={{
                          display: "flex",
                          alignItems: "center",
                          justifyContent: "space-between",
                          gap: "1rem",
                          padding: "0.875rem 1.25rem",
                          borderBottom:
                            idx < recentApps.length - 1 ? "1px solid var(--border)" : "none",
                        }}
                      >
                        <div style={{ minWidth: 0 }}>
                          <div
                            style={{
                              fontWeight: 500,
                              fontSize: "0.9375rem",
                              color: "var(--ink)",
                              overflow: "hidden",
                              textOverflow: "ellipsis",
                              whiteSpace: "nowrap",
                            }}
                          >
                            {app.jobTitle ?? "Untitled role"}
                          </div>
                          {app.companyName && (
                            <div style={{ fontSize: "0.8125rem", color: "var(--ink-muted)", marginTop: "0.125rem" }}>
                              {app.companyName}
                            </div>
                          )}
                        </div>
                        <Badge tone={applicationBadgeTone(app.status)}>
                          {applicationStatusLabel(app.status)}
                        </Badge>
                      </div>
                    ))}
                  </Card>
                )}
              </section>
            </div>

            {/* Right sidebar */}
            <div style={{ display: "flex", flexDirection: "column", gap: "1.5rem" }}>

              {/*
                "Skills from your resume" — intentionally distinct from the
                market-wide trending skills shown on the Browse Jobs page.
                The heading makes the source of data clear.
              */}
              <section>
                <div
                  style={{
                    display: "flex",
                    alignItems: "baseline",
                    justifyContent: "space-between",
                    gap: "1rem",
                    marginBottom: "0.875rem",
                  }}
                >
                  <h2 style={{ fontSize: "1.0625rem" }}>Skills from your resume</h2>
                  <Link
                    to="/seeker/profile"
                    style={{ fontSize: "0.875rem", color: "var(--accent)", fontWeight: 500 }}
                  >
                    Update
                  </Link>
                </div>
                <Card>
                  {resumeSkills.length === 0 ? (
                    <div>
                      <p style={{ color: "var(--ink-muted)", fontSize: "0.875rem", marginBottom: "0.625rem" }}>
                        No skills detected yet. Upload a resume on your Profile page to get started.
                      </p>
                      <Link
                        to="/seeker/profile"
                        style={{ fontSize: "0.875rem", color: "var(--accent)", fontWeight: 500 }}
                      >
                        Go to Profile
                      </Link>
                    </div>
                  ) : (
                    <>
                      <div style={{ display: "flex", flexWrap: "wrap", gap: "0.4rem" }}>
                        {resumeSkills.map((skill) => (
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
                      {skillCount !== null && skillCount > resumeSkills.length && (
                        <p style={{ marginTop: "0.625rem", fontSize: "0.75rem", color: "var(--ink-muted)" }}>
                          +{skillCount - resumeSkills.length} more on your profile
                        </p>
                      )}
                    </>
                  )}
                </Card>
              </section>

              {/* Quick links */}
              <section>
                <h2 style={{ fontSize: "1.0625rem", marginBottom: "0.875rem" }}>Quick links</h2>
                <Card padded={false}>
                  {[
                    { to: "/seeker/matches", label: "View job matches" },
                    { to: "/seeker/applications", label: "My applications" },
                    { to: "/seeker/profile", label: "Profile and resume" },
                    { to: "/jobs", label: "Browse all open roles" },
                  ].map((link, idx, arr) => (
                    <Link
                      key={link.to}
                      to={link.to}
                      style={{
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "space-between",
                        padding: "0.75rem 1.25rem",
                        borderBottom: idx < arr.length - 1 ? "1px solid var(--border)" : "none",
                        color: "var(--ink-2)",
                        fontSize: "0.9375rem",
                        fontWeight: 500,
                        textDecoration: "none",
                        transition: "background 0.12s",
                      }}
                      onMouseEnter={(e) => {
                        (e.currentTarget as HTMLAnchorElement).style.background = "var(--bg-subtle)";
                      }}
                      onMouseLeave={(e) => {
                        (e.currentTarget as HTMLAnchorElement).style.background = "transparent";
                      }}
                    >
                      <span>{link.label}</span>
                      <span style={{ color: "var(--ink-faint)", fontSize: "0.875rem" }}>›</span>
                    </Link>
                  ))}
                </Card>
              </section>
            </div>
          </div>
        </>
      )}
    </main>
  );
}
