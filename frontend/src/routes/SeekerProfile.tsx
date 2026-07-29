import { FormEvent, useEffect, useRef, useState, type ChangeEvent, type DragEvent } from "react";
import { ApiResponseError } from "../api/client";
import {
  getSeekerProfile,
  getSeekerResume,
  updateSeekerProfile,
  uploadSeekerResume,
} from "../api/seeker";
import type { SeekerProfile } from "../api/seeker";
import type { SectorCode, WorkMode } from "../api/matching";
import { SECTOR_OPTIONS, WORK_MODE_LABELS } from "../api/matching";
import {
  Alert,
  Badge,
  Button,
  Card,
  EmptyState,
  Icon,
  Input,
  Modal,
  Spinner,
} from "../components/ui";
import PageLoader from "../components/PageLoader";

function formatDate(iso: string | null): string {
  if (!iso) return "Not parsed yet";
  return new Date(iso).toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

function errorMessage(error: unknown, fallback: string): string {
  if (error instanceof Error && error.message.includes("ERR_AUTH_003")) {
    return "Your session has ended. Please sign in again.";
  }
  if (error instanceof ApiResponseError) {
    const fields = error.details?.fields;
    if (fields && typeof fields === "object") {
      const first = Object.values(fields as Record<string, unknown>).find(
        (value): value is string => typeof value === "string" && value.length > 0
      );
      if (first) return first;
    }
  }
  return fallback;
}

function ProfileForm({
  profile,
  onSaved,
  onCancel,
}: {
  profile: SeekerProfile;
  onSaved: (profile: SeekerProfile) => void;
  onCancel: () => void;
}) {
  const [displayName, setDisplayName] = useState(profile.displayName ?? "");
  const [sectors, setSectors] = useState<SectorCode[]>(profile.preferredSectors);
  const [location, setLocation] = useState(profile.preferredLocation ?? "");
  const [workMode, setWorkMode] = useState<WorkMode | "">(profile.workMode ?? "");
  const [years, setYears] = useState(profile.yearsExperience == null ? "" : String(profile.yearsExperience));
  const [skills, setSkills] = useState(profile.resume?.skills ?? []);
  const [newSkill, setNewSkill] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const addSkill = () => {
    const value = newSkill.trim();
    if (!value) return;
    if (!skills.some((skill) => skill.toLowerCase() === value.toLowerCase())) {
      setSkills((current) => [...current, value]);
    }
    setNewSkill("");
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    const parsedYears = years.trim() === "" ? null : Number(years);
    if (parsedYears !== null && (!Number.isInteger(parsedYears) || parsedYears < 0 || parsedYears > 60)) {
      setError("Years of experience must be between 0 and 60.");
      return;
    }
    setSaving(true);
    try {
      const updated = await updateSeekerProfile({
        displayName: displayName.trim() || null,
        preferredSectors: sectors,
        preferredLocation: location.trim() || null,
        workMode: workMode || null,
        yearsExperience: parsedYears,
        skills,
      });
      onSaved(updated);
    } catch (err) {
      setError(errorMessage(err, "Could not save your profile."));
    } finally {
      setSaving(false);
    }
  };

  return (
    <form onSubmit={(event) => void submit(event)} noValidate>
      <div style={{ display: "grid", gap: "1rem" }}>
        {error && <Alert tone="error">{error}</Alert>}
        <Input
          label="Name"
          value={displayName}
          onChange={(event) => setDisplayName(event.target.value)}
          maxLength={200}
          placeholder="Your name"
        />
        <div>
          <label htmlFor="profile-email" style={{ display: "block", fontSize: "0.8125rem", fontWeight: 500, marginBottom: "0.375rem" }}>
            Contact email
          </label>
          <input id="profile-email" value={profile.email} disabled style={{ width: "100%", padding: "0.65rem 0.75rem", border: "1px solid var(--border)", borderRadius: "var(--radius-sm)", background: "var(--bg-subtle)", color: "var(--ink-muted)" }} />
        </div>
        <fieldset style={{ border: 0, padding: 0, margin: 0 }}>
          <legend style={{ fontSize: "0.8125rem", fontWeight: 500, marginBottom: "0.5rem" }}>Preferred sectors</legend>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(2, minmax(0, 1fr))", gap: "0.45rem" }}>
            {SECTOR_OPTIONS.map((option) => (
              <label key={option.value} style={{ display: "flex", gap: "0.5rem", alignItems: "center", color: "var(--ink-2)", fontSize: "0.875rem" }}>
                <input
                  type="checkbox"
                  checked={sectors.includes(option.value)}
                  onChange={() => setSectors((current) => current.includes(option.value) ? current.filter((item) => item !== option.value) : [...current, option.value])}
                />
                {option.label}
              </label>
            ))}
          </div>
        </fieldset>
        <Input
          label="Preferred location"
          value={location}
          onChange={(event) => setLocation(event.target.value)}
          maxLength={150}
          placeholder="e.g. Kathmandu"
        />
        <label style={{ display: "grid", gap: "0.375rem", fontSize: "0.8125rem", fontWeight: 500 }}>
          Work preference
          <select value={workMode} onChange={(event) => setWorkMode(event.target.value as WorkMode | "")} style={{ padding: "0.65rem 0.75rem", border: "1px solid var(--border)", borderRadius: "var(--radius-sm)", background: "var(--bg-card)", color: "var(--ink)" }}>
            <option value="">Not specified</option>
            {Object.entries(WORK_MODE_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
          </select>
        </label>
        <Input
          label="Years of experience"
          type="number"
          min={0}
          max={60}
          value={years}
          onChange={(event) => setYears(event.target.value)}
          placeholder="e.g. 3"
        />
        <fieldset style={{ border: 0, padding: 0, margin: 0 }}>
          <legend style={{ fontSize: "0.8125rem", fontWeight: 500, marginBottom: "0.375rem" }}>Parsed skills</legend>
          {profile.resume ? (
            <>
              <div style={{ display: "flex", gap: "0.5rem" }}>
                <input value={newSkill} onChange={(event) => setNewSkill(event.target.value)} onKeyDown={(event) => { if (event.key === "Enter") { event.preventDefault(); addSkill(); } }} placeholder="Add a skill" style={{ flex: 1, padding: "0.6rem 0.75rem", border: "1px solid var(--border)", borderRadius: "var(--radius-sm)", background: "var(--bg-card)", color: "var(--ink)" }} />
                <Button type="button" variant="secondary" size="sm" onClick={addSkill}>Add</Button>
              </div>
              <div style={{ display: "flex", flexWrap: "wrap", gap: "0.4rem", marginTop: "0.6rem" }}>
                {skills.map((skill) => <button key={skill} type="button" onClick={() => setSkills((current) => current.filter((item) => item !== skill))} title={`Remove ${skill}`} style={{ border: "1px solid var(--border)", borderRadius: "999px", padding: "0.25rem 0.6rem", background: "var(--accent-faint)", color: "var(--accent)" }}>{skill} ×</button>)}
              </div>
            </>
          ) : <p style={{ color: "var(--ink-muted)", fontSize: "0.875rem" }}>Upload a parsed resume before editing skills.</p>}
        </fieldset>
        <div style={{ display: "flex", justifyContent: "space-between", gap: "0.75rem", marginTop: "0.5rem" }}>
          <Button type="button" variant="ghost" onClick={onCancel}>Cancel</Button>
          <Button type="submit" loading={saving}>Save profile</Button>
        </div>
      </div>
    </form>
  );
}

function ResumeUploadZone({ onSuccess }: { onSuccess: () => void }) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragOver, setDragOver] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleFile = async (file: File) => {
    if ((!file.name.toLowerCase().endsWith(".pdf") && file.type !== "application/pdf") || file.size > 10 * 1024 * 1024) {
      setError(file.size > 10 * 1024 * 1024 ? "File must be under 10 MB." : "Only PDF files are accepted.");
      return;
    }
    setError(null);
    setUploading(true);
    try {
      await uploadSeekerResume(file);
      onSuccess();
    } catch (err) {
      setError(errorMessage(err, "Upload failed. Please try again."));
    } finally {
      setUploading(false);
    }
  };

  const drop = (event: DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    setDragOver(false);
    const file = event.dataTransfer.files[0];
    if (file) void handleFile(file);
  };

  const inputChange = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (file) void handleFile(file);
    event.target.value = "";
  };

  return (
    <div style={{ display: "grid", gap: "0.75rem" }}>
      <div role="button" tabIndex={0} onClick={() => inputRef.current?.click()} onDragOver={(event) => { event.preventDefault(); setDragOver(true); }} onDragLeave={() => setDragOver(false)} onDrop={drop} onKeyDown={(event) => { if (event.key === "Enter" || event.key === " ") inputRef.current?.click(); }} style={{ border: `2px dashed ${dragOver ? "var(--accent)" : "var(--border)"}`, borderRadius: "var(--radius)", padding: "1.5rem", textAlign: "center", background: dragOver ? "var(--accent-faint)" : "var(--bg-subtle)", cursor: uploading ? "wait" : "pointer" }}>
        <input ref={inputRef} type="file" accept="application/pdf,.pdf" onChange={inputChange} disabled={uploading} style={{ display: "none" }} />
        {uploading ? <span style={{ display: "inline-flex", alignItems: "center", gap: "0.5rem", color: "var(--ink-muted)" }}><Spinner size={18} /> Uploading and parsing…</span> : <><strong>Drop a PDF here or browse</strong><span style={{ display: "block", marginTop: "0.35rem", color: "var(--ink-muted)", fontSize: "0.8125rem" }}>The new upload replaces your current resume.</span></>}
      </div>
      {error && <Alert tone="error">{error}</Alert>}
    </div>
  );
}

export default function SeekerProfile() {
  const [profile, setProfile] = useState<SeekerProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [editOpen, setEditOpen] = useState(false);
  const [uploadOpen, setUploadOpen] = useState(false);
  const [resumeError, setResumeError] = useState<string | null>(null);

  const loadProfile = async () => {
    setLoading(true);
    try {
      setProfile(await getSeekerProfile());
      setError(null);
    } catch (err) {
      setError(errorMessage(err, "Could not load your profile."));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    document.title = "Profile - JobVault";
    void loadProfile();
  }, []);

  useEffect(() => {
    if (!notice) return;
    const timer = window.setTimeout(() => setNotice(null), 3500);
    return () => window.clearTimeout(timer);
  }, [notice]);

  const openResume = async (download: boolean) => {
    if (!profile?.resume) return;
    setResumeError(null);
    try {
      const blob = await getSeekerResume(profile.userId, undefined, download);
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
      setResumeError(errorMessage(err, "Could not open the resume."));
    }
  };

  return (
    <main className="page seeker-page">
      <div className="page-header">
        <div className="page-header__copy">
          <p className="eyebrow">Your profile</p>
          <h1>Profile information</h1>
          <p className="page-header__subtitle">Keep one clear profile for matching, applications, and recruiter review.</p>
        </div>
        <div style={{ display: "flex", gap: "0.5rem", flexWrap: "wrap" }}>
          <Button variant="secondary" onClick={() => setUploadOpen(true)}><Icon name="upload" size={14} /> Replace resume</Button>
          <Button onClick={() => setEditOpen(true)} disabled={!profile}><Icon name="edit" size={14} /> Edit profile</Button>
        </div>
      </div>
      {notice && <Alert tone="success">{notice}</Alert>}
      {error && <Alert tone="error">{error}</Alert>}
      {loading ? <PageLoader /> : profile ? (
        <article className="profile-sheet">
          <section className="profile-sheet__identity">
            <div>
              <p className="eyebrow">Seeker profile</p>
              <h2>{profile.displayName || "Name not set"}</h2>
              <a href={`mailto:${profile.email}`} style={{ color: "var(--accent)" }}>{profile.email}</a>
            </div>
            <Badge tone={profile.resume?.status === "PARSED" ? "success" : "neutral"}>{profile.resume?.status === "PARSED" ? "Profile ready" : "Resume needed"}</Badge>
          </section>

          <div className="profile-sheet__grid">
            <section>
              <h3>Matching preferences</h3>
              <dl className="profile-facts">
                <div><dt>Preferred sectors</dt><dd>{profile.preferredSectors.length ? profile.preferredSectors.map((sector) => SECTOR_OPTIONS.find((item) => item.value === sector)?.label ?? sector).join(", ") : "Not set"}</dd></div>
                <div><dt>Location</dt><dd>{profile.preferredLocation || "Not set"}</dd></div>
                <div><dt>Work preference</dt><dd>{profile.workMode ? WORK_MODE_LABELS[profile.workMode] : "Not specified"}</dd></div>
                <div><dt>Experience</dt><dd>{profile.yearsExperience == null ? "Not set" : `${profile.yearsExperience} year${profile.yearsExperience === 1 ? "" : "s"}`}</dd></div>
              </dl>
            </section>
            <section>
              <h3>Current resume</h3>
              {profile.resume ? <>
                <p style={{ fontWeight: 600, marginBottom: "0.25rem", overflowWrap: "anywhere" }}>{profile.resume.originalFilename}</p>
                <p style={{ color: "var(--ink-muted)", fontSize: "0.875rem" }}>Parsed {formatDate(profile.resume.parsedAt)}</p>
                <div style={{ display: "flex", gap: "0.5rem", flexWrap: "wrap", marginTop: "0.875rem" }}>
                  <Button size="sm" variant="secondary" onClick={() => void openResume(false)} disabled={profile.resume.status !== "PARSED"}>View resume</Button>
                  <Button size="sm" variant="ghost" onClick={() => void openResume(true)} disabled={profile.resume.status !== "PARSED"}>Download</Button>
                </div>
              </> : <EmptyState title="No resume uploaded" description="Upload a PDF to extract skills and improve matching." action={<Button onClick={() => setUploadOpen(true)}>Upload resume</Button>} />}
            </section>
          </div>

          <section className="profile-sheet__skills">
            <div style={{ display: "flex", justifyContent: "space-between", gap: "1rem", alignItems: "baseline", flexWrap: "wrap" }}><h3>Skills</h3><span style={{ color: "var(--ink-muted)", fontSize: "0.8125rem" }}>{profile.resume?.skills.length ?? 0} listed</span></div>
            {profile.resume?.skills.length ? <div className="skill-cloud">{profile.resume.skills.map((skill) => <span key={skill}>{skill}</span>)}</div> : <p style={{ color: "var(--ink-muted)" }}>Upload a parsed resume or add skills from Edit profile.</p>}
          </section>
          {resumeError && <Alert tone="error">{resumeError}</Alert>}
        </article>
      ) : null}

      <Modal open={editOpen} title="Edit profile" description="Update the details recruiters use to understand your fit." onClose={() => setEditOpen(false)}>
        {profile && <ProfileForm profile={profile} onCancel={() => setEditOpen(false)} onSaved={(updated) => { setProfile(updated); setEditOpen(false); setNotice("Profile saved. Matching preferences updated."); }} />}
      </Modal>
      <Modal open={uploadOpen} title="Replace resume" description="Upload one PDF. It will replace the current resume and refresh parsed skills." onClose={() => setUploadOpen(false)} footer={<Button variant="ghost" onClick={() => setUploadOpen(false)}>Close</Button>}>
        <ResumeUploadZone onSuccess={() => { setUploadOpen(false); setNotice("Resume replaced and parsed. Matching will use the new version."); void loadProfile(); }} />
      </Modal>
    </main>
  );
}
