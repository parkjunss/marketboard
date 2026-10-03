package org.juns.marketboardbackend.notification.dto;

import java.util.List;

public record NotificationListResponse(List<NotificationResponse> items, long unreadCount) {}
