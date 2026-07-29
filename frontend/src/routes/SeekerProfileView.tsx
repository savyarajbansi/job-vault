import { useEffect, useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { ApiResponseError } from "../api/client";
import { getSeekerPublicProfile, getSeekerResume } from "../api/seeker";
import type { SeekerProfile } from "../api/seeker";
import { SECTOR_OPTIONS, WORK_MODE_LABELS } from "../api/matching";
import { Alert, Badge, Button, EmptyState } from "../components/ui";
import PageLoader from "../components/PageLoader";

function message(error: unknown): string {
  if (error instanceof Error && error.message.includes("ERR_AUTH_003")) return "Your session has ended. Please sign in again.";
  if (error instanceof ApiResponseError && error.response.status === 404) return "This profile is not available for the current job context.";
  return "Could not load this profile.";
}

export default function SeekerProfileView() {
  const { seekerId } = useParams();
  const [params] = useSearchParams();
  const jobId = params.get("jobId") ?? undefined;
  const [profile, setProfile] = useState<SeekerProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [resumeError, setResumeError] = useState<string | null>(null);

  useEffect(() => {
    document.title = "Seeker Profile - JobVault";
    if (!seekerId) return;
    void getSeekerPublicProfile(seekerId, jobId).then(setProfile).catch((err) => setError(message(err))).finally(() => setLoading(false));
  }, [seekerId, jobId]);

  const openResume = async (download: boolean) => {
    if (!profile?.resume || !seekerId) return;
    try {
      const blob = await getSeekerResume(seekerId, jobId, download);
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.target = download ? "_self" : "_blank";
      if (download) link.download = profile.resume.originalFilename;
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (err) {
      setResumeError(message(err));
    }
  };

  return (
    <main className="page seeker-page">
      <div className="page-header">
        <div className="page-header__copy">
          <Link to={jobId ? `/employer/jobs/${jobId}/matches` : "/seeker/profile"} style={{ color: "var(--ink-muted)", fontSize: "0.875rem" }}>← Back</Link>
          <h1 style={{ marginTop: "0.75rem" }}>Seeker profile</h1>
          <p className="page-header__subtitle">The profile information shared for this matching workflow.</p>
        </div>
      </div>
      {loading ? <PageLoader /> : error ? <Alert tone="error">{error}</Alert> : profile ? (
        <article className="profile-sheet">
          <section className="profile-sheet__identity">
            <div><p className="eyebrow">Candidate</p><h2>{profile.displayName || "Name not set"}</h2><a href={`mailto:${profile.email}`} style={{ color: "var(--accent)" }}>{profile.email}</a></div>
            <Badge tone={profile.resume?.status === "PARSED" ? "success" : "neutral"}>{profile.resume?.status === "PARSED" ? "Resume ready" : "No parsed resume"}</Badge>
          </section>
          <div className="profile-sheet__grid">
            <section><h3>Relevant information</h3><dl className="profile-facts">
              <div><dt>Preferred sectors</dt><dd>{profile.preferredSectors.length ? profile.preferredSectors.map((sector) => SECTOR_OPTIONS.find((item) => item.value === sector)?.label ?? sector).join(", ") : "Not set"}</dd></div>
              <div><dt>Location</dt><dd>{profile.preferredLocation || "Not set"}</dd></div>
              <div><dt>Work preference</dt><dd>{profile.workMode ? WORK_MODE_LABELS[profile.workMode] : "Not specified"}</dd></div>
              <div><dt>Experience</dt><dd>{profile.yearsExperience == null ? "Not set" : `${profile.yearsExperience} years`}</dd></div>
            </dl></section>
            <section><h3>Resume</h3>{profile.resume ? <><p style={{ fontWeight: 600, overflowWrap: "anywhere" }}>{profile.resume.originalFilename}</p><p style={{ color: "var(--ink-muted)", fontSize: "0.875rem" }}>{profile.resume.skills.length} skills parsed</p><div style={{ display: "flex", gap: "0.5rem", marginTop: "0.875rem", flexWrap: "wrap" }}><Button size="sm" variant="secondary" onClick={() => void openResume(false)} disabled={profile.resume.status !== "PARSED"}>View resume</Button><Button size="sm" variant="ghost" onClick={() => void openResume(true)} disabled={profile.resume.status !== "PARSED"}>Download</Button></div></> : <EmptyState title="No resume available" description="This seeker has not uploaded a parsed resume." />}</section>
          </div>
          <section className="profile-sheet__skills"><div style={{ display: "flex", justifyContent: "space-between", gap: "1rem" }}><h3>Skills</h3><span style={{ color: "var(--ink-muted)", fontSize: "0.8125rem" }}>{profile.resume?.skills.length ?? 0} listed</span></div>{profile.resume?.skills.length ? <div className="skill-cloud">{profile.resume.skills.map((skill) => <span key={skill}>{skill}</span>)}</div> : <p style={{ color: "var(--ink-muted)" }}>No parsed skills available.</p>}</section>
          {resumeError && <Alert tone="error">{resumeError}</Alert>}
        </article>
      ) : null}
    </main>
  );
}
