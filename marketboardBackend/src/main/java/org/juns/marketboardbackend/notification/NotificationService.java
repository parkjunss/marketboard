package org.juns.marketboardbackend.notification;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.juns.marketboardbackend.alert.Alert;
import org.juns.marketboardbackend.common.exception.ResourceNotFoundException;
import org.juns.marketboardbackend.notification.dto.NotificationListResponse;
import org.juns.marketboardbackend.notification.dto.NotificationResponse;
import org.juns.marketboardbackend.user.User;
import org.juns.marketboardbackend.user.UserRepository;
import org.juns.marketboardbackend.user.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
    private final NotificationRepository notifications;
    private final UserRepository users;

    public NotificationService(NotificationRepository notifications, UserRepository users) {
        this.notifications = notifications;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public NotificationListResponse getAll(Long userId) {
        var items = notifications.findTop100ByUser_IdOrderByCreatedAtDesc(userId).stream()
                .map(NotificationResponse::from).toList();
        return new NotificationListResponse(items, notifications.countByUser_IdAndReadAtIsNull(userId));
    }

    @Transactional
    public void markRead(Long userId, Long notificationId) {
        notifications.findByIdAndUser_Id(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"))
                .markRead();
    }

    @Transactional
    public void markAllRead(Long userId) {
        notifications.findByUser_IdAndReadAtIsNull(userId).forEach(Notification::markRead);
    }

    @Transactional
    public void createPriceAlert(Alert alert, BigDecimal price) {
        String direction = alert.getCondition().name().equals("ABOVE") ? "이상" : "이하";
        create(alert.getUser(), NotificationType.PRICE_ALERT,
                alert.getSymbol().getTicker() + " 목표가 도달",
                "현재가 " + price + " · 목표가 " + alert.getTargetPrice() + " " + direction,
                "/symbols/" + alert.getSymbol().getTicker(), "price-alert:" + alert.getId());
    }

    @Transactional
    public void createDailyReport(User user, LocalDate date) {
        create(user, NotificationType.DAILY_REPORT, "데일리 보고서가 도착했습니다",
                date + " 시장 요약을 확인하세요.", "/dashboard", "daily-report:" + date);
    }

    @Transactional
    public void createImportantInfoForEnabled(String dedupKey, String title, String message, String link) {
        users.findAllByImportantInfoEnabledTrueAndStatus(UserStatus.ACTIVE)
                .forEach(user -> create(user, NotificationType.IMPORTANT_INFO, title, message, link, dedupKey));
    }

    private void create(User user, NotificationType type, String title, String message, String link, String dedupKey) {
        if (notifications.existsByUser_IdAndDedupKey(user.getId(), dedupKey)) return;
        notifications.save(Notification.builder().user(user).type(type).title(title).message(message)
                .link(link).dedupKey(dedupKey).build());
    }
}
