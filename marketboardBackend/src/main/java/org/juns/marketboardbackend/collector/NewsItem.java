package org.juns.marketboardbackend.collector;

public record NewsItem(
        String category, long datetime, String headline, long id, String image, String related, String source,
        String summary, String url) {
}
