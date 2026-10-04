package org.juns.marketboardbackend.news;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.juns.marketboardbackend.collector.NewsItem;
import org.springframework.stereotype.Service;

@Service
public class NewsTranslationService {
    private final NewsTranslationClient client;

    public NewsTranslationService(NewsTranslationClient client) {
        this.client = client;
    }

    public List<NewsItem> translateNewItems(List<NewsItem> current, List<NewsItem> previous) {
        Map<Long, NewsItem> previousById = new HashMap<>();
        previous.forEach(item -> previousById.put(item.id(), item));

        List<String> texts = new ArrayList<>();
        List<TextTarget> targets = new ArrayList<>();
        String[] headlines = new String[current.size()];
        String[] summaries = new String[current.size()];

        for (int index = 0; index < current.size(); index++) {
            NewsItem item = current.get(index);
            NewsItem old = previousById.get(item.id());
            if (old != null && Objects.equals(old.headline(), item.headline())) headlines[index] = old.headlineKo();
            if (old != null && Objects.equals(old.summary(), item.summary())) summaries[index] = old.summaryKo();
            if (headlines[index] == null && hasText(item.headline())) {
                targets.add(new TextTarget(index, true));
                texts.add(item.headline());
            }
            if (summaries[index] == null && hasText(item.summary())) {
                targets.add(new TextTarget(index, false));
                texts.add(item.summary());
            }
        }

        List<String> translated = client.translateToKorean(texts);
        if (translated.size() == targets.size()) {
            for (int i = 0; i < targets.size(); i++) {
                TextTarget target = targets.get(i);
                if (target.headline()) headlines[target.itemIndex()] = translated.get(i);
                else summaries[target.itemIndex()] = translated.get(i);
            }
        }

        List<NewsItem> result = new ArrayList<>(current.size());
        for (int index = 0; index < current.size(); index++) {
            result.add(current.get(index).withKorean(headlines[index], summaries[index]));
        }
        return result;
    }

    private boolean hasText(String text) {
        return text != null && !text.isBlank();
    }

    private record TextTarget(int itemIndex, boolean headline) {}
}
