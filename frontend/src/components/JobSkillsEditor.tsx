import { FormEvent, useState } from "react";

import { addEmployerJobSkill, removeEmployerJobSkill } from "../api/employer";
import type { JobDetail } from "../api/employer";
import { ApiResponseError } from "../api/client";
import { Alert, Button } from "./ui";

type Props = {
  job: JobDetail;
  onUpdate: (job: JobDetail) => void;
};

function skillActionError(error: unknown): string {
  if (error instanceof Error && error.message.includes("ERR_AUTH_003")) {
    return "Your session has ended. Please sign in again.";
  }
  if (error instanceof ApiResponseError && error.response.status === 409) {
    return "This job is disabled. Reactivate it to manage skills.";
  }
  return "Could not update skills. Please try again.";
}

export default function JobSkillsEditor({ job, onUpdate }: Props) {
  const [skillInput, setSkillInput] = useState("");
  const [pendingSkill, setPendingSkill] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const isDisabled = job.status === "DISABLED";
  const busy = adding || pendingSkill !== null;

  const handleAdd = async (event: FormEvent) => {
    event.preventDefault();
    const trimmed = skillInput.trim();
    if (!trimmed) {
      return;
    }
    setAdding(true);
    setError(null);
    try {
      const updated = await addEmployerJobSkill(job.id, trimmed);
      onUpdate(updated);
      setSkillInput("");
    } catch (err) {
      setError(skillActionError(err));
    } finally {
      setAdding(false);
    }
  };

  const handleRemove = async (skillName: string) => {
    setPendingSkill(skillName);
    setError(null);
    try {
      const updated = await removeEmployerJobSkill(job.id, skillName);
      onUpdate(updated);
    } catch (err) {
      setError(skillActionError(err));
    } finally {
      setPendingSkill(null);
    }
  };

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "0.75rem" }}>
      <p style={{ fontSize: "0.8125rem", color: "var(--ink-muted)" }}>
        Required skills drive the skills-overlap component of candidate match scores.
        Skills mentioned in the title or description are detected automatically — add or remove
        any that need adjusting. Note: a skill that's still literally present in the
        posting text will be re-detected the next time you save the job, even if
        you remove it here.
      </p>

      {error && <Alert tone="error">{error}</Alert>}
      {isDisabled && (
        <Alert tone="info">This job is disabled. Reactivate it to manage skills.</Alert>
      )}

      <div style={{ display: "flex", flexWrap: "wrap", gap: "0.45rem" }}>
        {job.requiredSkills.length === 0 && (
          <span style={{ fontSize: "0.875rem", color: "var(--ink-faint)" }}>
            No required skills yet.
          </span>
        )}
        {job.requiredSkills.map((skill) => (
          <span
            key={skill}
            style={{
              display: "inline-flex",
              alignItems: "center",
              gap: "0.4rem",
              padding: "0.25rem 0.5rem 0.25rem 0.625rem",
              background: "var(--accent-faint)",
              color: "var(--accent)",
              borderRadius: "999px",
              fontSize: "0.8125rem",
              fontWeight: 500,
            }}
          >
            {skill}
            {!isDisabled && (
              <button
                type="button"
                onClick={() => void handleRemove(skill)}
                disabled={busy}
                aria-label={`Remove ${skill}`}
                style={{
                  border: "none",
                  background: "none",
                  color: "var(--accent)",
                  cursor: busy ? "default" : "pointer",
                  fontSize: "0.875rem",
                  lineHeight: 1,
                  padding: 0,
                  opacity: pendingSkill === skill ? 0.5 : 1,
                }}
              >
                ×
              </button>
            )}
          </span>
        ))}
      </div>

      {!isDisabled && (
        <form onSubmit={(event) => void handleAdd(event)} style={{ display: "flex", gap: "0.5rem" }}>
          <input
            value={skillInput}
            onChange={(event) => setSkillInput(event.target.value)}
            placeholder="e.g. Kubernetes"
            maxLength={100}
            disabled={adding}
            style={{
              flex: 1,
              padding: "0.5rem 0.75rem",
              borderRadius: "var(--radius-sm)",
              border: "1.5px solid var(--border)",
              background: "var(--bg-card)",
              color: "var(--ink)",
              fontSize: "0.875rem",
              fontFamily: "var(--font-body)",
              outline: "none",
            }}
          />
          <Button type="submit" size="sm" loading={adding} disabled={!skillInput.trim()}>
            Add
          </Button>
        </form>
      )}
    </div>
  );
}
