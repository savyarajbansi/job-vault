import type { ApplicationStatus } from "../api/employer";
import { Badge } from "./ui";

export function applicationBadgeTone(
  status: ApplicationStatus
): "neutral" | "success" | "warn" | "accent" {
  if (status === "ACCEPTED") return "success";
  if (status === "REJECTED" || status === "WITHDRAWN") return "warn";
  if (status === "SUBMITTED" || status === "UNDER_REVIEW") return "accent";
  return "neutral";
}

export function applicationStatusLabel(status: ApplicationStatus): string {
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

export default function ApplicationStatusBadge({
  status,
}: {
  status: ApplicationStatus;
}) {
  return (
    <Badge tone={applicationBadgeTone(status)}>
      {applicationStatusLabel(status)}
    </Badge>
  );
}
