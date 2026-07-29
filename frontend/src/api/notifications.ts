import { authorizedRequest } from "./auth";

export type NotificationType =
  | "APPLICATION_SUBMITTED"
  | "APPLICATION_STATUS_CHANGED"
  | "JOB_POSTED_MATCHING_SECTOR"
  | "JOB_MATCH_FOUND"
  | "CANDIDATE_SHORTLISTED"
  | "SHORTLIST_ACCEPTED";

export type NotificationItem = {
  id: string;
  type: NotificationType;
  message: string;
  isRead: boolean;
  createdAt: string;
  relatedJobId: string | null;
  shortlistId: string | null;
  shortlistStatus: "PENDING" | "ACCEPTED" | null;
};

export type NotificationUnreadCount = {
  unreadCount: number;
};

export async function getNotifications(): Promise<NotificationItem[]> {
  return authorizedRequest<NotificationItem[]>("/api/notifications", { method: "GET" });
}

export async function getUnreadNotificationCount(): Promise<NotificationUnreadCount> {
  return authorizedRequest<NotificationUnreadCount>("/api/notifications/unread-count", {
    method: "GET",
  });
}

export async function markNotificationRead(notificationId: string): Promise<void> {
  await authorizedRequest<void>(`/api/notifications/${notificationId}/read`, {
    method: "POST",
  });
}
