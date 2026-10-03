package org.juns.marketboardbackend.mail;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import org.juns.marketboardbackend.collector.NewsItem;
import org.juns.marketboardbackend.common.exception.ResourceNotFoundException;
import org.juns.marketboardbackend.news.NewsService;
import org.juns.marketboardbackend.notification.NotificationService;
import org.juns.marketboardbackend.user.UserRepository;
import org.juns.marketboardbackend.user.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DailyReportMailer {
    private static final Logger log = LoggerFactory.getLogger(DailyReportMailer.class);
    private final UserRepository users;
    private final NewsService news;
    private final MailService mail;
    private final NotificationService notifications;

    public DailyReportMailer(UserRepository users, NewsService news, MailService mail, NotificationService notifications) {
        this.users = users;
        this.news = news;
        this.mail = mail;
        this.notifications = notifications;
    }

    @Scheduled(cron = "${mail.daily-report-cron:0 0 8 * * *}", zone = "Asia/Seoul")
    public void send() {
        List<NewsItem> items;
        try {
            items = news.getGeneralNews();
        } catch (ResourceNotFoundException ex) {
            notifications.createImportantInfoForEnabled("daily-report:no-news:" + LocalDate.now(),
                    "데일리 보고서가 지연되고 있습니다", "최신 뉴스 자료가 없어 보고서를 만들지 못했습니다.", "/news");
            log.warn("Daily report skipped: no news snapshot");
            return;
        }
        String body = items.stream().limit(10)
                .map(NewsItem::headline)
                .map(headline -> "• " + headline)
                .collect(Collectors.joining("\n"));
        users.findAllByDailyReportEnabledTrueAndStatus(UserStatus.ACTIVE).forEach(user -> {
            notifications.createDailyReport(user, LocalDate.now());
            try {
                mail.send(user.getEmail(), "MarketBoard 데일리 보고서", body);
            } catch (RuntimeException ex) {
                log.warn("Daily report delivery failed for user {}", user.getId(), ex);
            }
        });
    }
}
