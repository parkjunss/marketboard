package org.juns.marketboardbackend.marketreport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.event.EventListener;

class MarketReportSchedulerTest {
    final MarketReportService service = mock(MarketReportService.class);
    final TaskScheduler timer = mock(TaskScheduler.class);
    final MarketReportScheduler scheduler = new MarketReportScheduler(service, timer);

    MarketReportService.Generation result(String state) {
        return new MarketReportService.Generation(state, null, null, List.of());
    }

    @Test void delayedRetriesAtThirtyMinutesAndStopsAfterTwoRetries() {
        var tasks = new ArrayList<Runnable>();
        when(service.generate()).thenReturn(result("DELAYED"));
        when(timer.schedule(any(Runnable.class), any(Instant.class))).thenAnswer(call -> {
            Instant when = call.getArgument(1);
            assertThat(when).isBetween(Instant.now().plusSeconds(1795), Instant.now().plusSeconds(1805));
            tasks.add(call.getArgument(0)); return null;
        });
        scheduler.publish();
        assertThat(tasks).hasSize(1);
        tasks.get(0).run();
        assertThat(tasks).hasSize(2);
        tasks.get(1).run();
        assertThat(tasks).hasSize(2);
        verify(service, times(3)).generate();
    }

    @Test void publishedOrExistingNeverSchedulesRetry() {
        when(service.generate()).thenReturn(result("PUBLISHED"), result("EXISTING"));
        scheduler.publish(); scheduler.publish();
        verifyNoInteractions(timer);
    }

    @Test void successOnFirstRetryStopsChain() {
        var tasks = new ArrayList<Runnable>();
        when(service.generate()).thenReturn(result("DELAYED"), result("PUBLISHED"));
        when(timer.schedule(any(Runnable.class), any(Instant.class))).thenAnswer(call -> {
            tasks.add(call.getArgument(0)); return null;
        });
        scheduler.publish(); tasks.get(0).run();
        assertThat(tasks).hasSize(1);
    }

    @Test void unexpectedFailureDoesNotScheduleShortRetry() {
        when(service.generate()).thenThrow(new IllegalStateException("database unavailable"));
        scheduler.publish(); verifyNoInteractions(timer);
    }

    @Test void dailyUtcScheduleHasNoStartupListener() throws Exception {
        var method = MarketReportScheduler.class.getMethod("publish");
        assertThat(method.getAnnotation(Scheduled.class).cron()).isEqualTo("${market-report.cron:0 40 8 * * *}");
        assertThat(method.getAnnotation(Scheduled.class).zone()).isEqualTo("UTC");
        assertThat(method.getAnnotation(EventListener.class)).isNull();
    }
}
