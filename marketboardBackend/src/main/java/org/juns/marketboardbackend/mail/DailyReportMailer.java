package org.juns.marketboardbackend.mail;

import java.util.stream.Collectors;
import org.juns.marketboardbackend.collector.NewsItem;
import org.juns.marketboardbackend.news.NewsService;
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

    public DailyReportMailer(UserRepository users, NewsService news, MailService mail) {
        this.users = users;
        this.news = news;
        this.mail = mail;
    }

    @Scheduled(cron = "${mail.daily-report-cron:0 0 8 * * *}", zone = "Asia/Seoul")
    public void send() {
        String body = news.getGeneralNews().stream().limit(10)
                .map(NewsItem::headline)
                .map(headline -> "• " + headline)
                .collect(Collectors.joining("\n"));
        users.findAllByDailyReportEnabledTrueAndStatus(UserStatus.ACTIVE).forEach(user -> {
            try {
                mail.send(user.getEmail(), "MarketBoard 데일리 보고서", body);
            } catch (RuntimeException ex) {
                log.warn("Daily report delivery failed for user {}", user.getId(), ex);
            }
        });
    }
}
