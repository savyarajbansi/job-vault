import { FormEvent, useEffect, useRef, useState } from "react";
import {
  getSeekerProfile,
  getSeekerResumeHistory,
  updateSeekerProfile,
  uploadSeekerResume,
} from "../api/seeker";
import type { ResumeHistoryItem, SeekerProfile } from "../api/seeker";
import { ApiResponseError } from "../api/client";
import { Alert, Badge, Button, Card, Divider, Input, Spinner } from "../components/ui";

/* ── Helpers ─────────────────────────────────────────────────── */

function resumeStatusTone(status: string): "success" | "warn" | "neutral" | "accent" {
  if (status === "PARSED") return "success";
  if (status === "FAILED") return "warn";
  if (status === "PARSING") return "accent";
  return "neutral";
}

function formatDate(iso: string | null): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function profileErrorMessage(error: unknown): string {
  if (error instanceof Error && error.message.includes("ERR_AUTH_003")) {
    return "Your session has ended. Please sign in again.";
  }
  if (error instanceof ApiResponseError) {
    const payload = error.payload as Record<string, unknown> | null;
    const details = payload?.["details"] as Record<string, unknown> | undefined;
    if (details?.["reason"] === "validation_failed") {
      const fields = details["fields"] as Record<string, string> | undefined;
      if (fields) {
        const first = Object.values(fields).find(Boolean);
        if (first) return first;
      }
    }
  }
  return "Could not save profile. Please try again.";
}

/* ── Profile summary card ────────────────────────────────────── */

function ProfileSummaryCard({
  profile,
  history,
}: {
  profile: SeekerProfile;
  history: ResumeHistoryItem[];
}) {
  const latestParsed = history.find((r) => r.status === "PARSED");
  const hasAnyInfo =
    profile.preferredSector ||
    profile.preferredLocation ||
    profile.yearsExperience != null ||
    profile.remoteOk != null ||
    latestParsed;

  if (!hasAnyInfo) {
    return (
      <Card>
        <p style={{ color: "var(--ink-muted)", fontSize: "0.875rem" }}>
          Your profile is empty. Fill in your preferences below and upload a resume to get
          started.
        </p>
      </Card>
    );
  }

  const topSkills = latestParsed ? latestParsed.inferredSkills.slice(0, 10) : [];

  return (
    <Card>
      <h2 style={{ fontSize: "1.0625rem", marginBottom: "1rem" }}>Profile summary</h2>

      <div style={{ display: "flex", flexDirection: "column", gap: "0.75rem" }}>
        {/* Sector */}
        {profile.preferredSector && (
          <div style={{ display: "flex", gap: "0.75rem", alignItems: "baseline" }}>
            <span
              style={{
                fontSize: "0.75rem",
                fontWeight: 600,
                letterSpacing: "0.06em",
                textTransform: "uppercase",
                color: "var(--ink-muted)",
                minWidth: 90,
                flexShrink: 0,
              }}
            >
              Sector
            </span>
            <span style={{ fontSize: "0.9375rem", color: "var(--ink-2)" }}>
              {profile.preferredSector}
            </span>
          </div>
        )}

        {/* Location */}
        {profile.preferredLocation && (
          <div style={{ display: "flex", gap: "0.75rem", alignItems: "baseline" }}>
            <span
              style={{
                fontSize: "0.75rem",
                fontWeight: 600,
                letterSpacing: "0.06em",
                textTransform: "uppercase",
                color: "var(--ink-muted)",
                minWidth: 90,
                flexShrink: 0,
              }}
            >
              Location
            </span>
            <span style={{ fontSize: "0.9375rem", color: "var(--ink-2)" }}>
              {profile.preferredLocation}
            </span>
          </div>
        )}

        {/* Experience */}
        {profile.yearsExperience != null && (
          <div style={{ display: "flex", gap: "0.75rem", alignItems: "baseline" }}>
            <span
              style={{
                fontSize: "0.75rem",
                fontWeight: 600,
                letterSpacing: "0.06em",
                textTransform: "uppercase",
                color: "var(--ink-muted)",
                minWidth: 90,
                flexShrink: 0,
              }}
            >
              Experience
            </span>
            <span style={{ fontSize: "0.9375rem", color: "var(--ink-2)" }}>
              {profile.yearsExperience} year{profile.yearsExperience !== 1 ? "s" : ""}
            </span>
          </div>
        )}

        {/* Remote */}
        {profile.remoteOk != null && (
          <div style={{ display: "flex", gap: "0.75rem", alignItems: "baseline" }}>
            <span
              style={{
                fontSize: "0.75rem",
                fontWeight: 600,
                letterSpacing: "0.06em",
                textTransform: "uppercase",
                color: "var(--ink-muted)",
                minWidth: 90,
                flexShrink: 0,
              }}
            >
              Remote
            </span>
            <Badge tone={profile.remoteOk ? "accent" : "neutral"}>
              {profile.remoteOk ? "Open to remote" : "On-site preferred"}
            </Badge>
          </div>
        )}

        {/* Resume status */}
        {latestParsed && (
          <div style={{ display: "flex", gap: "0.75rem", alignItems: "baseline" }}>
            <span
              style={{
                fontSize: "0.75rem",
                fontWeight: 600,
                letterSpacing: "0.06em",
                textTransform: "uppercase",
                color: "var(--ink-muted)",
                minWidth: 90,
                flexShrink: 0,
              }}
            >
              Resume
            </span>
            <div style={{ display: "flex", alignItems: "center", gap: "0.5rem", flexWrap: "wrap" }}>
              <Badge tone="success">Parsed</Badge>
              <span style={{ fontSize: "0.8125rem", color: "var(--ink-muted)" }}>
                {latestParsed.originalFilename}
              </span>
            </div>
          </div>
        )}

        {/* Skills from resume */}
        {topSkills.length > 0 && (
          <div style={{ display: "flex", gap: "0.75rem", alignItems: "flex-start" }}>
            <span
              style={{
                fontSize: "0.75rem",
                fontWeight: 600,
                letterSpacing: "0.06em",
                textTransform: "uppercase",
                color: "var(--ink-muted)",
                minWidth: 90,
                flexShrink: 0,
                paddingTop: "0.25rem",
              }}
            >
              Skills
            </span>
            <div style={{ display: "flex", flexWrap: "wrap", gap: "0.35rem" }}>
              {topSkills.map((skill) => (
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
              {latestParsed && latestParsed.inferredSkills.length > 10 && (
                <span
                  style={{
                    padding: "0.2rem 0.55rem",
                    background: "var(--bg-subtle)",
                    color: "var(--ink-muted)",
                    borderRadius: "999px",
                    fontSize: "0.75rem",
                    fontWeight: 500,
                  }}
                >
                  +{latestParsed.inferredSkills.length - 10} more
                </span>
              )}
            </div>
          </div>
        )}
      </div>
    </Card>
  );
}

/* ── Resume history row ──────────────────────────────────────── */

function ResumeRow({ item, isLatest }: { item: ResumeHistoryItem; isLatest: boolean }) {
  const [expanded, setExpanded] = useState(false);

  return (
    <div
      style={{
        padding: "0.875rem 1.25rem",
        borderBottom: "1px solid var(--border)",
        display: "flex",
        flexDirection: "column",
        gap: "0.5rem",
      }}
    >
      <div
        style={{
          display: "flex",
          alignItems: "flex-start",
          justifyContent: "space-between",
          gap: "1rem",
        }}
      >
        <div style={{ minWidth: 0 }}>
          <div
            style={{
              display: "flex",
              alignItems: "center",
              gap: "0.5rem",
              flexWrap: "wrap",
              marginBottom: "0.25rem",
            }}
          >
            <span
              style={{
                fontWeight: 500,
                fontSize: "0.9375rem",
                color: "var(--ink)",
                overflow: "hidden",
                textOverflow: "ellipsis",
                whiteSpace: "nowrap",
                maxWidth: 240,
              }}
            >
              {item.originalFilename}
            </span>
            <Badge tone={resumeStatusTone(item.status)}>{item.status}</Badge>
            {isLatest && <Badge tone="accent">Current</Badge>}
          </div>
          <div
            style={{
              display: "flex",
              gap: "1rem",
              color: "var(--ink-muted)",
              fontSize: "0.8125rem",
              flexWrap: "wrap",
            }}
          >
            <span>Uploaded {formatDate(item.createdAt)}</span>
            {item.parsedAt && <span>Parsed {formatDate(item.parsedAt)}</span>}
          </div>
          {item.failureCode && (
            <p style={{ fontSize: "0.8125rem", color: "var(--warn)", marginTop: "0.25rem" }}>
              Error: {item.failureCode}
            </p>
          )}
        </div>

        {item.inferredSkills.length > 0 && (
          <button
            type="button"
            onClick={() => setExpanded((v) => !v)}
            style={{
              border: "none",
              background: "none",
              color: "var(--accent)",
              fontSize: "0.8125rem",
              fontWeight: 500,
              cursor: "pointer",
              flexShrink: 0,
              fontFamily: "var(--font-body)",
            }}
          >
            {expanded ? "Hide skills" : `${item.inferredSkills.length} skills`}
          </button>
        )}
      </div>

      {expanded && item.inferredSkills.length > 0 && (
        <div style={{ display: "flex", flexWrap: "wrap", gap: "0.35rem", paddingTop: "0.25rem" }}>
          {item.inferredSkills.map((skill) => (
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
      )}
    </div>
  );
}

/* ── Upload zone (compact) ───────────────────────────────────── */

function ResumeUploadZone({ onSuccess }: { onSuccess: () => void }) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragOver, setDragOver] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  const handleFile = async (file: File) => {
    if (!file.name.toLowerCase().endsWith(".pdf") && file.type !== "application/pdf") {
      setError("Only PDF files are accepted.");
      return;
    }
    if (file.size > 10 * 1024 * 1024) {
      setError("File must be under 10 MB.");
      return;
    }

    setError(null);
    setSuccessMsg(null);
    setUploading(true);

    try {
      await uploadSeekerResume(file);
      setSuccessMsg(`${file.name} uploaded and parsed.`);
      onSuccess();
    } catch (err) {
      if (err instanceof ApiResponseError) {
        const payload = err.payload as Record<string, unknown> | null;
        const code = payload?.["code"] as string | undefined;
        if (code === "ERR_UPLOAD_001") {
          setError("Only PDF files are accepted.");
        } else if (code === "ERR_UPLOAD_002") {
          setError("File exceeds the 10 MB limit.");
        } else if (code === "ERR_PARSE_001" || code === "ERR_PARSE_002") {
          setError("The PDF could not be parsed. Make sure it contains selectable text.");
        } else {
          setError("Upload failed. Please try again.");
        }
      } else if (err instanceof Error && err.message.includes("ERR_AUTH_003")) {
        setError("Your session has ended. Please sign in again.");
      } else {
        setError("Upload failed. Please try again.");
      }
    } finally {
      setUploading(false);
    }
  };

  const onDrop = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setDragOver(false);
    const file = e.dataTransfer.files[0];
    if (file) void handleFile(file);
  };

  const onInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) void handleFile(file);
    e.target.value = "";
  };

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "0.625rem" }}>
      {/* Compact drop zone */}
      <div
        role="button"
        tabIndex={0}
        aria-label="Upload resume PDF"
        onDragOver={(e) => { e.preventDefault(); setDragOver(true); }}
        onDragLeave={() => setDragOver(false)}
        onDrop={onDrop}
        onClick={() => inputRef.current?.click()}
        onKeyDown={(e) => { if (e.key === "Enter" || e.key === " ") inputRef.current?.click(); }}
        style={{
          border: `2px dashed ${dragOver ? "var(--accent)" : "var(--border)"}`,
          borderRadius: "var(--radius)",
          background: dragOver ? "var(--accent-faint)" : "var(--bg-subtle)",
          padding: "1.25rem 1rem",
          textAlign: "center",
          cursor: uploading ? "not-allowed" : "pointer",
          transition: "border-color 0.15s, background 0.15s",
          opacity: uploading ? 0.7 : 1,
        }}
      >
        <input
          ref={inputRef}
          type="file"
          accept="application/pdf,.pdf"
          onChange={onInputChange}
          disabled={uploading}
          style={{ display: "none" }}
        />

        {uploading ? (
          <div style={{ display: "flex", alignItems: "center", justifyContent: "center", gap: "0.75rem" }}>
            <Spinner size={20} />
            <span style={{ color: "var(--ink-muted)", fontSize: "0.875rem" }}>Uploading and parsing...</span>
          </div>
        ) : (
          <div style={{ display: "flex", alignItems: "center", justifyContent: "center", gap: "0.75rem" }}>
            <svg
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="var(--accent)"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
            >
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
              <polyline points="17 8 12 3 7 8" />
              <line x1="12" y1="3" x2="12" y2="15" />
            </svg>
            <div style={{ textAlign: "left" }}>
              <p style={{ color: "var(--ink-2)", fontWeight: 500, fontSize: "0.875rem", margin: 0 }}>
                Drop a PDF here, or click to browse
              </p>
              <p style={{ color: "var(--ink-muted)", fontSize: "0.75rem", margin: 0 }}>
                PDF only — max 10 MB
              </p>
            </div>
          </div>
        )}
      </div>

      {error && <Alert tone="error">{error}</Alert>}
      {successMsg && <Alert tone="success">{successMsg}</Alert>}
    </div>
  );
}

/* ── Profile form ────────────────────────────────────────────── */

function ProfileForm({
  profile,
  onSaved,
}: {
  profile: SeekerProfile;
  onSaved: (updated: SeekerProfile) => void;
}) {
  const [sector, setSector] = useState(profile.preferredSector ?? "");
  const [location, setLocation] = useState(profile.preferredLocation ?? "");
  const [remoteOk, setRemoteOk] = useState<boolean>(profile.remoteOk ?? false);
  const [yearsExperience, setYearsExperience] = useState(
    profile.yearsExperience != null ? String(profile.yearsExperience) : ""
  );
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setSaved(false);

    const parsedYears = yearsExperience.trim() === "" ? null : Number(yearsExperience);

    if (
      parsedYears !== null &&
      (Number.isNaN(parsedYears) || parsedYears < 0 || parsedYears > 60)
    ) {
      setError("Years of experience must be between 0 and 60.");
      return;
    }

    setSaving(true);
    try {
      const updated = await updateSeekerProfile({
        preferredSector: sector.trim() || null,
        preferredLocation: location.trim() || null,
        remoteOk,
        yearsExperience: parsedYears,
      });
      onSaved(updated);
      setSaved(true);
      setTimeout(() => setSaved(false), 3000);
    } catch (err) {
      setError(profileErrorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <form onSubmit={(e) => void handleSubmit(e)} noValidate>
      <div style={{ display: "flex", flexDirection: "column", gap: "1rem" }}>
        {error && <Alert tone="error">{error}</Alert>}
        {saved && <Alert tone="success">Profile saved.</Alert>}

        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "1rem" }}>
          <Input
            label="Preferred sector"
            type="text"
            value={sector}
            onChange={(e) => setSector(e.target.value)}
            placeholder="e.g. Backend Engineering"
            maxLength={100}
          />
          <Input
            label="Preferred location"
            type="text"
            value={location}
            onChange={(e) => setLocation(e.target.value)}
            placeholder="e.g. Austin, TX"
            maxLength={150}
          />
        </div>

        <Input
          label="Years of experience"
          type="number"
          min={0}
          max={60}
          value={yearsExperience}
          onChange={(e) => setYearsExperience(e.target.value)}
          placeholder="e.g. 5"
          hint="Used to match you against minimum experience requirements."
        />

        <label
          style={{
            display: "flex",
            alignItems: "center",
            gap: "0.75rem",
            fontSize: "0.9375rem",
            color: "var(--ink-2)",
            cursor: "pointer",
          }}
        >
          <input
            type="checkbox"
            checked={remoteOk}
            onChange={(e) => setRemoteOk(e.target.checked)}
            style={{ accentColor: "var(--accent)", width: 16, height: 16 }}
          />
          <span>Open to remote work</span>
        </label>

        <div style={{ display: "flex", justifyContent: "flex-end" }}>
          <Button type="submit" loading={saving}>
            Save profile
          </Button>
        </div>
      </div>
    </form>
  );
}

/* ── Main page ───────────────────────────────────────────────── */

export default function SeekerProfile() {
  const [profile, setProfile] = useState<SeekerProfile | null>(null);
  const [profileLoading, setProfileLoading] = useState(true);
  const [profileError, setProfileError] = useState<string | null>(null);

  const [history, setHistory] = useState<ResumeHistoryItem[]>([]);
  const [historyLoading, setHistoryLoading] = useState(true);

  const loadProfile = async () => {
    try {
      const result = await getSeekerProfile();
      setProfile(result);
      setProfileError(null);
    } catch (err) {
      const msg = err instanceof Error ? err.message : "";
      setProfileError(
        msg.includes("ERR_AUTH_003")
          ? "Your session has ended. Please sign in again."
          : "Could not load your profile."
      );
    } finally {
      setProfileLoading(false);
    }
  };

  const loadHistory = async () => {
    setHistoryLoading(true);
    try {
      const result = await getSeekerResumeHistory({ limit: 10, offset: 0 });
      setHistory(result.items);
    } catch {
      // non-fatal
    } finally {
      setHistoryLoading(false);
    }
  };

  useEffect(() => {
    void loadProfile();
    void loadHistory();
  }, []);

  const latestParsedId = history.find((r) => r.status === "PARSED")?.resumeId ?? null;
  const isLoading = profileLoading || historyLoading;

  return (
    <main style={{ maxWidth: 1100, margin: "0 auto", padding: "2rem 1.5rem 3rem" }}>
      {/* Page header */}
      <div style={{ marginBottom: "1.75rem" }}>
        <h1 style={{ marginBottom: "0.25rem" }}>Profile</h1>
        <p style={{ color: "var(--ink-muted)", fontSize: "0.9375rem" }}>
          Keep your preferences and resume up to date so your matches stay accurate.
        </p>
      </div>

      {isLoading ? (
        <div style={{ display: "flex", justifyContent: "center", padding: "4rem 0" }}>
          <Spinner size={32} />
        </div>
      ) : (
        /*
          Two-column layout:
          - Left (primary): profile summary + edit form
          - Right (secondary): resume upload + history
        */
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "3fr 2fr",
            gap: "1.5rem",
            alignItems: "start",
          }}
        >
          {/* ── Left column: summary + edit ── */}
          <div style={{ display: "flex", flexDirection: "column", gap: "1.5rem" }}>

            {/* Profile summary */}
            {profile && !profileError && (
              <ProfileSummaryCard profile={profile} history={history} />
            )}

            {/* Edit form */}
            <Card>
              <h2 style={{ fontSize: "1.0625rem", marginBottom: "0.25rem" }}>
                Matching preferences
              </h2>
              <p style={{ color: "var(--ink-muted)", fontSize: "0.875rem", marginBottom: "1.25rem" }}>
                These fields feed directly into the experience and location factors of your match
                scores. A populated field improves accuracy; leaving one blank means it won't
                factor in.
              </p>

              {profileError ? (
                <Alert tone="error">{profileError}</Alert>
              ) : profile ? (
                <ProfileForm
                  profile={profile}
                  onSaved={(updated) => setProfile(updated)}
                />
              ) : null}
            </Card>
          </div>

          {/* ── Right column: resume ── */}
          <div style={{ display: "flex", flexDirection: "column", gap: "1.5rem" }}>
            <Card>
              <h2 style={{ fontSize: "1.0625rem", marginBottom: "0.25rem" }}>Resume</h2>
              <p style={{ color: "var(--ink-muted)", fontSize: "0.875rem", marginBottom: "1.25rem" }}>
                Upload a PDF resume. Skills are extracted automatically and used to rank your job
                matches. Uploading a new resume replaces your active skill set.
              </p>

              <ResumeUploadZone
                onSuccess={() => {
                  void loadHistory();
                  void loadProfile();
                }}
              />

              {/* Resume history */}
              <div style={{ marginTop: "1.5rem" }}>
                <Divider label="Upload history" />
                <div style={{ marginTop: "1rem" }}>
                  {historyLoading ? (
                    <div style={{ display: "flex", justifyContent: "center", padding: "1.5rem 0" }}>
                      <Spinner size={22} />
                    </div>
                  ) : history.length === 0 ? (
                    <p style={{ color: "var(--ink-muted)", fontSize: "0.875rem" }}>
                      No resumes uploaded yet.
                    </p>
                  ) : (
                    <Card padded={false}>
                      {history.map((item) => (
                        <ResumeRow
                          key={item.resumeId}
                          item={item}
                          isLatest={item.resumeId === latestParsedId}
                        />
                      ))}
                    </Card>
                  )}
                </div>
              </div>
            </Card>
          </div>
        </div>
      )}
    </main>
  );
}
