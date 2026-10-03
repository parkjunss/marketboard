package org.juns.marketboardbackend.notification.dto;

import java.time.Instant;
import org.juns.marketboardbackend.notification.Notification;
import org.juns.marketboardbackend.notification.NotificationType;

public record NotificationResponse(Long id, NotificationType type, String title, String message,
                                   String link, Instant readAt, Instant createdAt) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getType(), notification.getTitle(),
                notification.getMessage(), notification.getLink(), notification.getReadAt(), notification.getCreatedAt());
    }
}
