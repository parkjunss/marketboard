package org.juns.marketboardbackend.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import org.juns.marketboardbackend.collector.CollectorClient;
import org.juns.marketboardbackend.collector.NewsItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * Same behavior SectorPerformanceServiceTest/PutCallRatioServiceTest lock in: a request never
 * blocks on a live collector fetch, and a failed/empty refresh must NOT wipe out the last good
 * snapshot.
 */
@SpringBootTest
@Transactional
class NewsServiceTest {

    @Autowired
    private NewsService newsService;

    @MockitoBean
    private CollectorClient collectorClient;

    @Test
    void refreshPersistsAndGetGeneralNewsReadsItBack() {
        List<NewsItem> news = List.of(new NewsItem("general", 1_700_000_000L, "Headline", 1L, "", "", "Reuters", "summary", "https://example.com"));
        when(collectorClient.getGeneralNews()).thenReturn(news);

        newsService.refresh();

        assertThat(newsService.getGeneralNews()).isEqualTo(news);
    }

    @Test
    void emptyRefreshKeepsThePreviousSnapshot() {
        List<NewsItem> firstGood = List.of(new NewsItem("general", 1_700_000_000L, "Headline", 1L, "", "", "Reuters", "summary", "https://example.com"));
        when(collectorClient.getGeneralNews()).thenReturn(firstGood);
        newsService.refresh();

        when(collectorClient.getGeneralNews()).thenReturn(List.of());
        newsService.refresh();

        assertThat(newsService.getGeneralNews()).isEqualTo(firstGood);
    }
}
