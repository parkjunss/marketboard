package org.juns.marketboardbackend.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.juns.marketboardbackend.user.Role;
import org.juns.marketboardbackend.user.User;
import org.juns.marketboardbackend.notification.NotificationService;
import org.juns.marketboardbackend.symbol.Symbol;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AlertTriggerSubscriberTest {
    @Mock AlertRepository alerts;
    @Mock SimpMessagingTemplate messaging;
    @Mock Message message;
    @Mock NotificationService notifications;

    @Test
    void enabledPriceNotificationsArePersistedAndPushed() {
        User user = User.builder().email("user@example.com").passwordHash("hash").username("user").role(Role.USER).build();
        Symbol symbol = Symbol.builder().ticker("AAPL").name("Apple").exchange("NASDAQ").priority(1).build();
        Alert alert = Alert.builder().user(user).symbol(symbol).condition(AlertCondition.ABOVE).targetPrice(BigDecimal.TEN).build();
        ReflectionTestUtils.setField(alert, "id", 1L);
        when(alerts.findById(1L)).thenReturn(Optional.of(alert));
        when(message.getBody()).thenReturn(("{\"alertId\":1,\"userId\":2,\"symbol\":\"AAPL\"," +
                "\"condition\":\"ABOVE\",\"targetPrice\":\"10\",\"price\":\"11\"}").getBytes(StandardCharsets.UTF_8));

        new AlertTriggerSubscriber(alerts, messaging, new ObjectMapper(), notifications).onMessage(message, null);

        verify(notifications).createPriceAlert(alert, new BigDecimal("11"));
        verify(messaging).convertAndSendToUser(org.mockito.ArgumentMatchers.eq("2"),
                org.mockito.ArgumentMatchers.eq("/queue/alerts"), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void disabledPriceNotificationsStillMarkAlertTriggeredWithoutPush() {
        User user = User.builder().email("user@example.com").passwordHash("hash").username("user").role(Role.USER).build();
        user.updateProfile("user", false, false, false);
        Alert alert = Alert.builder().user(user).condition(AlertCondition.ABOVE).targetPrice(BigDecimal.TEN).build();
        ReflectionTestUtils.setField(alert, "id", 1L);
        when(alerts.findById(1L)).thenReturn(Optional.of(alert));
        when(message.getBody()).thenReturn(("{\"alertId\":1,\"userId\":2,\"symbol\":\"AAPL\"," +
                "\"condition\":\"ABOVE\",\"targetPrice\":\"10\",\"price\":\"11\"}").getBytes(StandardCharsets.UTF_8));

        new AlertTriggerSubscriber(alerts, messaging, new ObjectMapper(), notifications).onMessage(message, null);

        assertThat(alert.isActive()).isFalse();
        verify(messaging, never()).convertAndSendToUser(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
        verify(notifications, never()).createPriceAlert(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }
}
