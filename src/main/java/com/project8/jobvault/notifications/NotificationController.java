package com.project8.jobvault.notifications;

import com.project8.jobvault.auth.JwtPrincipal;
import com.project8.jobvault.users.UserAccount;
import com.project8.jobvault.users.UserAccountRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationRepository notificationRepository;
    private final UserAccountRepository userAccountRepository;

    public NotificationController(
            NotificationRepository notificationRepository,
            UserAccountRepository userAccountRepository) {
        this.notificationRepository = notificationRepository;
        this.userAccountRepository = userAccountRepository;
    }

    @GetMapping
    public List<NotificationResponse> list(@AuthenticationPrincipal JwtPrincipal principal) {
        UserAccount user = requireUser(principal);
        return notificationRepository.findTop20ByRecipientIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(notification -> new NotificationResponse(
                        notification.getId(),
                        notification.getType(),
                        notification.getMessage(),
                        notification.isRead(),
                        notification.getCreatedAt()))
                .toList();
    }

    @GetMapping("/unread-count")
    public NotificationUnreadCountResponse unreadCount(@AuthenticationPrincipal JwtPrincipal principal) {
        UserAccount user = requireUser(principal);
        long unread = notificationRepository.countByRecipientIdAndIsReadFalse(user.getId());
        return new NotificationUnreadCountResponse(unread);
    }

    @PostMapping("/{notificationId}/read")
    public ResponseEntity<Void> markRead(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable UUID notificationId) {
        UserAccount user = requireUser(principal);
        Notification notification = notificationRepository
                .findByIdAndRecipientId(notificationId, user.getId())
                .orElse(null);
        if (notification == null) {
            return ResponseEntity.notFound().build();
        }
        if (!notification.isRead()) {
            notification.setRead(true);
            notificationRepository.save(notification);
        }
        return ResponseEntity.noContent().build();
    }

    private UserAccount requireUser(JwtPrincipal principal) {
        if (principal == null) {
            throw new BadCredentialsException("Invalid authentication");
        }
        UUID userId = principal.userId();
        if (userId == null) {
            throw new BadCredentialsException("Invalid authentication");
        }
        return userAccountRepository.findById(userId)
                .filter(UserAccount::isEnabled)
                .orElseThrow(() -> new BadCredentialsException("Invalid authentication"));
    }
}
