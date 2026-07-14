import {
  FormEvent,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ChangeEvent,
  type DragEvent,
} from "react";

import { ApiResponseError } from "../api/client";
import {
  getSeekerProfile,
  getSeekerResumeHistory,
  updateSeekerProfile,
  uploadSeekerResume,
} from "../api/seeker";
import type { ResumeHistoryItem, SeekerProfile } from "../api/seeker";
import {
  Alert,
  Badge,
  Button,
  Card,
  EmptyState,
  Icon,
  Input,
  Modal,
  RowItem,
  RowList,
  SectionHeader,
  Spinner,
} from "../components/ui";
import PageLoader from "../components/PageLoader";

function resumeStatusTone(status: string): "success" | "warn" | "neutral" | "accent" {
  if (status === "PARSED") return "success";
  if (status === "FAILED") return "warn";
  if (status === "PARSING") return "accent";
  return "neutral";
}

function formatDate(iso: string | null): string {
  if (!iso) return "Unknown";
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

function uploadErrorMessage(error: unknown): string {
  if (error instanceof Error && error.message.includes("ERR_AUTH_003")) {
    return "Your session has ended. Please sign in again.";
  }
  if (error instanceof ApiResponseError) {
    const payload = error.payload as Record<string, unknown> | null;
    const code = payload?.["code"] as string | undefined;
    if (code === "ERR_UPLOAD_001") {
      return "Only PDF files are accepted.";
    }
    if (code === "ERR_UPLOAD_002") {
      return "File exceeds the 10 MB limit.";
    }
    if (code === "ERR_PARSE_001" || code === "ERR_PARSE_002") {
      return "The PDF could not be parsed. Make sure it contains selectable text.";
    }
  }
  return "Upload failed. Please try again.";
}

function historyErrorMessage(error: unknown): string {
  if (error instanceof Error && error.message.includes("ERR_AUTH_003")) {
    return "Your session has ended. Please sign in again.";
  }
  return "Could not load upload history. Please try again.";
}

function profileValue(value: string | number | null, fallback = "Not set"): string {
  if (value === null || value === "") return fallback;
  return String(value);
}

function ProfileDetail({
  icon,
  label,
  value,
  muted = false,
}: {
  icon: Parameters<typeof Icon>[0]["name"];
  label: string;
  value: string;
  muted?: boolean;
}) {
  return (
    <div
      style={{
        display: "flex",
        alignItems: "flex-start",
        gap: "0.75rem",
        padding: "0.875rem",
        borderRadius: "var(--radius-sm)",
        border: "1px solid var(--border)",
        background: "var(--bg-row)",
      }}
    >
      <div
        style={{
          width: 32,
          height: 32,
          borderRadius: "999px",
          background: "var(--bg-card)",
          border: "1px solid var(--border)",
          color: "var(--accent)",
          display: "inline-flex",
          alignItems: "center",
          justifyContent: "center",
          flexShrink: 0,
        }}
      >
        <Icon name={icon} size={15} />
      </div>
      <div style={{ minWidth: 0 }}>
        <div
          style={{
            fontSize: "0.6875rem",
            fontWeight: 600,
            letterSpacing: "0.07em",
            textTransform: "uppercase",
            color: "var(--ink-muted)",
            marginBottom: "0.2rem",
          }}
        >
          {label}
        </div>
        <div
          style={{
            fontSize: "0.9375rem",
            lineHeight: 1.45,
            color: muted ? "var(--ink-muted)" : "var(--ink-2)",
            fontWeight: 500,
            wordBreak: "break-word",
          }}
        >
          {value}
        </div>
      </div>
    </div>
  );
}

function ProfileForm({
  profile,
  onSaved,
  onCancel,
}: {
  profile: SeekerProfile;
  onSaved: (updated: SeekerProfile) => void;
  onCancel: () => void;
}) {
  const [sector, setSector] = useState(profile.preferredSector ?? "");
  const [location, setLocation] = useState(profile.preferredLocation ?? "");
  const [remoteOk, setRemoteOk] = useState<boolean | null>(profile.remoteOk);
  const [yearsExperience, setYearsExperience] = useState(
    profile.yearsExperience != null ? String(profile.yearsExperience) : ""
  );
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);

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
    } catch (err) {
      setError(profileErrorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <form onSubmit={(event) => void handleSubmit(event)} noValidate>
      <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-5)" }}>
        {error && <Alert tone="error">{error}</Alert>}

        <div style={{ display: "grid", gridTemplateColumns: "repeat(2, minmax(0, 1fr))", gap: "var(--space-4)" }}>
          <Input
            label="Preferred sector"
            type="text"
            value={sector}
            onChange={(event) => setSector(event.target.value)}
            placeholder="e.g. Backend Engineering"
            maxLength={100}
          />
          <Input
            label="Preferred location"
            type="text"
            value={location}
            onChange={(event) => setLocation(event.target.value)}
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
          onChange={(event) => setYearsExperience(event.target.value)}
          placeholder="e.g. 5"
          hint="Used when a job includes a minimum experience requirement."
        />

        <label style={{ display: "flex", flexDirection: "column", gap: "0.375rem", fontSize: "0.875rem", color: "var(--ink-2)" }}>
          Remote preference
          <select
            value={remoteOk == null ? "" : String(remoteOk)}
            onChange={(event) => setRemoteOk(event.target.value === "" ? null : event.target.value === "true")}
            style={{ padding: "0.65rem 0.75rem", border: "1px solid var(--border)", borderRadius: "var(--radius-sm)", background: "var(--bg-card)", color: "var(--ink)" }}
          >
            <option value="">Not specified</option>
            <option value="true">Open to remote work</option>
            <option value="false">Prefer on-site work</option>
          </select>
        </label>

        <div style={{ display: "flex", justifyContent: "space-between", gap: "0.75rem" }}>
          <Button type="button" variant="ghost" onClick={onCancel}>
            Cancel
          </Button>
          <Button type="submit" loading={saving}>
            Save profile
          </Button>
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
    if (!file.name.toLowerCase().endsWith(".pdf") && file.type !== "application/pdf") {
      setError("Only PDF files are accepted.");
      return;
    }
    if (file.size > 10 * 1024 * 1024) {
      setError("File must be under 10 MB.");
      return;
    }

    setError(null);
    setUploading(true);

    try {
      await uploadSeekerResume(file);
      onSuccess();
    } catch (err) {
      setError(uploadErrorMessage(err));
    } finally {
      setUploading(false);
    }
  };

  const onDrop = (event: DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    setDragOver(false);
    const file = event.dataTransfer.files[0];
    if (file) void handleFile(file);
  };

  const onInputChange = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (file) void handleFile(file);
    event.target.value = "";
  };

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-4)" }}>
      <div
        role="button"
        tabIndex={0}
        aria-label="Upload resume PDF"
        onDragOver={(event) => {
          event.preventDefault();
          setDragOver(true);
        }}
        onDragLeave={() => setDragOver(false)}
        onDrop={onDrop}
        onClick={() => inputRef.current?.click()}
        onKeyDown={(event) => {
          if (event.key === "Enter" || event.key === " ") {
            inputRef.current?.click();
          }
        }}
        style={{
          border: `2px dashed ${dragOver ? "var(--accent)" : "var(--border)"}`,
          borderRadius: "var(--radius)",
          background: dragOver ? "var(--accent-faint)" : "var(--bg-subtle)",
          padding: "1.5rem 1rem",
          textAlign: "center",
          cursor: uploading ? "not-allowed" : "pointer",
          transition: "border-color 0.15s, background 0.15s",
          opacity: uploading ? 0.75 : 1,
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
          <div style={{ display: "flex", alignItems: "center", justifyContent: "center", gap: "0.875rem" }}>
            <div
              style={{
                width: 36,
                height: 36,
                borderRadius: "999px",
                background: "var(--bg-card)",
                border: "1px solid var(--border)",
                color: "var(--accent)",
                display: "inline-flex",
                alignItems: "center",
                justifyContent: "center",
                flexShrink: 0,
              }}
            >
              <Icon name="upload" size={16} />
            </div>
            <div style={{ textAlign: "left" }}>
              <p style={{ color: "var(--ink-2)", fontWeight: 500, fontSize: "0.875rem", margin: 0 }}>
                Drop a PDF here, or click to browse
              </p>
              <p style={{ color: "var(--ink-muted)", fontSize: "0.75rem", margin: 0 }}>
                PDF only - max 10 MB
              </p>
            </div>
          </div>
        )}
      </div>

      {error && <Alert tone="error">{error}</Alert>}
    </div>
  );
}

function ResumeRow({
  item,
  isLatest,
}: {
  item: ResumeHistoryItem;
  isLatest: boolean;
}) {
  const [expanded, setExpanded] = useState(false);

  return (
    <RowItem
      interactive
      selected={expanded}
      onClick={() => setExpanded((current) => !current)}
      style={{
        alignItems: "stretch",
        flexDirection: "column",
      }}
    >
      <div
        style={{
          display: "flex",
          alignItems: "flex-start",
          justifyContent: "space-between",
          gap: "1rem",
          width: "100%",
        }}
      >
        <div style={{ minWidth: 0, flex: 1 }}>
          <div
            style={{
              display: "flex",
              alignItems: "center",
              gap: "0.5rem",
              flexWrap: "wrap",
              marginBottom: "0.35rem",
            }}
          >
            <span
              style={{
                fontSize: "0.9375rem",
                fontWeight: 500,
                color: "var(--ink)",
                overflow: "hidden",
                textOverflow: "ellipsis",
                whiteSpace: "nowrap",
                maxWidth: 260,
              }}
            >
              {item.originalFilename}
            </span>
            <Badge tone={resumeStatusTone(item.status)}>{item.status.replace(/_/g, " ")}</Badge>
            {isLatest && <Badge tone="accent">Current</Badge>}
            <Badge tone="neutral">
              {item.inferredSkills.length} skill{item.inferredSkills.length === 1 ? "" : "s"}
            </Badge>
          </div>
          <div
            style={{
              display: "flex",
              gap: "0.875rem",
              color: "var(--ink-muted)",
              fontSize: "0.8125rem",
              flexWrap: "wrap",
            }}
          >
            <span style={{ display: "inline-flex", alignItems: "center", gap: "0.35rem" }}>
              <Icon name="upload" size={12} />
              Uploaded {formatDate(item.createdAt)}
            </span>
            {item.parsedAt && (
              <span style={{ display: "inline-flex", alignItems: "center", gap: "0.35rem" }}>
                <Icon name="calendar" size={12} />
                Parsed {formatDate(item.parsedAt)}
              </span>
            )}
          </div>
          {item.failureCode && (
            <p style={{ fontSize: "0.8125rem", color: "var(--warn)", marginTop: "0.3rem" }}>
              Processing issue: {item.failureCode}
            </p>
          )}
        </div>

        <span
          style={{
            flexShrink: 0,
            width: 30,
            height: 30,
            borderRadius: "999px",
            border: "1px solid var(--border)",
            background: "var(--bg-card)",
            color: "var(--ink-muted)",
            display: "inline-flex",
            alignItems: "center",
            justifyContent: "center",
            marginTop: "0.1rem",
          }}
        >
          <Icon name={expanded ? "chevron-up" : "chevron-down"} size={14} />
        </span>
      </div>

      {expanded && (
        <div
          style={{
            display: "flex",
            flexWrap: "wrap",
            gap: "0.35rem",
            marginTop: "0.75rem",
          }}
        >
          {item.inferredSkills.length > 0 ? (
            item.inferredSkills.map((skill) => (
              <span
                key={skill}
                style={{
                  padding: "0.22rem 0.6rem",
                  background: "var(--accent-faint)",
                  color: "var(--accent)",
                  borderRadius: "999px",
                  fontSize: "0.75rem",
                  fontWeight: 500,
                }}
              >
                {skill}
              </span>
            ))
          ) : (
            <span style={{ color: "var(--ink-muted)", fontSize: "0.8125rem" }}>
              No extracted skills for this upload.
            </span>
          )}
        </div>
      )}
    </RowItem>
  );
}

function ProfileSnapshotCard({
  profile,
  latestParsed,
  onEdit,
}: {
  profile: SeekerProfile | null;
  latestParsed: ResumeHistoryItem | null;
  onEdit: () => void;
}) {
  const fieldCount = profile
    ? [profile.preferredSector, profile.preferredLocation, profile.yearsExperience, profile.remoteOk].filter(
        (value) => value !== null && value !== ""
      ).length
    : 0;

  return (
    <Card>
      <SectionHeader
        title="Profile overview"
        action={
          profile ? (
            <Badge tone={fieldCount >= 3 ? "success" : "accent"}>{fieldCount}/4 set</Badge>
          ) : null
        }
      />

      {profile ? (
        <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-5)" }}>
          <p style={{ color: "var(--ink-muted)", fontSize: "0.875rem", maxWidth: "58ch" }}>
            Employers can see these preferences. Location, experience, and remote preference are
            current matching signals; sector is retained for your profile but is not ranked yet.
          </p>

          <div
            style={{
              display: "grid",
              gridTemplateColumns: "repeat(2, minmax(0, 1fr))",
              gap: "0.75rem",
            }}
          >
            <ProfileDetail
              icon="briefcase"
              label="Preferred sector"
              value={profileValue(profile.preferredSector)}
              muted={!profile.preferredSector}
            />
            <ProfileDetail
              icon="location"
              label="Preferred location"
              value={profileValue(profile.preferredLocation)}
              muted={!profile.preferredLocation}
            />
            <ProfileDetail
              icon="clock"
              label="Experience"
              value={
                profile.yearsExperience != null
                  ? `${profile.yearsExperience} year${profile.yearsExperience === 1 ? "" : "s"}`
                  : "Not set"
              }
              muted={profile.yearsExperience == null}
            />
            <ProfileDetail
              icon="remote"
              label="Remote preference"
              value={
                profile.remoteOk == null
                  ? "Not set"
                  : profile.remoteOk
                    ? "Open to remote work"
                    : "On-site preferred"
              }
              muted={profile.remoteOk == null}
            />
          </div>

          <div
            style={{
              display: "flex",
              alignItems: "center",
              justifyContent: "space-between",
              gap: "1rem",
              padding: "0.9rem 1rem",
              borderRadius: "var(--radius-sm)",
              border: "1px solid var(--border)",
              background: "var(--bg-row)",
            }}
          >
            <div style={{ minWidth: 0 }}>
              <div
                style={{
                  fontSize: "0.6875rem",
                  fontWeight: 600,
                  letterSpacing: "0.07em",
                  textTransform: "uppercase",
                  color: "var(--ink-muted)",
                  marginBottom: "0.2rem",
                }}
              >
                Latest parsed resume
              </div>
              <div style={{ fontSize: "0.9375rem", color: "var(--ink-2)", fontWeight: 500 }}>
                {latestParsed ? latestParsed.originalFilename : "No parsed resume yet"}
              </div>
            </div>
            {latestParsed ? (
              <Badge tone="success">Ready</Badge>
            ) : (
              <Badge tone="neutral">Upload needed</Badge>
            )}
          </div>
        </div>
      ) : (
        <EmptyState
          title="No profile details yet"
          description="Add location, experience, and remote preference to make current matching signals more accurate."
          action={
            <Button onClick={onEdit} variant="secondary">
              <Icon name="edit" size={14} />
              Add profile details
            </Button>
          }
        />
      )}
    </Card>
  );
}

function ResumeSnapshotCard({
  history,
  latestParsed,
  historyError,
  onUpload,
  onRefreshHistory,
}: {
  history: ResumeHistoryItem[];
  latestParsed: ResumeHistoryItem | null;
  historyError: string | null;
  onUpload: () => void;
  onRefreshHistory: () => void;
}) {
  const hasHistoryError = historyError !== null;
  const hasHistory = history.length > 0;

  return (
    <Card>
      <SectionHeader
        title="Resume snapshot"
        action={
          latestParsed ? (
            <Badge tone="success">Parsed</Badge>
          ) : hasHistoryError ? (
            <Badge tone="warn">History unavailable</Badge>
          ) : (
            <Badge tone="neutral">No resume</Badge>
          )
        }
      />

      {hasHistoryError && hasHistory && (
        <div style={{ marginBottom: "var(--space-4)" }}>
          <Alert tone="error">{historyError}</Alert>
        </div>
      )}

      {latestParsed ? (
        <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-4)" }}>
          <div
            style={{
              display: "grid",
              gridTemplateColumns: "repeat(2, minmax(0, 1fr))",
              gap: "0.75rem",
            }}
          >
            <ProfileDetail
              icon="file"
              label="Current file"
              value={latestParsed.originalFilename}
            />
            <ProfileDetail
              icon="calendar"
              label="Parsed"
              value={formatDate(latestParsed.parsedAt)}
            />
          </div>

          <div style={{ display: "flex", alignItems: "center", gap: "0.5rem", flexWrap: "wrap" }}>
            <Badge tone="accent">{latestParsed.inferredSkills.length} skills detected</Badge>
            <span style={{ color: "var(--ink-muted)", fontSize: "0.875rem" }}>
              Skills extracted from your most recent parsed resume.
            </span>
          </div>

          {latestParsed.inferredSkills.length > 0 && (
            <div style={{ display: "flex", flexWrap: "wrap", gap: "0.35rem" }}>
              {latestParsed.inferredSkills.slice(0, 8).map((skill) => (
                <span
                  key={skill}
                  style={{
                    padding: "0.22rem 0.6rem",
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
              {latestParsed.inferredSkills.length > 8 && (
                <span
                  style={{
                    padding: "0.22rem 0.6rem",
                    background: "var(--bg-subtle)",
                    color: "var(--ink-muted)",
                    borderRadius: "999px",
                    fontSize: "0.75rem",
                    fontWeight: 500,
                  }}
                >
                  +{latestParsed.inferredSkills.length - 8} more
                </span>
              )}
            </div>
          )}
        </div>
      ) : hasHistoryError ? (
        <EmptyState
          title="Resume history unavailable"
          description={historyError ?? undefined}
          action={
            <Button onClick={onRefreshHistory} variant="secondary">
              Reload history
            </Button>
          }
        />
      ) : (
        <EmptyState
          title="No parsed resume yet"
          description="Upload a PDF resume to extract skills and unlock better matching."
          action={
            <Button onClick={onUpload}>
              <Icon name="upload" size={14} />
              Upload resume
            </Button>
          }
        />
      )}

      {hasHistory && (
        <p style={{ color: "var(--ink-muted)", fontSize: "0.8125rem", marginTop: "var(--space-4)" }}>
          {history.length} upload{history.length === 1 ? "" : "s"} in your history.
        </p>
      )}
    </Card>
  );
}

export default function SeekerProfile() {
  const [profile, setProfile] = useState<SeekerProfile | null>(null);
  const [profileLoading, setProfileLoading] = useState(true);
  const [profileError, setProfileError] = useState<string | null>(null);

  const [history, setHistory] = useState<ResumeHistoryItem[]>([]);
  const [historyLoading, setHistoryLoading] = useState(true);
  const [historyError, setHistoryError] = useState<string | null>(null);

  const [notice, setNotice] = useState<{ tone: "success" | "info"; message: string } | null>(null);
  const [profileModalOpen, setProfileModalOpen] = useState(false);
  const [uploadModalOpen, setUploadModalOpen] = useState(false);

  useEffect(() => {
    if (!notice) return;
    const timeout = window.setTimeout(() => setNotice(null), 4000);
    return () => window.clearTimeout(timeout);
  }, [notice]);

  const loadProfile = async (silent = false) => {
    if (!silent) setProfileLoading(true);
    try {
      const result = await getSeekerProfile();
      setProfile(result);
      setProfileError(null);
    } catch (error) {
      const message = error instanceof Error ? error.message : "";
      setProfileError(
        message.includes("ERR_AUTH_003")
          ? "Your session has ended. Please sign in again."
          : "Could not load your profile."
      );
    } finally {
      if (!silent) setProfileLoading(false);
    }
  };

  const loadHistory = async (silent = false) => {
    if (!silent) setHistoryLoading(true);
    setHistoryError(null);
    try {
      const result = await getSeekerResumeHistory({ limit: 10, offset: 0 });
      setHistory(result.items);
    } catch (error) {
      setHistoryError(historyErrorMessage(error));
    } finally {
      if (!silent) setHistoryLoading(false);
    }
  };

  useEffect(() => {
    document.title = "Profile - JobVault";
  }, []);

  useEffect(() => {
    void loadProfile();
    void loadHistory();
  }, []);

  const latestParsed = useMemo(
    () => history.find((item) => item.status === "PARSED") ?? null,
    [history]
  );

  const isLoading = profileLoading || historyLoading;

  return (
    <main className="page seeker-page">
      <div className="page-header">
        <div className="page-header__copy">
          <h1>Profile</h1>
          <p className="page-header__subtitle">
            Keep your profile read-only and current. Edit details or upload a resume from the
            actions below when something changes.
          </p>
        </div>

        <div style={{ display: "flex", flexWrap: "wrap", gap: "0.5rem" }}>
          <Button variant="secondary" onClick={() => setUploadModalOpen(true)}>
            <Icon name="upload" size={14} />
            Upload resume
          </Button>
          <Button onClick={() => setProfileModalOpen(true)}>
            <Icon name="edit" size={14} />
            Edit profile
          </Button>
        </div>
      </div>

      {notice && (
        <div style={{ marginBottom: "var(--space-5)" }}>
          <Alert tone={notice.tone}>{notice.message}</Alert>
        </div>
      )}

      {profileError && (
        <div style={{ marginBottom: "var(--space-5)" }}>
          <Alert tone={profileError.includes("session") ? "info" : "error"}>{profileError}</Alert>
        </div>
      )}

      {isLoading ? (
        <PageLoader />
      ) : (
        <div className="seeker-profile__content">
          <div style={{ display: "flex", flexDirection: "column", gap: "1.5rem" }}>
            <ProfileSnapshotCard
              profile={profile}
              latestParsed={latestParsed}
              onEdit={() => setProfileModalOpen(true)}
            />

            <Card>
              <SectionHeader title="Matching signals" />
              <div style={{ display: "grid", gap: "0.75rem" }}>
                <ProfileDetail
                  icon="briefcase"
                  label="Sector"
                  value={
                    profile?.preferredSector
                      ? profile.preferredSector
                      : "Not set"
                  }
                  muted={!profile?.preferredSector}
                />
                <ProfileDetail
                  icon="location"
                  label="Location"
                  value={
                    profile?.preferredLocation
                      ? profile.preferredLocation
                      : "Set a location to improve local and hybrid matches."
                  }
                  muted={!profile?.preferredLocation}
                />
                <ProfileDetail
                  icon="clock"
                  label="Experience"
                  value={
                    profile?.yearsExperience != null
                      ? `${profile.yearsExperience} year${profile.yearsExperience === 1 ? "" : "s"}`
                      : "Add years of experience to refine seniority matching."
                  }
                  muted={profile?.yearsExperience == null}
                />
                <ProfileDetail
                  icon="remote"
                  label="Remote"
                  value={
                    profile?.remoteOk == null
                      ? "Set a remote preference so location fit is clearer."
                      : profile.remoteOk
                        ? "Open to remote work"
                        : "On-site preferred"
                  }
                  muted={profile?.remoteOk == null}
                />
              </div>
            </Card>
          </div>

          <div style={{ display: "flex", flexDirection: "column", gap: "1.5rem" }}>
            <ResumeSnapshotCard
              history={history}
              latestParsed={latestParsed}
              historyError={historyError}
              onUpload={() => setUploadModalOpen(true)}
              onRefreshHistory={() => void loadHistory()}
            />

            <Card>
              <SectionHeader
                title="Upload history"
                action={history.length > 0 ? `${history.length} uploads` : undefined}
              />
              {historyError && history.length > 0 && (
                <div style={{ marginBottom: "var(--space-4)" }}>
                  <Alert tone="error">{historyError}</Alert>
                </div>
              )}

              {history.length === 0 ? (
                historyError ? (
                  <EmptyState
                    title="Resume history unavailable"
                    description={historyError}
                    action={
                      <Button onClick={() => void loadHistory()} variant="secondary">
                        Reload history
                      </Button>
                    }
                  />
                ) : (
                  <EmptyState
                    title="No resumes uploaded yet"
                    description="Upload a PDF resume to start building your history and skill extraction."
                    action={
                      <Button onClick={() => setUploadModalOpen(true)} variant="secondary">
                        <Icon name="upload" size={14} />
                        Upload resume
                      </Button>
                    }
                  />
                )
              ) : (
                <RowList>
                  {history.map((item) => (
                    <ResumeRow
                      key={item.resumeId}
                      item={item}
                      isLatest={item.resumeId === latestParsed?.resumeId}
                    />
                  ))}
                </RowList>
              )}
            </Card>
          </div>
        </div>
      )}

      <Modal
        open={profileModalOpen}
        title="Edit profile"
        description="Update the profile details that drive your match quality. Changes are saved immediately."
        onClose={() => setProfileModalOpen(false)}
      >
        {profile ? (
          <ProfileForm
            profile={profile}
            onSaved={(updated) => {
              setProfile(updated);
              setProfileModalOpen(false);
              setNotice({
                tone: "success",
                message: "Profile saved. Your matching signals are now up to date.",
              });
            }}
            onCancel={() => setProfileModalOpen(false)}
          />
        ) : (
          <Alert tone="info">Profile data is still loading.</Alert>
        )}
      </Modal>

      <Modal
        open={uploadModalOpen}
        title="Upload resume"
        description="Upload a PDF resume so the app can extract skills and refresh your match results."
        onClose={() => setUploadModalOpen(false)}
        footer={
          <Button variant="ghost" onClick={() => setUploadModalOpen(false)}>
            Close
          </Button>
        }
      >
        <ResumeUploadZone
          onSuccess={() => {
            setUploadModalOpen(false);
            setNotice({
              tone: "success",
              message: "Resume uploaded and parsed. Your resume history has been refreshed.",
            });
            void loadHistory(true);
          }}
        />
      </Modal>
    </main>
  );
}
