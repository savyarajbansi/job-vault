import { FormEvent, useMemo, useState } from "react";

import {
  getSeekerMatches,
  getSeekerSkillGaps,
  SeekerJobMatchResponse,
  uploadSeekerResume
} from "../api/seeker";

type Banner = {
  tone: "success" | "error";
  message: string;
};

type SkillGapsState =
  | { state: "idle" }
  | { state: "loading" }
  | { state: "error"; message: string }
  | { state: "perfect_match"; jobId: string }
  | { state: "has_gaps"; jobId: string; missingSkills: string[] };

const codeToMessage: Record<string, string> = {
  ERR_AUTH_003: "Your session has ended. Please sign in again.",
  ERR_UPLOAD_001: "Only PDF files are supported.",
  ERR_UPLOAD_002: "The selected file is too large.",
  ERR_UPLOAD_003: "Resume upload failed. Please try again.",
  ERR_PARSE_001: "Resume parsing failed. Please try another PDF.",
  ERR_PARSE_002: "No readable text was found in this PDF.",
  ERR_MATCH_001: "Matching is temporarily unavailable. Please retry.",
  ERR_MATCH_002: "Skill-gap analysis is temporarily unavailable. Please retry."
};

function errorMessage(error: unknown): string {
  const raw = error instanceof Error ? error.message : "Request failed.";
  const matched = raw.match(/ERR_[A-Z]+_\d{3}/)?.[0];
  if (matched && codeToMessage[matched]) {
    return codeToMessage[matched];
  }
  return raw;
}

export default function SeekerWorkspace() {
  const [file, setFile] = useState<File | null>(null);
  const [uploadBusy, setUploadBusy] = useState(false);
  const [uploadStatus, setUploadStatus] = useState("Awaiting PDF upload.");
  const [uploadBanner, setUploadBanner] = useState<Banner | null>(null);
  const [latestUpload, setLatestUpload] = useState<{ resumeId: string; status: string } | null>(
    null
  );

  const [limit, setLimit] = useState(10);
  const [offset, setOffset] = useState(0);
  const [matchesBusy, setMatchesBusy] = useState(false);
  const [matchesError, setMatchesError] = useState<string | null>(null);
  const [matches, setMatches] = useState<SeekerJobMatchResponse | null>(null);

  const [selectedJobId, setSelectedJobId] = useState("");
  const [skillGapsState, setSkillGapsState] = useState<SkillGapsState>({ state: "idle" });

  const sortedMatchJobs = useMemo(() => matches?.items ?? [], [matches]);

  const handleUpload = async (event: FormEvent) => {
    event.preventDefault();
    if (!file) {
      setUploadBanner({ tone: "error", message: "Select a PDF before submitting." });
      return;
    }
    setUploadBusy(true);
    setUploadBanner(null);
    setUploadStatus("Uploading and parsing resume...");
    try {
      const result = await uploadSeekerResume(file);
      setLatestUpload(result);
      setUploadStatus(`Upload completed with status ${result.status}.`);
      setUploadBanner({
        tone: "success",
        message: `Resume ${result.resumeId} was processed successfully.`
      });
    } catch (error) {
      setUploadStatus("Upload request failed.");
      setUploadBanner({ tone: "error", message: errorMessage(error) });
    } finally {
      setUploadBusy(false);
    }
  };

  const handleLoadMatches = async () => {
    setMatchesBusy(true);
    setMatchesError(null);
    try {
      const response = await getSeekerMatches({ limit, offset });
      setMatches(response);
      setSelectedJobId((current) =>
        response.items.some((item) => item.jobId === current)
          ? current
          : (response.items[0]?.jobId ?? "")
      );
      setSkillGapsState({ state: "idle" });
    } catch (error) {
      setMatchesError(errorMessage(error));
    } finally {
      setMatchesBusy(false);
    }
  };

  const handleLoadSkillGaps = async () => {
    if (!selectedJobId) {
      setSkillGapsState({ state: "idle" });
      return;
    }

    setSkillGapsState({ state: "loading" });
    try {
      const result = await getSeekerSkillGaps(selectedJobId);
      if (result.missingSkills.length === 0) {
        setSkillGapsState({ state: "perfect_match", jobId: result.jobId });
        return;
      }
      setSkillGapsState({
        state: "has_gaps",
        jobId: result.jobId,
        missingSkills: result.missingSkills
      });
    } catch (error) {
      setSkillGapsState({ state: "error", message: errorMessage(error) });
    }
  };

  return (
    <div className="page">
      <section className="hero">
        <h1>Seeker Workspace</h1>
        <p>
          Upload your latest resume, load ranked job matches, and inspect job-specific
          skill gaps.
        </p>
      </section>

      <section className="grid seeker-grid">
        <article className="card">
          <h2>Resume upload</h2>
          <form onSubmit={handleUpload}>
            <label htmlFor="resume-file">Resume PDF</label>
            <input
              id="resume-file"
              type="file"
              accept=".pdf,application/pdf"
              onChange={(event) => setFile(event.target.files?.[0] ?? null)}
            />
            <div className="actions" style={{ marginTop: 16 }}>
              <button type="submit" disabled={uploadBusy}>
                {uploadBusy ? "Uploading..." : "Upload resume"}
              </button>
            </div>
          </form>
          <p className="mono">Status: {uploadStatus}</p>
          {latestUpload && (
            <p className="mono">
              Latest upload: {latestUpload.resumeId} ({latestUpload.status})
            </p>
          )}
          {uploadBanner && (
            <div
              className={`status ${uploadBanner.tone === "success" ? "status-success" : "status-error"}`}
              role={uploadBanner.tone === "error" ? "alert" : "status"}
              aria-live="polite"
            >
              {uploadBanner.message}
            </div>
          )}
        </article>

        <article className="card">
          <h2>Ranked matches</h2>
          <div className="seeker-controls">
            <div>
              <label htmlFor="limit">Limit</label>
              <input
                id="limit"
                type="number"
                min={1}
                value={limit}
                onChange={(event) => setLimit(Math.max(1, Number(event.target.value) || 1))}
              />
            </div>
            <div>
              <label htmlFor="offset">Offset</label>
              <input
                id="offset"
                type="number"
                min={0}
                value={offset}
                onChange={(event) => setOffset(Math.max(0, Number(event.target.value) || 0))}
              />
            </div>
          </div>
          <div className="actions" style={{ marginTop: 16 }}>
            <button className="secondary" onClick={handleLoadMatches} disabled={matchesBusy}>
              {matchesBusy ? "Loading..." : "Fetch matches"}
            </button>
          </div>
          {matchesError && (
            <div className="status status-error" role="alert">
              {matchesError}
            </div>
          )}
          {matches && (
            <>
              <p className="mono">
                Page: limit {matches.page.limit}, offset {matches.page.offset}, total{" "}
                {matches.page.total}
              </p>
              <ul className="seeker-list">
                {sortedMatchJobs.map((item) => (
                  <li key={item.jobId}>
                    <strong>{item.job.title}</strong> ({item.jobId}) score{" "}
                    {item.score.toFixed(4)}
                    <br />
                    <span className="mono">
                      Factors: cosine {item.factors.cosine.toFixed(4)}, skills{" "}
                      {item.factors.skillsOverlap.toFixed(4)}, experience{" "}
                      {item.factors.experience.toFixed(4)}, location{" "}
                      {item.factors.location.toFixed(4)}
                    </span>
                  </li>
                ))}
              </ul>
            </>
          )}
        </article>

        <article className="card">
          <h2>Skill gaps</h2>
          <label htmlFor="job-select">Selected job</label>
          <select
            id="job-select"
            value={selectedJobId}
            onChange={(event) => {
              setSelectedJobId(event.target.value);
              setSkillGapsState({ state: "idle" });
            }}
            disabled={sortedMatchJobs.length === 0}
          >
            <option value="">Select a matched job</option>
            {sortedMatchJobs.map((item) => (
              <option key={item.jobId} value={item.jobId}>
                {item.job.title} ({item.jobId})
              </option>
            ))}
          </select>
          <div className="actions" style={{ marginTop: 16 }}>
            <button
              className="ghost"
              onClick={handleLoadSkillGaps}
              disabled={!selectedJobId || skillGapsState.state === "loading"}
            >
              {skillGapsState.state === "loading" ? "Loading..." : "Check skill gaps"}
            </button>
          </div>

          {skillGapsState.state === "idle" && (
            <p className="mono">Select a job and request skill gaps.</p>
          )}
          {skillGapsState.state === "loading" && (
            <p className="mono">Loading skill gaps for {selectedJobId}...</p>
          )}
          {skillGapsState.state === "error" && (
            <div className="status status-error" role="alert">
              {skillGapsState.message}
            </div>
          )}
          {skillGapsState.state === "perfect_match" && (
            <div className="status status-success">
              No missing skills. You are a strong match for this role.
            </div>
          )}
          {skillGapsState.state === "has_gaps" && (
            <>
              <p className="mono">Job ID: {skillGapsState.jobId}</p>
              <ul className="seeker-list">
                {skillGapsState.missingSkills.map((skill) => (
                  <li key={skill}>{skill}</li>
                ))}
              </ul>
            </>
          )}
        </article>
      </section>
    </div>
  );
}
