package org.juns.marketboardbackend.collector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;
import org.juns.marketboardbackend.backtest.dto.EtfComparisonRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class EtfMomentumClientTest {
    private HttpServer server;
    private CollectorClient client;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        client = new CollectorClient(new ObjectMapper(), "http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stop() { server.stop(0); }

    private void respond(int status, String json) {
        server.createContext("/strategies/etf-momentum", exchange -> {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var body = exchange.getResponseBody()) { body.write(bytes); }
        });
    }

    @Test
    void readsCollectorContractIncludingNullsAndDates() {
        respond(200, """
                {"asOf":"2025-09-30","startDate":"2024-09-30","endDate":"2025-08-31",
                 "ready":false,"cashWeight":null,"rows":[
                   {"ticker":"GLD","rank":null,"momentum":null,"close":null,"sma200":null,
                    "sessionDate":null,"weight":null,"reason":"일봉 데이터 없음"}]}
                """);
        var result = client.getEtfMomentum().orElseThrow();
        assertEquals(LocalDate.of(2025, 9, 30), result.asOf());
        assertNull(result.cashWeight());
        assertNull(result.rows().get(0).weight());
        assertEquals("일봉 데이터 없음", result.rows().get(0).reason());
    }

    @Test
    void collectorFailureIsNotAValidResult() {
        respond(502, "{\"detail\":\"ETF 전략 계산에 실패했습니다.\"}");
        assertTrue(client.getEtfMomentum().isEmpty());
    }

    @Test
    void invalidJsonIsNotAValidResult() {
        respond(200, "not json");
        assertTrue(client.getEtfMomentum().isEmpty());
    }

    private EtfComparisonRequest comparisonRequest() {
        return new EtfComparisonRequest(LocalDate.parse("2024-10-01"), LocalDate.parse("2024-11-29"),
                new BigDecimal("10000"), new BigDecimal("5"), new BigDecimal("10"));
    }

    @Test
    void postsComparisonAndReadsMetricsTradesAndRebalances() {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> method = new AtomicReference<>();
        server.createContext("/backtest/etf-comparison", exchange -> {
            method.set(exchange.getRequestMethod());
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = """
                    {"ready":true,"message":null,"requestedStartDate":"2024-10-01","requestedEndDate":"2024-11-29",
                     "startDate":"2024-10-01","endDate":"2024-11-29","initialCapital":10000,"feeBps":5,
                     "slippageBps":10,"cashRatePct":0,"currency":"USD","strategies":[
                       {"strategyId":"SPY_HOLD","label":"SPY 단순 보유","metrics":{"totalReturnPct":-0.15,
                        "cagrPct":-0.9,"maxDrawdownPct":-0.15,"volatilityPct":0.1,"sharpeRatio":null},
                        "equityCurve":[{"date":"2024-10-01","value":9985.02,"returnPct":-0.1498}],
                        "totalFees":4.99,"totalSlippage":9.99,"turnoverPct":99.85,
                        "trades":[{"ticker":"SPY","side":"BUY","quantity":99.85,"price":100,
                         "notional":9985,"fee":4.99,"slippage":9.99,"signalDate":"2024-09-30","executionDate":"2024-10-01"}],
                        "rebalances":[{"signalDate":"2024-09-30","executionDate":"2024-10-01","cashWeight":0,
                         "targets":[{"ticker":"SPY","weight":1}]}],
                        "regimes":[{"label":"횡보","days":44,"returnPct":-0.15}]}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var body = exchange.getResponseBody()) { body.write(bytes); }
        });
        var result = client.compareEtfStrategies(comparisonRequest()).orElseThrow();
        assertEquals("POST", method.get());
        var posted = new ObjectMapper().readTree(requestBody.get());
        assertEquals("2024-10-01", posted.get("startDate").asText());
        assertEquals(10, posted.get("slippageBps").asInt());
        assertEquals("USD", result.currency());
        assertEquals(new BigDecimal("-0.15"), result.strategies().get(0).metrics().totalReturnPct());
        assertEquals(LocalDate.parse("2024-09-30"), result.strategies().get(0).trades().get(0).signalDate());
        assertEquals(BigDecimal.ONE, result.strategies().get(0).rebalances().get(0).targets().get(0).weight());
    }

    @Test
    void brokenComparisonJsonReturnsUnavailable() {
        server.createContext("/backtest/etf-comparison", exchange -> {
            byte[] bytes = "not json".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var body = exchange.getResponseBody()) { body.write(bytes); }
        });
        assertTrue(client.compareEtfStrategies(comparisonRequest()).isEmpty());
    }
}
