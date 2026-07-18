package org.juns.marketboardbackend.collector;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public record CollectorHealth(
        @JsonProperty("ws_connected") boolean wsConnected,
        @JsonProperty("reconnect_count") int reconnectCount,
        @JsonProperty("subscribed_symbols") List<String> subscribedSymbols,
        @JsonProperty("last_tick_at") Map<String, String> lastTickAt,
        @JsonProperty("last_error") String lastError) {
}
