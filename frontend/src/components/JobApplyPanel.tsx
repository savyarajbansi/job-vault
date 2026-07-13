import { useCallback, useEffect, useState } from "react";

import {
  getMyApplications,
} from "../api/seeker";
import type { SeekerApplicationItem } from "../api/seeker";
import { Alert, Button, Spinner } from "./ui";
import ApplicationStatusBadge from "./ApplicationStatusBadge";
import useApplicationAction from "../hooks/useApplicationAction";

type Props = {
  jobId: string;
};

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

  const loadApplication = useCallback(async (): Promise<boolean> => {
    try {
      const items = await getMyApplications();
      setApplication(items.find((item) => item.jobId === jobId) ?? null);
      setLoadError(null);
      return true;
    } catch {
      setLoadError("Could not load your application status for this job.");
      return false;
    } finally {
      setLoading(false);
    }
  }, [jobId]);

  const { actionState, actionError, runAction } = useApplicationAction({
    jobId,
    application,
    reloadApplications: loadApplication,
  });

  useEffect(() => {
    setLoading(true);
    void loadApplication();
  }, [loadApplication]);

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
            <ApplicationStatusBadge status={application.status} />
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
            <ApplicationStatusBadge status={application.status} />
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
            <ApplicationStatusBadge status={application.status} />
            <span style={{ fontSize: "0.8125rem", color: "var(--ink-muted)" }}>
              This application is closed.
            </span>
          </div>
        )}
    </div>
  );
}