package org.juns.marketboardbackend.mail;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import org.juns.marketboardbackend.collector.NewsItem;
import org.juns.marketboardbackend.common.exception.ResourceNotFoundException;
import org.juns.marketboardbackend.news.NewsService;
import org.juns.marketboardbackend.notification.NotificationService;
import org.juns.marketboardbackend.user.Role;
import org.juns.marketboardbackend.user.User;
import org.juns.marketboardbackend.user.UserRepository;
import org.juns.marketboardbackend.user.UserStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DailyReportMailerTest {
    @Mock UserRepository users;
    @Mock NewsService news;
    @Mock MailService mail;
    @Mock NotificationService notifications;

    @Test
    void missingNewsCreatesImportantInfoInsteadOfFailing() {
        when(news.getGeneralNews()).thenThrow(new ResourceNotFoundException("missing"));

        new DailyReportMailer(users, news, mail, notifications).send();

        verify(notifications).createImportantInfoForEnabled(
                "daily-report:no-news:" + LocalDate.now(), "데일리 보고서가 지연되고 있습니다",
                "최신 뉴스 자료가 없어 보고서를 만들지 못했습니다.", "/news");
        verify(mail, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void reportCreatesInboxNotificationAndEmail() {
        User user = User.builder().email("user@example.com").passwordHash("hash").username("user").role(Role.USER).build();
        NewsItem item = new NewsItem("general", 0, "시장 뉴스", 1, "", "", "source", "", "url");
        when(news.getGeneralNews()).thenReturn(List.of(item));
        when(users.findAllByDailyReportEnabledTrueAndStatus(UserStatus.ACTIVE)).thenReturn(List.of(user));

        new DailyReportMailer(users, news, mail, notifications).send();

        verify(notifications).createDailyReport(user, LocalDate.now());
        verify(mail).send("user@example.com", "MarketBoard 데일리 보고서", "• 시장 뉴스");
    }
}
