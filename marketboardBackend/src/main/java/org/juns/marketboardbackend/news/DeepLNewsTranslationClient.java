package org.juns.marketboardbackend.news;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import java.net.http.HttpClient;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class DeepLNewsTranslationClient implements NewsTranslationClient {
    private static final Logger log = LoggerFactory.getLogger(DeepLNewsTranslationClient.class);
    private static final int BATCH_SIZE = 40;

    private final RestClient restClient;
    private final String apiKey;

    public DeepLNewsTranslationClient(
            @Value("${news.translation.base-url:https://api-free.deepl.com}") String baseUrl,
            @Value("${news.translation.api-key:}") String apiKey) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.apiKey = apiKey;
    }

    @Override
    public List<String> translateToKorean(List<String> texts) {
        if (apiKey.isBlank() || texts.isEmpty()) return List.of();
        List<String> translated = new ArrayList<>(texts.size());
        try {
            for (int start = 0; start < texts.size(); start += BATCH_SIZE) {
                List<String> batch = texts.subList(start, Math.min(start + BATCH_SIZE, texts.size()));
                TranslationResponse response = restClient.post()
                        .uri("/v2/translate")
                        .header("Authorization", "DeepL-Auth-Key " + apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(new TranslationRequest(batch, "KO"))
                        .retrieve()
                        .body(TranslationResponse.class);
                if (response == null || response.translations() == null || response.translations().size() != batch.size()) {
                    return List.of();
                }
                response.translations().stream().map(Translation::text).forEach(translated::add);
            }
            return translated;
        } catch (RestClientException ex) {
            log.warn("News translation failed; original text will be kept", ex);
            return List.of();
        }
    }

    record TranslationRequest(List<String> text, @JsonProperty("target_lang") String targetLang) {}
    record TranslationResponse(List<Translation> translations) {}
    record Translation(String text) {}
}
