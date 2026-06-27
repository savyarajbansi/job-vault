import { useState, useEffect, useMemo, useRef, FormEvent } from "react";
import { Link } from "react-router-dom";
import {
  uploadSeekerResume,
  getSeekerResumeHistory,
  getSeekerProfile,
  updateSeekerProfile,
} from "../api/seeker";
import type { ResumeHistoryResponse, SeekerProfile } from "../api/seeker";
import { Button, Alert, Card, Badge, Spinner, Input } from "../components/ui";
import { useAuth } from "../api/authContext";

function statusTone(status: string): "success" | "warn" | "neutral" {
  if (status === "PARSED") return "success";
  if (status === "FAILED") return "warn";
  return "neutral";
}

function statusLabel(status: string): string {
  const map: Record<string, string> = {
    UPLOADED: "Uploaded",
    PARSING: "Processing",
    PARSED: "Ready",
    FAILED: "Failed",
  };
  return map[status] ?? status;
}

function formatDate(iso: string | null): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

type ResumeDrawerItem = {
  resumeId: string;
  originalFilename: string;
  status: string;
  failureCode: string | null;
  createdAt: string;
  parsedAt: string | null;
  inferredSkills: string[];
};

/* ── Resume detail drawer ─────────────────────────────────────── */
function ResumeDrawer({
  resume,
  onClose,
}: {
  resume: ResumeDrawerItem;
  onClose: () => void;
}) {
  // Close on Escape key
  useEffect(() => {
    const handler = (e: KeyboardEvent) => { if (e.key === "Escape") onClose(); };
    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, [onClose]);

  return (
    <>
      {/* Backdrop */}
      <div
        onClick={onClose}
        style={{
          position: "fixed", inset: 0, background: "rgba(26,25,22,0.35)",
          zIndex: 200, backdropFilter: "blur(2px)",
          animation: "fadeIn 0.2s ease both",
        }}
      />
      {/* Panel */}
      <div
        style={{
          position: "fixed", right: 0, top: 0, bottom: 0, width: "min(480px, 100vw)",
          background: "var(--bg-card)", borderLeft: "1px solid var(--border)",
          zIndex: 201, overflowY: "auto", padding: "2rem",
          boxShadow: "-8px 0 32px rgba(26,25,22,0.1)",
          animation: "slideInRight 0.28s cubic-bezier(0.16,1,0.3,1) both",
        }}
      >
        <style>{`
          @keyframes slideInRight {
            from { transform: translateX(100%); opacity: 0; }
            to   { transform: translateX(0); opacity: 1; }
          }
        `}</style>

        {/* Header */}
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: "1.75rem" }}>
          <div>
            <h2 style={{ fontSize: "1.125rem", marginBottom: "0.25rem" }}>Resume details</h2>
            <p style={{ fontSize: "0.8125rem", color: "var(--ink-muted)" }}>
              {resume.originalFilename}
            </p>
          </div>
          <button
            onClick={onClose}
            style={{
              background: "none", border: "none", cursor: "pointer",
              color: "var(--ink-muted)", fontSize: "1.25rem", lineHeight: 1,
              padding: "0.25rem", borderRadius: "var(--radius-sm)",
              flexShrink: 0, marginLeft: "1rem",
            }}
            aria-label="Close"
          >
            ✕
          </button>
        </div>

        {/* Status & dates */}
        <div style={{ display: "flex", flexDirection: "column", gap: "1rem", marginBottom: "1.5rem" }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <span style={{ fontSize: "0.8125rem", color: "var(--ink-muted)", fontWeight: 500 }}>Status</span>
            <Badge tone={statusTone(resume.status)}>{statusLabel(resume.status)}</Badge>
          </div>
          <div style={{ display: "flex", justifyContent: "space-between" }}>
            <span style={{ fontSize: "0.8125rem", color: "var(--ink-muted)", fontWeight: 500 }}>Uploaded</span>
            <span style={{ fontSize: "0.8125rem", color: "var(--ink-2)" }}>{formatDate(resume.createdAt)}</span>
          </div>
          {resume.parsedAt && (
            <div style={{ display: "flex", justifyContent: "space-between" }}>
              <span style={{ fontSize: "0.8125rem", color: "var(--ink-muted)", fontWeight: 500 }}>Parsed</span>
              <span style={{ fontSize: "0.8125rem", color: "var(--ink-2)" }}>{formatDate(resume.parsedAt)}</span>
            </div>
          )}
          {resume.failureCode && (
            <div style={{ display: "flex", justifyContent: "space-between" }}>
              <span style={{ fontSize: "0.8125rem", color: "var(--ink-muted)", fontWeight: 500 }}>Error code</span>
              <span style={{ fontSize: "0.8125rem", color: "var(--warn)", fontFamily: "monospace" }}>{resume.failureCode}</span>
            </div>
          )}
          <div style={{ display: "flex", justifyContent: "space-between" }}>
            <span style={{ fontSize: "0.8125rem", color: "var(--ink-muted)", fontWeight: 500 }}>Resume ID</span>
            <span style={{ fontSize: "0.75rem", color: "var(--ink-faint)", fontFamily: "monospace", wordBreak: "break-all", textAlign: "right", maxWidth: "60%" }}>
              {resume.resumeId}
            </span>
          </div>
        </div>

        {/* Detected skills */}
        {resume.inferredSkills.length > 0 && (
          <div style={{ marginBottom: "1.5rem" }}>
            <p style={{ fontSize: "0.8125rem", color: "var(--ink-muted)", fontWeight: 500, marginBottom: "0.625rem" }}>
              Detected skills
            </p>
            <div style={{ display: "flex", flexWrap: "wrap", gap: "0.4rem" }}>
              {resume.inferredSkills.map((skill) => (
                <span
                  key={skill}
                  style={{
                    padding: "0.2rem 0.55rem",
                    background: "var(--accent-faint)",
                    color: "var(--accent)",
                    borderRadius: "999px",
                    fontSize: "0.75rem",
                    fontWeight: 500,
                  }}
                >
                  {skill}
                </span>
              ))}
            </div>
          </div>
        )}

        <div style={{ height: "1px", background: "var(--border)", marginBottom: "1.5rem" }} />

        {resume.status === "PARSED" ? (
          <div
            style={{
              padding: "1rem",
              background: "var(--success-faint)",
              borderRadius: "var(--radius-sm)",
              border: "1px solid var(--success)",
              display: "flex", gap: "0.75rem", alignItems: "flex-start",
            }}
          >
            <span style={{ fontSize: "1.25rem" }}>✅</span>
            <div>
              <p style={{ fontWeight: 600, color: "var(--success)", marginBottom: "0.25rem", fontSize: "0.875rem" }}>
                Ready for matching
              </p>
              <p style={{ fontSize: "0.8125rem", color: "var(--ink-2)" }}>
                Your skills have been extracted. Visit the Matches tab to see how your resume ranks against open roles.
              </p>
            </div>
          </div>
        ) : resume.status === "FAILED" ? (
          <div
            style={{
              padding: "1rem",
              background: "var(--warn-faint)",
              borderRadius: "var(--radius-sm)",
              border: "1px solid var(--warn)",
              display: "flex", gap: "0.75rem", alignItems: "flex-start",
            }}
          >
            <span style={{ fontSize: "1.25rem" }}>⚠️</span>
            <div>
              <p style={{ fontWeight: 600, color: "var(--warn)", marginBottom: "0.25rem", fontSize: "0.875rem" }}>
                Parsing failed
              </p>
              <p style={{ fontSize: "0.8125rem", color: "var(--ink-2)" }}>
                Try uploading a text-based PDF. Scanned images and password-protected files may not parse correctly.
              </p>
            </div>
          </div>
        ) : (
          <div
            style={{
              padding: "1rem",
              background: "var(--bg-subtle)",
              borderRadius: "var(--radius-sm)",
              border: "1px solid var(--border)",
            }}
          >
            <p style={{ fontSize: "0.875rem", color: "var(--ink-muted)" }}>
              Processing — check back in a moment.
            </p>
          </div>
        )}
      </div>
    </>
  );
}

/* ── Profile editor panel ─────────────────────────────────────── */
function ProfilePanel({
  profile,
  saving,
  onSave,
}: {
  profile: SeekerProfile | null;
  saving: boolean;
  onSave: (data: Partial<SeekerProfile>) => Promise<void>;
}) {
  const [editing, setEditing] = useState(false);
  const [sector, setSector] = useState(profile?.preferredSector ?? "");
  const [location, setLocation] = useState(profile?.preferredLocation ?? "");
  const [remote, setRemote] = useState<boolean>(profile?.remoteOk ?? false);
  const [years, setYears] = useState(String(profile?.yearsExperience ?? ""));
  const [saveError, setSaveError] = useState<string | null>(null);
  const [saveSuccess, setSaveSuccess] = useState(false);

  // Sync fields when profile loads
  useEffect(() => {
    if (profile) {
      setSector(profile.preferredSector ?? "");
      setLocation(profile.preferredLocation ?? "");
      setRemote(profile.remoteOk ?? false);
      setYears(profile.yearsExperience != null ? String(profile.yearsExperience) : "");
    }
  }, [profile]);

  const handleSave = async (e: FormEvent) => {
    e.preventDefault();
    setSaveError(null);
    setSaveSuccess(false);
    const yearsNum = years === "" ? null : parseInt(years, 10);
    if (years !== "" && (isNaN(yearsNum!) || yearsNum! < 0 || yearsNum! > 60)) {
      setSaveError("Years of experience must be between 0 and 60.");
      return;
    }
    try {
      await onSave({
        preferredSector: sector.trim() || null,
        preferredLocation: location.trim() || null,
        remoteOk: remote,
        yearsExperience: yearsNum,
      });
      setSaveSuccess(true);
      setEditing(false);
      setTimeout(() => setSaveSuccess(false), 3000);
    } catch {
      setSaveError("Failed to save profile. Please try again.");
    }
  };

  if (!profile) {
    return (
      <div style={{ display: "flex", justifyContent: "center", padding: "1.5rem" }}>
        <Spinner />
      </div>
    );
  }

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "1rem" }}>
      {saveSuccess && <Alert tone="success">Profile saved successfully.</Alert>}
      {saveError && <Alert tone="error">{saveError}</Alert>}

      {!editing ? (
        <>
          {/* Read view */}
          <div style={{ display: "flex", flexDirection: "column", gap: "0.625rem" }}>
            <ProfileRow label="Preferred sector" value={profile.preferredSector} />
            <ProfileRow label="Preferred location" value={profile.preferredLocation} />
            <ProfileRow label="Open to remote" value={profile.remoteOk === true ? "Yes" : profile.remoteOk === false ? "No" : null} />
            <ProfileRow label="Years of experience" value={profile.yearsExperience != null ? String(profile.yearsExperience) : null} />
          </div>
          <Button variant="secondary" size="sm" onClick={() => setEditing(true)} style={{ alignSelf: "flex-start", marginTop: "0.25rem" }}>
            Edit profile
          </Button>
        </>
      ) : (
        <form onSubmit={(e) => void handleSave(e)} style={{ display: "flex", flexDirection: "column", gap: "0.875rem" }}>
          <Input
            label="Preferred sector"
            type="text"
            value={sector}
            onChange={(e) => setSector(e.target.value)}
            placeholder="e.g. Backend, Data, Design"
          />
          <Input
            label="Preferred location"
            type="text"
            value={location}
            onChange={(e) => setLocation(e.target.value)}
            placeholder="e.g. Kathmandu, Remote"
          />
          <div style={{ display: "flex", flexDirection: "column", gap: "0.375rem" }}>
            <label style={{ fontSize: "0.8125rem", fontWeight: 500, color: "var(--ink-2)", letterSpacing: "0.02em" }}>
              Years of experience
            </label>
            <input
              type="number"
              min={0}
              max={60}
              value={years}
              onChange={(e) => setYears(e.target.value)}
              placeholder="e.g. 3"
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
          <div style={{ display: "flex", alignItems: "center", gap: "0.625rem" }}>
            <input
              type="checkbox"
              id="remote-ok"
              checked={remote}
              onChange={(e) => setRemote(e.target.checked)}
              style={{ width: 16, height: 16, accentColor: "var(--accent)", cursor: "pointer" }}
            />
            <label
              htmlFor="remote-ok"
              style={{ fontSize: "0.875rem", color: "var(--ink-2)", cursor: "pointer", userSelect: "none" }}
            >
              Open to remote work
            </label>
          </div>
          <div style={{ display: "flex", gap: "0.5rem", marginTop: "0.25rem" }}>
            <Button type="submit" size="sm" loading={saving}>Save changes</Button>
            <Button type="button" variant="ghost" size="sm" onClick={() => { setEditing(false); setSaveError(null); }}>
              Cancel
            </Button>
          </div>
        </form>
      )}
    </div>
  );
}

function ProfileRow({ label, value }: { label: string; value: string | null | undefined }) {
  return (
    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline", gap: "1rem" }}>
      <span style={{ fontSize: "0.8125rem", color: "var(--ink-muted)", flexShrink: 0 }}>{label}</span>
      <span style={{ fontSize: "0.875rem", color: value ? "var(--ink-2)" : "var(--ink-faint)", textAlign: "right" }}>
        {value || "—"}
      </span>
    </div>
  );
}

/* ── Main dashboard ───────────────────────────────────────────── */
export default function SeekerDashboard() {
  const { setUser } = useAuth();

  // Upload state
  const [file, setFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [uploadSuccess, setUploadSuccess] = useState<string | null>(null);

  // History state
  const [history, setHistory] = useState<ResumeHistoryResponse | null>(null);
  const [historyLoading, setHistoryLoading] = useState(true);
  const [historyError, setHistoryError] = useState<string | null>(null);

  // Resume drawer
  const [drawerResume, setDrawerResume] = useState<ResumeDrawerItem | null>(null);

  // Profile state
  const [profile, setProfile] = useState<SeekerProfile | null>(null);
  const [profileLoading, setProfileLoading] = useState(true);
  const [profileSaving, setProfileSaving] = useState(false);

  const fileInputRef = useRef<HTMLInputElement>(null);

  const loadHistory = async () => {
    setHistoryLoading(true);
    setHistoryError(null);
    try {
      const result = await getSeekerResumeHistory({ limit: 5, offset: 0 });
      setHistory(result);
    } catch {
      setHistoryError("Could not load resume history.");
    } finally {
      setHistoryLoading(false);
    }
  };

  const loadProfile = async () => {
    setProfileLoading(true);
    try {
      const result = await getSeekerProfile();
      setProfile(result);
    } catch {
      // Profile load failure is non-fatal
    } finally {
      setProfileLoading(false);
    }
  };

  useEffect(() => {
    void loadHistory();
    void loadProfile();
  }, []);

  // The resume actually used for scoring is the most recently *parsed* one
  // (mirrors MatchingFacade / findFirstBySeekerIdAndProcessingStatusOrder...),
  // not just the most recently uploaded row.
  const latestParsedResume = useMemo(() => {
    const parsedItems = (history?.items ?? []).filter((item) => item.status === "PARSED");
    if (parsedItems.length === 0) return null;
    return parsedItems.reduce((latest, item) => {
      const latestTime = latest.parsedAt ? new Date(latest.parsedAt).getTime() : 0;
      const itemTime = item.parsedAt ? new Date(item.parsedAt).getTime() : 0;
      return itemTime > latestTime ? item : latest;
    });
  }, [history]);

  const handleUpload = async (e: FormEvent) => {
    e.preventDefault();
    if (!file) return;
    setUploading(true);
    setUploadError(null);
    setUploadSuccess(null);
    try {
      const result = await uploadSeekerResume(file);
      setUploadSuccess(
        result.status === "PARSED"
          ? "Resume uploaded and parsed successfully."
          : "Resume uploaded — processing in progress."
      );
      setFile(null);
      if (fileInputRef.current) fileInputRef.current.value = "";
      await loadHistory();
    } catch (err) {
      const msg = err instanceof Error ? err.message : "";
      if (msg.includes("ERR_UPLOAD_001")) setUploadError("Only PDF files are supported.");
      else if (msg.includes("ERR_UPLOAD_002")) setUploadError("File is too large. Maximum size is 10 MB.");
      else if (msg.includes("ERR_PARSE_001")) setUploadError("Parsing failed. Try a different PDF.");
      else if (msg.includes("ERR_PARSE_002")) setUploadError("No readable text found in this PDF.");
      else setUploadError("Upload failed. Please try again.");
    } finally {
      setUploading(false);
    }
  };

  const handleProfileSave = async (data: Partial<SeekerProfile>) => {
    setProfileSaving(true);
    try {
      const updated = await updateSeekerProfile(data);
      setProfile(updated);
    } finally {
      setProfileSaving(false);
    }
  };

  const hasReadyResume = history?.items.some((i) => i.status === "PARSED") ?? false;

  return (
    <main style={{ maxWidth: 1100, margin: "0 auto", padding: "2rem 1.5rem" }}>
      {/* Drawer */}
      {drawerResume && (
        <ResumeDrawer resume={drawerResume} onClose={() => setDrawerResume(null)} />
      )}

      {/* Page header */}
      <div style={{ marginBottom: "2rem" }}>
        <h1 style={{ marginBottom: "0.375rem" }}>My Dashboard</h1>
        <p style={{ color: "var(--ink-muted)" }}>
          Upload your resume, manage your profile, then explore matches tailored to your skills.
        </p>
      </div>

      {/* Top row: Upload + History */}
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "1.5rem", marginBottom: "1.5rem" }}>
        {/* ── Upload card ── */}
        <Card style={{ display: "flex", flexDirection: "column", gap: "1rem" }}>
          <div>
            <h3 style={{ marginBottom: "0.25rem" }}>Resume</h3>
            <p style={{ fontSize: "0.875rem", color: "var(--ink-muted)" }}>
              PDF only · max 10 MB · we parse and match automatically.
            </p>
          </div>

          <form onSubmit={(e) => void handleUpload(e)} style={{ display: "flex", flexDirection: "column", gap: "0.875rem" }}>
            {uploadError && <Alert tone="error">{uploadError}</Alert>}
            {uploadSuccess && <Alert tone="success">{uploadSuccess}</Alert>}

            <label
              htmlFor="resume-input"
              style={{
                display: "block",
                border: `2px dashed ${file ? "var(--accent)" : "var(--border)"}`,
                borderRadius: "var(--radius)",
                padding: "1.5rem",
                textAlign: "center",
                cursor: "pointer",
                background: file ? "var(--accent-faint)" : "var(--bg-subtle)",
                transition: "border-color 0.15s, background 0.15s",
              }}
            >
              <input
                id="resume-input"
                ref={fileInputRef}
                type="file"
                accept=".pdf,application/pdf"
                className="sr-only"
                onChange={(e) => {
                  setFile(e.target.files?.[0] ?? null);
                  setUploadError(null);
                  setUploadSuccess(null);
                }}
              />
              <div style={{ fontSize: "1.75rem", marginBottom: "0.5rem" }}>
                {file ? "📄" : "⬆"}
              </div>
              <div style={{ fontWeight: 500, color: "var(--ink-2)", fontSize: "0.9375rem" }}>
                {file ? file.name : "Click to select your PDF resume"}
              </div>
              {file && (
                <div style={{ fontSize: "0.8125rem", color: "var(--ink-muted)", marginTop: "0.25rem" }}>
                  {(file.size / 1024 / 1024).toFixed(2)} MB
                </div>
              )}
            </label>

            <Button type="submit" fullWidth loading={uploading} disabled={!file}>
              {uploading ? "Uploading…" : "Upload resume"}
            </Button>
          </form>

          {hasReadyResume && (
            <Link
              to="/seeker/matches"
              style={{
                display: "block",
                textAlign: "center",
                padding: "0.625rem",
                background: "var(--accent-faint)",
                color: "var(--accent)",
                borderRadius: "var(--radius-sm)",
                fontWeight: 500,
                fontSize: "0.9375rem",
                textDecoration: "none",
              }}
            >
              View job matches →
            </Link>
          )}
        </Card>

        {/* ── Resume history card ── */}
        <Card>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "1rem" }}>
            <h3>Upload history</h3>
            {history && history.page.total > 5 && (
              <span style={{ fontSize: "0.8125rem", color: "var(--ink-muted)" }}>
                {history.page.total} total
              </span>
            )}
          </div>
          {historyLoading && (
            <div style={{ display: "flex", justifyContent: "center", padding: "1.5rem" }}>
              <Spinner />
            </div>
          )}
          {historyError && <Alert tone="error">{historyError}</Alert>}
          {!historyLoading && !historyError && history?.items.length === 0 && (
            <p style={{ color: "var(--ink-muted)", fontSize: "0.875rem" }}>
              No resumes uploaded yet.
            </p>
          )}
          {!historyLoading && (history?.items ?? []).length > 0 && (
            <div style={{ display: "flex", flexDirection: "column", gap: "0.625rem" }}>
              {history!.items.map((item) => (
                <button
                  key={item.resumeId}
                  onClick={() => setDrawerResume(item)}
                  style={{
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "space-between",
                    padding: "0.75rem",
                    background: "var(--bg-subtle)",
                    borderRadius: "var(--radius-sm)",
                    gap: "0.75rem",
                    border: "1px solid transparent",
                    cursor: "pointer",
                    fontFamily: "var(--font-body)",
                    width: "100%",
                    textAlign: "left",
                    transition: "border-color 0.15s, background 0.15s",
                  }}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.borderColor = "var(--border)";
                    e.currentTarget.style.background = "var(--bg-card)";
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.borderColor = "transparent";
                    e.currentTarget.style.background = "var(--bg-subtle)";
                  }}
                >
                  <div style={{ minWidth: 0 }}>
                    <div style={{ fontWeight: 500, fontSize: "0.875rem", color: "var(--ink)", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                      {item.originalFilename}
                    </div>
                    <div style={{ fontSize: "0.75rem", color: "var(--ink-muted)", marginTop: "0.125rem" }}>
                      {formatDate(item.parsedAt ?? item.createdAt)}
                    </div>
                  </div>
                  <div style={{ display: "flex", alignItems: "center", gap: "0.5rem", flexShrink: 0 }}>
                    <Badge tone={statusTone(item.status)}>{statusLabel(item.status)}</Badge>
                    <span style={{ color: "var(--ink-faint)", fontSize: "0.875rem" }}>›</span>
                  </div>
                </button>
              ))}
            </div>
          )}
        </Card>
      </div>

      {/* Skills from resume */}
      {latestParsedResume && (
        <Card style={{ marginBottom: "1.5rem" }}>
          <div style={{ marginBottom: "1rem" }}>
            <h3 style={{ marginBottom: "0.25rem" }}>Skills from your resume</h3>
            <p style={{ fontSize: "0.8125rem", color: "var(--ink-muted)" }}>
              Detected from {latestParsedResume.originalFilename}. These drive your skills-overlap score on job matches.
            </p>
          </div>
          {latestParsedResume.inferredSkills.length > 0 ? (
            <div style={{ display: "flex", flexWrap: "wrap", gap: "0.45rem" }}>
              {latestParsedResume.inferredSkills.map((skill) => (
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
          ) : (
            <p style={{ fontSize: "0.875rem", color: "var(--ink-muted)" }}>
              No specific skills were detected. Try a resume with more explicit technology or tool names.
            </p>
          )}
        </Card>
      )}

      {/* Bottom row: Profile */}
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "1.5rem" }}>
        {/* ── Profile card ── */}
        <Card>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "1.25rem" }}>
            <div>
              <h3 style={{ marginBottom: "0.125rem" }}>My profile</h3>
              <p style={{ fontSize: "0.8125rem", color: "var(--ink-muted)" }}>
                These details improve your match accuracy.
              </p>
            </div>
            {profileLoading && <Spinner size={18} />}
          </div>
          <ProfilePanel
            profile={profileLoading ? null : profile}
            saving={profileSaving}
            onSave={handleProfileSave}
          />
        </Card>

        {/* ── Tip or CTA card ── */}
        {!hasReadyResume && !historyLoading ? (
          <div
            style={{
              padding: "1.5rem",
              background: "var(--accent-faint)",
              border: "1px solid var(--accent)",
              borderRadius: "var(--radius)",
              display: "flex",
              flexDirection: "column",
              gap: "0.75rem",
              justifyContent: "center",
            }}
          >
            <span style={{ fontSize: "2rem" }}>💡</span>
            <div>
              <p style={{ fontWeight: 600, color: "var(--accent)", marginBottom: "0.375rem" }}>
                Upload a resume to unlock matches
              </p>
              <p style={{ fontSize: "0.875rem", color: "var(--ink-2)" }}>
                JobVault reads your resume, identifies your skills, and ranks open positions by fit — all automatically.
              </p>
            </div>
          </div>
        ) : hasReadyResume ? (
          <div
            style={{
              padding: "1.5rem",
              background: "var(--success-faint)",
              border: "1px solid var(--success)",
              borderRadius: "var(--radius)",
              display: "flex",
              flexDirection: "column",
              gap: "0.875rem",
              justifyContent: "center",
            }}
          >
            <span style={{ fontSize: "2rem" }}>🎯</span>
            <div>
              <p style={{ fontWeight: 600, color: "var(--success)", marginBottom: "0.375rem" }}>
                Resume ready — see your matches
              </p>
              <p style={{ fontSize: "0.875rem", color: "var(--ink-2)", marginBottom: "0.75rem" }}>
                Your profile and resume are set. Explore ranked job matches with skill gap analysis.
              </p>
              <Link to="/seeker/matches">
                <Button size="sm">View matches →</Button>
              </Link>
            </div>
          </div>
        ) : null}
      </div>
    </main>
  );
}
