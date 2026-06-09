import { useState, useEffect, FormEvent } from "react";
import { Link } from "react-router-dom";
import {
  uploadSeekerResume,
  getSeekerResumeHistory,
  ResumeHistoryResponse,
} from "../api/seeker";
import { Button, Alert, Card, Badge, Spinner } from "../components/ui";

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

export default function SeekerDashboard() {
  const [file, setFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [uploadSuccess, setUploadSuccess] = useState<string | null>(null);

  const [history, setHistory] = useState<ResumeHistoryResponse | null>(null);
  const [historyLoading, setHistoryLoading] = useState(true);
  const [historyError, setHistoryError] = useState<string | null>(null);

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

  useEffect(() => { void loadHistory(); }, []);

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
      // Reset file input
      const el = document.getElementById("resume-input") as HTMLInputElement;
      if (el) el.value = "";
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

  const hasReadyResume = history?.items.some((i) => i.status === "PARSED") ?? false;

  return (
    <main style={{ maxWidth: 1100, margin: "0 auto", padding: "2rem 1.5rem" }}>
      {/* Page header */}
      <div style={{ marginBottom: "2rem" }}>
        <h1 style={{ marginBottom: "0.375rem" }}>My Dashboard</h1>
        <p style={{ color: "var(--ink-muted)" }}>
          Upload your resume, then explore job matches tailored to your skills.
        </p>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "1.5rem" }}>
        {/* ── Upload card ── */}
        <Card style={{ gridColumn: "1", display: "flex", flexDirection: "column", gap: "1rem" }}>
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

            <Button
              type="submit"
              fullWidth
              loading={uploading}
              disabled={!file}
            >
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
        <Card style={{ gridColumn: "2" }}>
          <h3 style={{ marginBottom: "1rem" }}>Upload history</h3>
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
            <div style={{ display: "flex", flexDirection: "column", gap: "0.75rem" }}>
              {history!.items.map((item) => (
                <div
                  key={item.resumeId}
                  style={{
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "space-between",
                    padding: "0.75rem",
                    background: "var(--bg-subtle)",
                    borderRadius: "var(--radius-sm)",
                    gap: "0.75rem",
                  }}
                >
                  <div style={{ minWidth: 0 }}>
                    <div
                      style={{
                        fontWeight: 500,
                        fontSize: "0.875rem",
                        color: "var(--ink)",
                        overflow: "hidden",
                        textOverflow: "ellipsis",
                        whiteSpace: "nowrap",
                      }}
                    >
                      {item.originalFilename}
                    </div>
                    <div style={{ fontSize: "0.75rem", color: "var(--ink-muted)", marginTop: "0.125rem" }}>
                      {formatDate(item.parsedAt ?? item.createdAt)}
                    </div>
                  </div>
                  <Badge tone={statusTone(item.status)}>
                    {statusLabel(item.status)}
                  </Badge>
                </div>
              ))}
              {history && history.page.total > 5 && (
                <p style={{ fontSize: "0.8125rem", color: "var(--ink-muted)" }}>
                  Showing 5 of {history.page.total} uploads.
                </p>
              )}
            </div>
          )}
        </Card>

        {/* ── Tip card (full width) ── */}
        {!hasReadyResume && !historyLoading && (
          <div
            style={{
              gridColumn: "1 / -1",
              padding: "1.25rem 1.5rem",
              background: "var(--accent-faint)",
              border: "1px solid var(--accent)",
              borderRadius: "var(--radius)",
              display: "flex",
              alignItems: "center",
              gap: "1rem",
            }}
          >
            <span style={{ fontSize: "1.5rem" }}>💡</span>
            <div>
              <p style={{ fontWeight: 600, color: "var(--accent)", marginBottom: "0.125rem" }}>
                Upload a resume to unlock matches
              </p>
              <p style={{ fontSize: "0.875rem", color: "var(--ink-2)" }}>
                JobVault reads your resume, identifies your skills, and ranks open positions by fit — all automatically.
              </p>
            </div>
          </div>
        )}
      </div>
    </main>
  );
}
