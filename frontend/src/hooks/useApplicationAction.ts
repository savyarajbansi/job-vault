import { useCallback, useState } from "react";

import { ApiResponseError } from "../api/client";
import {
  applyToJob,
  draftApplication,
  withdrawApplication,
} from "../api/seeker";
import type { SeekerApplicationItem } from "../api/seeker";

export type ApplicationAction = "draft" | "apply" | "withdraw";
export type ActionState = "idle" | "drafting" | "applying" | "withdrawing";

function actionErrorMessage(error: unknown): string {
  if (error instanceof Error && error.message.includes("ERR_AUTH_003")) {
    return "Your session has ended. Please sign in again.";
  }
  if (error instanceof ApiResponseError && error.response.status === 409) {
    return "This application has already moved on - refreshing the latest status.";
  }
  return "That action didn't go through. Please try again.";
}

export default function useApplicationAction({
  jobId,
  application,
  reloadApplications,
}: {
  jobId: string | null | undefined;
  application: SeekerApplicationItem | null | undefined;
  reloadApplications: () => Promise<boolean>;
}) {
  const [actionState, setActionState] = useState<ActionState>("idle");
  const [actionError, setActionError] = useState<string | null>(null);

  const clearActionError = useCallback(() => setActionError(null), []);

  const runAction = async (action: ApplicationAction) => {
    if (!jobId) return;

    setActionError(null);
    setActionState(
      action === "draft" ? "drafting" : action === "apply" ? "applying" : "withdrawing"
    );

    try {
      if (action === "draft") {
        await draftApplication(jobId);
      } else if (action === "apply") {
        await applyToJob(jobId);
      } else if (application) {
        await withdrawApplication(application.id);
      }

      const refreshed = await reloadApplications();
      if (!refreshed) {
        setActionError(
          "Your application was updated, but we couldn't refresh the latest application status."
        );
      }
    } catch (error) {
      const baseMessage = actionErrorMessage(error);
      const refreshed = await reloadApplications();
      setActionError(
        refreshed
          ? baseMessage
          : `${baseMessage} We also couldn't refresh your latest application status.`
      );
    } finally {
      setActionState("idle");
    }
  };

  return { actionState, actionError, clearActionError, runAction };
}
