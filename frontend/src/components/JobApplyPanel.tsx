import { useCallback, useEffect, useState } from "react";

import {
  applyToJob,
  draftApplication,
  getMyApplications,
  withdrawApplication,
} from "../api/seeker";
import type { SeekerApplicationItem } from "../api/seeker";
import type { ApplicationStatus } from "../api/employer";
import { ApiResponseError } from "../api/client";
import { Alert, Badge, Button, Spinner } from "./ui";

type Props = {
  jobId: string;
};

type ActionState = "idle" | "drafting" | "applying" | "withdrawing";

function applicationBadgeTone(status: ApplicationStatus): "neutral" | "success" | "warn" | "accent" {
  if (status === "ACCEPTED") return "success";
  if (status === "REJECTED" || status === "WITHDRAWN") return "warn";
  if (status === "SUBMITTED" || status === "UNDER_REVIEW") return "accent";
  return "neutral"; // DRAFT
}

function applicationStatusLabel(status: ApplicationStatus): string {
  const map: Record<ApplicationStatus, string> = {
    DRAFT: "Draft saved",
    SUBMITTED: "Applied",
    UNDER_REVIEW: "Under review",
    ACCEPTED: "Accepted",
    REJECTED: "Not selected",
    WITHDRAWN: "Withdrawn",
  };
  return map[status];
}

function describeApplicationActionError(error: unknown): string {
  if (error instanceof Error && error.message.includes("ERR_AUTH_003")) {
    return "Your session has ended. Please sign in again.";
  }
  if (error instanceof ApiResponseError && error.response.status === 409) {
    return "This application has already moved on — refreshing the latest status.";
  }
  return "That action didn't go through. Please try again.";
}

/**
 * Self-contained apply/draft/withdraw control for a single job. Used on the
 * shared job detail page (/jobs/:jobId) so a seeker who arrives via Browse —
 * rather than via /seeker/matches, which requires a parsed resume — has a
 * way to act on the listing. Mirrors the application-action block in
 * SeekerMatches.tsx, but only tracks the one job it's given rather than the
 * full applications map (SeekerMatches needs the full map to badge every
 * row in its list; this only ever needs one).
 */
export default function JobApplyPanel({ jobId }: Props) {
  const [application, setApplication] = useState<SeekerApplicationItem | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [actionState, setActionState] = useState<ActionState>("idle");
  const [actionError, setActionError] = useState<string | null>(null);

  const loadApplication = useCallback(async () => {
    try {
      const items = await getMyApplications();
      setApplication(items.find((item) => item.jobId === jobId) ?? null);
      setLoadError(null);
    } catch {
      setLoadError("Could not load your application status for this job.");
    } finally {
      setLoading(false);
    }
  }, [jobId]);

  useEffect(() => {
    setLoading(true);
    void loadApplication();
  }, [loadApplication]);

  const runAction = async (action: "draft" | "apply" | "withdraw") => {
    setActionError(null);
    setActionState(action === "draft" ? "drafting" : action === "apply" ? "applying" : "withdrawing");
    try {
      if (action === "draft") {
        await draftApplication(jobId);
      } else if (action === "apply") {
        await applyToJob(jobId);
      } else if (application) {
        await withdrawApplication(application.id);
      }
      await loadApplication();
    } catch (err) {
      setActionError(describeApplicationActionError(err));
      await loadApplication();
    } finally {
      setActionState("idle");
    }
  };

  if (loading) {
    return (
      <div style={{ display: "flex", justifyContent: "center", padding: "1rem" }}>
        <Spinner size={20} />
      </div>
    );
  }

  return (
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        gap: "0.625rem",
        padding: "0.875rem",
        background: "var(--bg-subtle)",
        borderRadius: "var(--radius-sm)",
      }}
    >
      {loadError && <Alert tone="error">{loadError}</Alert>}
      {actionError && <Alert tone="error">{actionError}</Alert>}

      {!application && (
        <>
          <p style={{ fontSize: "0.8125rem", color: "var(--ink-muted)" }}>
            You haven't applied to this job yet.
          </p>
          <div style={{ display: "flex", gap: "0.5rem", flexWrap: "wrap" }}>
            <Button
              size="sm"
              loading={actionState === "applying"}
              disabled={actionState !== "idle"}
              onClick={() => void runAction("apply")}
            >
              Apply now
            </Button>
            <Button
              size="sm"
              variant="secondary"
              loading={actionState === "drafting"}
              disabled={actionState !== "idle"}
              onClick={() => void runAction("draft")}
            >
              Save as draft
            </Button>
          </div>
        </>
      )}

      {application && application.status === "DRAFT" && (
        <>
          <div style={{ display: "flex", alignItems: "center", gap: "0.5rem" }}>
            <Badge tone="neutral">Draft saved</Badge>
            <span style={{ fontSize: "0.8125rem", color: "var(--ink-muted)" }}>
              Not yet submitted to the employer.
            </span>
          </div>
          <Button
            size="sm"
            loading={actionState === "applying"}
            disabled={actionState !== "idle"}
            onClick={() => void runAction("apply")}
          >
            Submit application
          </Button>
        </>
      )}

      {application && (application.status === "SUBMITTED" || application.status === "UNDER_REVIEW") && (
        <>
          <div style={{ display: "flex", alignItems: "center", gap: "0.5rem" }}>
            <Badge tone={applicationBadgeTone(application.status)}>
              {applicationStatusLabel(application.status)}
            </Badge>
          </div>
          <Button
            size="sm"
            variant="secondary"
            loading={actionState === "withdrawing"}
            disabled={actionState !== "idle"}
            onClick={() => void runAction("withdraw")}
          >
            Withdraw application
          </Button>
        </>
      )}

      {application &&
        (application.status === "ACCEPTED" ||
          application.status === "REJECTED" ||
          application.status === "WITHDRAWN") && (
          <div style={{ display: "flex", alignItems: "center", gap: "0.5rem" }}>
            <Badge tone={applicationBadgeTone(application.status)}>
              {applicationStatusLabel(application.status)}
            </Badge>
            <span style={{ fontSize: "0.8125rem", color: "var(--ink-muted)" }}>
              This application is closed.
            </span>
          </div>
        )}
    </div>
  );
}