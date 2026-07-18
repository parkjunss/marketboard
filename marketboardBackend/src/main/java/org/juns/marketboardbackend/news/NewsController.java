package org.juns.marketboardbackend.news;

import java.util.List;
import org.juns.marketboardbackend.collector.CollectorClient;
import org.juns.marketboardbackend.collector.NewsItem;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/news")
public class NewsController {

    private final CollectorClient collectorClient;

    public NewsController(CollectorClient collectorClient) {
        this.collectorClient = collectorClient;
    }

    @GetMapping
    public List<NewsItem> getGeneralNews() {
        return collectorClient.getGeneralNews();
    }

    @GetMapping("/{ticker}")
    public List<NewsItem> getCompanyNews(@PathVariable String ticker) {
        return collectorClient.getCompanyNews(ticker);
    }
}
