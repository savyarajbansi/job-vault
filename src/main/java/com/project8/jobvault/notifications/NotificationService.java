package com.project8.jobvault.notifications;

import com.project8.jobvault.jobs.CandidateMatchNotification;
import com.project8.jobvault.users.UserAccount;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {
    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public Notification createNotification(UserAccount recipient, NotificationType type, String message) {
        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setType(type);
        notification.setMessage(message);
        notification.setRead(false);
        return notificationRepository.save(notification);
    }

    public Notification createShortlistNotification(
            UserAccount recipient,
            CandidateMatchNotification shortlist,
            String message) {
        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setType(NotificationType.CANDIDATE_SHORTLISTED);
        notification.setMessage(message);
        notification.setRead(false);
        notification.setCandidateMatchNotification(shortlist);
        return notificationRepository.save(notification);
    }
}
