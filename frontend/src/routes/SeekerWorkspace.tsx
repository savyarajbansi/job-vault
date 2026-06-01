import { FormEvent, useMemo, useState } from "react";

import {
  getSeekerMatches,
  getSeekerResumeHistory,
  getSeekerSkillGaps,
  ResumeHistoryResponse,
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
  const [historyLimit, setHistoryLimit] = useState(5);
  const [historyOffset, setHistoryOffset] = useState(0);
  const [historyBusy, setHistoryBusy] = useState(false);
  const [historyError, setHistoryError] = useState<string | null>(null);
  const [history, setHistory] = useState<ResumeHistoryResponse | null>(null);

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

  const handleLoadHistory = async () => {
    setHistoryBusy(true);
    setHistoryError(null);
    try {
      const response = await getSeekerResumeHistory({
        limit: historyLimit,
        offset: historyOffset
      });
      setHistory(response);
    } catch (error) {
      setHistoryError(errorMessage(error));
    } finally {
      setHistoryBusy(false);
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
          : response.items[0]?.jobId ?? ""
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
    <main>
      <h1>Seeker</h1>
      <p>Resume upload, history, matches, and skill-gap data remain wired to the API.</p>

      <section>
        <h2>Resume upload</h2>
        <form onSubmit={handleUpload}>
          <p>
            <label htmlFor="resume-file">Resume PDF</label>
            <br />
            <input
              id="resume-file"
              type="file"
              accept=".pdf,application/pdf"
              onChange={(event) => setFile(event.target.files?.[0] ?? null)}
            />
          </p>
          <p>
            <button type="submit" disabled={uploadBusy}>
              {uploadBusy ? "Uploading..." : "Upload resume"}
            </button>
          </p>
        </form>
        <pre>Status: {uploadStatus}</pre>
        {latestUpload && <pre>Latest upload: {latestUpload.resumeId} ({latestUpload.status})</pre>}
        {uploadBanner && (
          <p>
            {uploadBanner.tone.toUpperCase()}: {uploadBanner.message}
          </p>
        )}
      </section>

      <section>
        <h2>Resume history</h2>
        <p>
          <label htmlFor="history-limit">Limit</label>
          <br />
          <input
            id="history-limit"
            type="number"
            min={1}
            value={historyLimit}
            onChange={(event) => setHistoryLimit(Math.max(1, Number(event.target.value) || 1))}
          />
        </p>
        <p>
          <label htmlFor="history-offset">Offset</label>
          <br />
          <input
            id="history-offset"
            type="number"
            min={0}
            value={historyOffset}
            onChange={(event) => setHistoryOffset(Math.max(0, Number(event.target.value) || 0))}
          />
        </p>
        <p>
          <button type="button" onClick={() => void handleLoadHistory()} disabled={historyBusy}>
            {historyBusy ? "Loading..." : "Load history"}
          </button>
        </p>
        {historyError && <p>{historyError}</p>}
        {history && (
          <>
            <pre>
              Page: limit {history.page.limit}, offset {history.page.offset}, total{" "}
              {history.page.total}
            </pre>
            <ul>
              {history.items.map((item) => (
                <li key={item.resumeId}>
                  {item.originalFilename} ({item.status})
                </li>
              ))}
            </ul>
          </>
        )}
      </section>

      <section>
        <h2>Ranked matches</h2>
        <p>
          <label htmlFor="limit">Limit</label>
          <br />
          <input
            id="limit"
            type="number"
            min={1}
            value={limit}
            onChange={(event) => setLimit(Math.max(1, Number(event.target.value) || 1))}
          />
        </p>
        <p>
          <label htmlFor="offset">Offset</label>
          <br />
          <input
            id="offset"
            type="number"
            min={0}
            value={offset}
            onChange={(event) => setOffset(Math.max(0, Number(event.target.value) || 0))}
          />
        </p>
        <p>
          <button type="button" onClick={() => void handleLoadMatches()} disabled={matchesBusy}>
            {matchesBusy ? "Loading..." : "Fetch matches"}
          </button>
        </p>
        {matchesError && <p>{matchesError}</p>}
        {matches && (
          <>
            <pre>
              Page: limit {matches.page.limit}, offset {matches.page.offset}, total{" "}
              {matches.page.total}
            </pre>
            <ul>
              {sortedMatchJobs.map((item) => (
                <li key={item.jobId}>
                  {item.job.title} ({item.jobId}) score {item.score.toFixed(4)}
                </li>
              ))}
            </ul>
          </>
        )}
      </section>

      <section>
        <h2>Skill gaps</h2>
        <p>
          <label htmlFor="job-select">Selected job</label>
          <br />
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
        </p>
        <p>
          <button
            type="button"
            onClick={() => void handleLoadSkillGaps()}
            disabled={!selectedJobId || skillGapsState.state === "loading"}
          >
            {skillGapsState.state === "loading" ? "Loading..." : "Check skill gaps"}
          </button>
        </p>

        {skillGapsState.state === "idle" && <p>Select a job and request skill gaps.</p>}
        {skillGapsState.state === "loading" && <p>Loading skill gaps for {selectedJobId}...</p>}
        {skillGapsState.state === "error" && <p>{skillGapsState.message}</p>}
        {skillGapsState.state === "perfect_match" && (
          <p>No missing skills. You are a strong match for this role.</p>
        )}
        {skillGapsState.state === "has_gaps" && (
          <>
            <pre>Job ID: {skillGapsState.jobId}</pre>
            <ul>
              {skillGapsState.missingSkills.map((skill) => (
                <li key={skill}>{skill}</li>
              ))}
            </ul>
          </>
        )}
      </section>
    </main>
  );
}
