package org.juns.marketboardbackend.collector;

public record NewsItem(
        String category, long datetime, String headline, long id, String image, String related, String source,
        String summary, String url, String headlineKo, String summaryKo) {

    public NewsItem(String category, long datetime, String headline, long id, String image, String related,
                    String source, String summary, String url) {
        this(category, datetime, headline, id, image, related, source, summary, url, null, null);
    }

    public NewsItem withKorean(String translatedHeadline, String translatedSummary) {
        return new NewsItem(category, datetime, headline, id, image, related, source, summary, url,
                translatedHeadline, translatedSummary);
    }
}
