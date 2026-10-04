package org.juns.marketboardbackend.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.juns.marketboardbackend.collector.NewsItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NewsTranslationServiceTest {
    @Mock NewsTranslationClient client;

    @Test
    void translatesHeadlineAndSummaryInOneBatch() {
        NewsItem item = news(1L, "Stocks rise", "Markets closed higher");
        when(client.translateToKorean(List.of("Stocks rise", "Markets closed higher")))
                .thenReturn(List.of("주가 상승", "시장은 상승 마감했습니다"));

        List<NewsItem> result = new NewsTranslationService(client).translateNewItems(List.of(item), List.of());

        assertThat(result.get(0).headlineKo()).isEqualTo("주가 상승");
        assertThat(result.get(0).summaryKo()).isEqualTo("시장은 상승 마감했습니다");
    }

    @Test
    void reusesStoredTranslationWhenSourceTextIsUnchanged() {
        NewsItem current = news(1L, "Stocks rise", "Markets closed higher");
        NewsItem previous = current.withKorean("주가 상승", "시장은 상승 마감했습니다");

        List<NewsItem> result = new NewsTranslationService(client)
                .translateNewItems(List.of(current), List.of(previous));

        assertThat(result).containsExactly(previous);
        verify(client).translateToKorean(List.of());
    }

    @Test
    void keepsOriginalAvailableWhenProviderFails() {
        NewsItem item = news(1L, "Stocks rise", "Markets closed higher");
        when(client.translateToKorean(List.of("Stocks rise", "Markets closed higher"))).thenReturn(List.of());

        NewsItem result = new NewsTranslationService(client).translateNewItems(List.of(item), List.of()).get(0);

        assertThat(result.headline()).isEqualTo("Stocks rise");
        assertThat(result.headlineKo()).isNull();
        assertThat(result.summaryKo()).isNull();
    }

    private NewsItem news(long id, String headline, String summary) {
        return new NewsItem("general", 1_700_000_000L, headline, id, "", "", "Reuters", summary,
                "https://example.com/" + id);
    }
}
