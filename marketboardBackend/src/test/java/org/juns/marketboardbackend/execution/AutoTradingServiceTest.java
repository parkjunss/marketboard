package org.juns.marketboardbackend.execution;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.juns.marketboardbackend.collector.CollectorClient;
import org.juns.marketboardbackend.common.exception.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

class AutoTradingServiceTest {
    @TempDir Path directory;
    final String mysqlUrl = System.getenv("EXECUTION_TEST_MYSQL_URL");
    final String schema = "execution_test_" + UUID.randomUUID().toString().replace("-", "");
    JdbcTemplate jdbc;
    TransactionTemplate tx;
    ObjectMapper mapper = new ObjectMapper();
    AutoTradingService service;
    AutoTradingController.Configuration config = new AutoTradingController.Configuration("sma-paper-v1", "TEST",
            LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-07"), 2, "10000", "5", "10", "0.8", "5000", 2);
    @BeforeAll static void start() throws Exception { ExecutionServiceTest.server(); }
    @AfterAll static void stop() { ExecutionServiceTest.stop(); }
    DriverManagerDataSource admin() { return new DriverManagerDataSource(mysqlUrl, "root", System.getenv("EXECUTION_TEST_MYSQL_PASSWORD")); }
    void connect() {
        var source = mysqlUrl == null
                ? new DriverManagerDataSource("jdbc:h2:file:" + directory.resolve("auto") + ";MODE=MySQL;LOCK_TIMEOUT=15000", "sa", "")
                : new DriverManagerDataSource(mysqlUrl + schema + "?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC", "root", System.getenv("EXECUTION_TEST_MYSQL_PASSWORD"));
        jdbc = new JdbcTemplate(source);
        tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        service = new AutoTradingService(jdbc, mapper, new CollectorClient(mapper, "http://127.0.0.1:" + ExecutionServiceTest.port));
    }
    @BeforeEach void setup() throws Exception {
        if (mysqlUrl != null) new JdbcTemplate(admin()).execute("CREATE DATABASE " + schema);
        connect();
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY)");
        jdbc.update("INSERT INTO users VALUES(1),(2)");
        jdbc.execute("CREATE TABLE symbols(id BIGINT PRIMARY KEY,ticker VARCHAR(16))");
        jdbc.update("INSERT INTO symbols VALUES(1,'TEST')");
        jdbc.execute("CREATE TABLE price_history(symbol_id BIGINT,ts TIMESTAMP,open DECIMAL(18,4),close DECIMAL(18,4),timeframe VARCHAR(10))");
        int[] values = {100,110,120,90,80,100,110};
        for (int i=0; i<values.length; i++) jdbc.update("INSERT INTO price_history VALUES(1,?,?,?,'1d')",
                Timestamp.from(Instant.parse("2026-01-0" + (i+1) + "T00:00:00Z")), values[i], values[i]);
        for (var sql : Files.readString(Path.of("src/main/resources/db/migration/V42__create_auto_trading_runs.sql")).split(";"))
            if (!sql.isBlank()) jdbc.execute(sql);
        for (var sql : Files.readString(Path.of("src/main/resources/db/migration/V43__create_auto_trading_strategies.sql")).split(";"))
            if (!sql.isBlank()) jdbc.execute(sql);
        for (var sql : Files.readString(Path.of("src/main/resources/db/migration/V44__create_auto_trading_validations.sql")).split(";"))
            if (!sql.isBlank()) jdbc.execute(sql);
        for (var sql : Files.readString(Path.of("src/main/resources/db/migration/V45__create_auto_trading_cost_analyses.sql")).split(";"))
            if (!sql.isBlank()) jdbc.execute(sql);
    }
    @AfterEach void cleanup() { if (mysqlUrl != null) new JdbcTemplate(admin()).execute("DROP DATABASE IF EXISTS " + schema); }
    AutoTradingService.Run create() { return tx.execute(s -> service.create(1, "create", config)); }
    void due(long id) { jdbc.update("UPDATE auto_trading_runs SET next_tick_at=? WHERE id=?", Timestamp.from(Instant.parse("2020-01-01T00:00:00Z")), id); }
    void tick(long id) { due(id); tx.executeWithoutResult(s -> service.tick(1, id)); }
    long validationStrategy() {
        long source = create().id();
        for (int i=8; i<=11; i++) jdbc.update("INSERT INTO price_history VALUES(1,?,?,?,'1d')",
                Timestamp.from(LocalDate.of(2026, 1, i).atStartOfDay().toInstant(ZoneOffset.UTC)), 100 + i, 110 + i);
        return tx.execute(s -> service.saveStrategy(1, "validation-strategy",
                new AutoTradingController.SaveStrategy("Period test", source, config))).id();
    }
    AutoTradingController.ValidationPeriod validationPeriod() {
        return new AutoTradingController.ValidationPeriod(LocalDate.of(2026, 1, 8), LocalDate.of(2026, 1, 11));
    }
    @Test void costScenariosReuseFrozenPricesAndExportPersistedResults() {
        long strategy = validationStrategy();
        var validation = tx.execute(s -> service.validatePeriod(1, "validation", strategy, validationPeriod()));
        var request = new AutoTradingController.CostRequest(List.of(
                new AutoTradingController.CostScenario("0", "0"), new AutoTradingController.CostScenario("20", "30")));
        jdbc.update("DELETE FROM price_history");
        var result = tx.execute(s -> service.analyzeCosts(1, "cost", validation.summary().id(), request));
        assertThat(result.results().size()).isEqualTo(3);
        assertThat(result.results().get(0).path("result")).isEqualTo(validation.result().path("validation"));
        assertThat(result.results().get(1).path("result").path("strategy").path("fees").asText()).isEqualTo("0.00000000");
        assertThat(result.results().get(2).path("result").path("strategy").path("equity"))
                .isNotEqualTo(result.results().get(1).path("result").path("strategy").path("equity"));
        var retried = tx.execute(s -> service.analyzeCosts(1, "cost", validation.summary().id(), request));
        assertThat(retried).isEqualTo(result);
        connect();
        assertThat(service.costAnalyses(1, validation.summary().id())).hasSize(1);
        assertThat(service.costAnalysis(1, result.summary().id())).isEqualTo(result);
        var csv = CostReport.export(result, "csv");
        var html = CostReport.export(result, "html");
        assertThat(csv.content()).startsWith("\ufeff").contains("Period test", "20", "30", "2026-01-08");
        assertThat(html.content()).contains("<!doctype html>", "2026-01-11", "일별 평가 자산", "<svg", "<polyline");
        assertThat(html.content()).doesNotContain("<script", "https://");
        assertThat(service.verifyValidation(1, validation.summary().id()).get("matches")).isTrue();
        assertThatThrownBy(() -> service.costAnalysis(2, result.summary().id())).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.costAnalyses(2, validation.summary().id())).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> tx.execute(s -> service.analyzeCosts(2, "foreign", validation.summary().id(), request))).isInstanceOf(ResourceNotFoundException.class);
    }
    @Test void invalidCostsAndFailedCalculationLeaveNoPartialAnalysis() {
        long strategy = validationStrategy();
        long id = tx.execute(s -> service.validatePeriod(1, "validation", strategy, validationPeriod())).summary().id();
        var duplicate = new AutoTradingController.CostRequest(List.of(new AutoTradingController.CostScenario("5.0", "10")));
        assertThatThrownBy(() -> tx.execute(s -> service.analyzeCosts(1, "duplicate", id, duplicate))).isInstanceOf(IllegalArgumentException.class);
        var excessive = new AutoTradingController.CostRequest(List.of(new AutoTradingController.CostScenario("101", "0")));
        assertThatThrownBy(() -> tx.execute(s -> service.analyzeCosts(1, "high", id, excessive))).isInstanceOf(IllegalArgumentException.class);
        var valid = new AutoTradingController.CostRequest(List.of(new AutoTradingController.CostScenario("0", "0")));
        var offline = new AutoTradingService(jdbc, mapper, new CollectorClient(mapper, "http://127.0.0.1:1"));
        assertThatThrownBy(() -> tx.execute(s -> offline.analyzeCosts(1, "retry", id, valid))).isInstanceOf(IllegalStateException.class);
        assertThat(service.costAnalyses(1, id)).isEmpty();
        tx.execute(s -> service.analyzeCosts(1, "retry", id, valid));
        assertThatThrownBy(() -> tx.execute(s -> service.analyzeCosts(1, "retry", id, excessive))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void separatePeriodFreezesSettingsPricesAndResultsAndExcludesWarmup() {
        long strategy = validationStrategy();
        var result = tx.execute(s -> service.validatePeriod(1, "validate", strategy, validationPeriod()));
        assertThat(result.warmupCount()).isEqualTo(2);
        assertThat(result.warmupStart()).isEqualTo("2026-01-06");
        assertThat(result.warmupEnd()).isEqualTo("2026-01-07");
        assertThat(result.configuration()).isEqualTo(mapper.valueToTree(config));
        assertThat(result.result().path("training").path("steps").asInt()).isEqualTo(7);
        assertThat(result.result().path("validation").path("steps").asInt()).isEqualTo(4);
        assertThat(result.result().path("validation").path("entryDate").asText()).isEqualTo("2026-01-08");
        assertThat(result.result().path("validation").path("curve").get(0).path("date").asText()).isEqualTo("2026-01-08");
        var retried = tx.execute(s -> service.validatePeriod(1, "validate", strategy, validationPeriod()));
        assertThat(retried).isEqualTo(result);
        assertThat(service.validations(1)).hasSize(1);
        jdbc.update("DELETE FROM price_history");
        connect();
        assertThat(service.validation(1, result.summary().id())).isEqualTo(result);
        assertThat(service.verifyValidation(1, result.summary().id()).get("matches")).isTrue();
        assertThat(service.list(1)).hasSize(1);
        assertThat(service.list(1).get(0).cursor()).isZero();
    }
    @Test void validationRejectsOverlapsMissingPricesAndOtherOwners() {
        long strategy = validationStrategy();
        assertThatThrownBy(() -> tx.execute(s -> service.validatePeriod(2, "foreign", strategy, validationPeriod())))
                .isInstanceOf(ResourceNotFoundException.class);
        var overlap = new AutoTradingController.ValidationPeriod(config.endDate(), LocalDate.of(2026, 1, 11));
        assertThatThrownBy(() -> tx.execute(s -> service.validatePeriod(1, "overlap", strategy, overlap))).isInstanceOf(IllegalArgumentException.class);
        var future = new AutoTradingController.ValidationPeriod(LocalDate.now(ZoneOffset.UTC), LocalDate.now(ZoneOffset.UTC));
        assertThatThrownBy(() -> tx.execute(s -> service.validatePeriod(1, "future", strategy, future))).isInstanceOf(IllegalArgumentException.class);
        var result = tx.execute(s -> service.validatePeriod(1, "valid", strategy, validationPeriod()));
        assertThatThrownBy(() -> service.validation(2, result.summary().id())).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.verifyValidation(2, result.summary().id())).isInstanceOf(ResourceNotFoundException.class);
        assertThat(service.validations(2)).isEmpty();
        assertThatThrownBy(() -> tx.execute(s -> service.validatePeriod(1, "valid", strategy,
                new AutoTradingController.ValidationPeriod(LocalDate.of(2026, 1, 9), LocalDate.of(2026, 1, 11)))))
                .isInstanceOf(IllegalArgumentException.class);
        jdbc.update("DELETE FROM price_history WHERE ts<?", Timestamp.from(Instant.parse("2026-01-08T00:00:00Z")));
        assertThatThrownBy(() -> tx.execute(s -> service.validatePeriod(1, "no-warmup", strategy, validationPeriod()))).isInstanceOf(IllegalArgumentException.class);
        jdbc.update("DELETE FROM price_history");
        assertThatThrownBy(() -> tx.execute(s -> service.validatePeriod(1, "empty", strategy, validationPeriod()))).isInstanceOf(IllegalArgumentException.class);
        assertThat(service.validations(1)).hasSize(1);
    }
    @Test void failedValidationDoesNotPersistPartialReport() {
        long strategy = validationStrategy();
        var offline = new AutoTradingService(jdbc, mapper, new CollectorClient(mapper, "http://127.0.0.1:1"));
        assertThatThrownBy(() -> tx.execute(s -> offline.validatePeriod(1, "retry", strategy, validationPeriod()))).isInstanceOf(IllegalStateException.class);
        assertThat(service.validations(1)).isEmpty();
        assertThat(tx.execute(s -> service.validatePeriod(1, "retry", strategy, validationPeriod())).result().has("validation")).isTrue();
    }

    @Test void namedStrategiesCloneAndReplayFrozenPricesWithoutChangingOriginalRecords() {
        long source = create().id();
        for (int i=0; i<7; i++) tick(source);
        var original = service.detail(1, source);
        var request = new AutoTradingController.SaveStrategy("TEST SMA 2", source, config);
        var saved = tx.execute(s -> service.saveStrategy(1, "save", request));
        assertThat(tx.execute(s -> service.saveStrategy(1, "save", request)).id()).isEqualTo(saved.id());
        jdbc.update("UPDATE price_history SET open=999,close=999");
        connect();
        assertThat(service.strategies(1)).hasSize(1);
        var repeated = tx.execute(s -> service.runStrategy(1, "repeat", saved.id()));
        assertThat(tx.execute(s -> service.runStrategy(1, "repeat", saved.id())).id()).isEqualTo(repeated.id());
        assertThatThrownBy(() -> tx.execute(s -> service.create(1, "repeat", config))).isInstanceOf(IllegalArgumentException.class);
        tick(repeated.id());
        var partial = service.compareRuns(1, source, repeated.id());
        assertThat(partial.comparable()).isTrue();
        assertThat(partial.steps()).isEqualTo(1);
        assertThat(partial.leftResult()).isEqualTo(partial.rightResult());
        for (int i=0; i<6; i++) tick(repeated.id());
        assertThat(service.detail(1, repeated.id()).run().state()).isEqualTo(original.run().state());
        assertThat(service.detail(1, source)).isEqualTo(original);

        var changed = new AutoTradingController.Configuration("sma-paper-v1", "TEST", config.startDate(), config.endDate(),
                3, "10000", "5", "10", "0.8", "5000", 2);
        var copy = tx.execute(s -> service.saveStrategy(1, "copy", new AutoTradingController.SaveStrategy("TEST SMA 3", source, changed)));
        assertThatThrownBy(() -> tx.execute(s -> service.runStrategy(1, "repeat", copy.id()))).isInstanceOf(IllegalArgumentException.class);
        var variant = tx.execute(s -> service.runStrategy(1, "variant", copy.id()));
        for (int i=0; i<7; i++) tick(variant.id());
        var result = service.compareRuns(1, repeated.id(), variant.id());
        assertThat(result.comparable()).isTrue();
        assertThat(result.steps()).isEqualTo(7);
        assertThat(result.leftResult().path("strategy")).isNotEqualTo(result.rightResult().path("strategy"));
        assertThat(service.strategies(1).stream().filter(v -> v.id() == saved.id()).findFirst().orElseThrow().configuration().path("window").asInt()).isEqualTo(2);
        assertThat(tx.execute(s -> service.verify(1, variant.id())).get("matches")).isEqualTo(true);
    }

    @Test void strategyOwnershipAndComparisonConditionsAreEnforced() {
        long source = create().id();
        var request = new AutoTradingController.SaveStrategy("Base", source, config);
        var saved = tx.execute(s -> service.saveStrategy(1, "save", request));
        assertThat(service.strategies(2)).isEmpty();
        assertThatThrownBy(() -> tx.execute(s -> service.saveStrategy(2, "other", request))).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> tx.execute(s -> service.runStrategy(2, "other", saved.id()))).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.compareRuns(2, source, source)).isInstanceOf(ResourceNotFoundException.class);
        var foreign = tx.execute(s -> service.create(2, "foreign", config));
        assertThatThrownBy(() -> service.compareRuns(1, source, foreign.id())).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> tx.execute(s -> service.saveStrategy(1, "save",
                new AutoTradingController.SaveStrategy("Changed", source, config)))).isInstanceOf(IllegalArgumentException.class);
        var differentCost = new AutoTradingController.Configuration("sma-paper-v1", "TEST", config.startDate(), config.endDate(),
                2, "10000", "9", "10", "0.8", "5000", 2);
        assertThatThrownBy(() -> tx.execute(s -> service.saveStrategy(1, "cost",
                new AutoTradingController.SaveStrategy("Cost", source, differentCost)))).isInstanceOf(IllegalArgumentException.class);
        var longWindow = new AutoTradingController.Configuration("sma-paper-v1", "TEST", config.startDate(), config.endDate(),
                7, "10000", "5", "10", "0.8", "5000", 2);
        assertThatThrownBy(() -> tx.execute(s -> service.saveStrategy(1, "long-window",
                new AutoTradingController.SaveStrategy("Too long", source, longWindow)))).isInstanceOf(IllegalArgumentException.class);
        var other = tx.execute(s -> service.create(1, "cost-run", differentCost));
        assertThat(service.compareRuns(1, source, other.id()).differences()).containsExactly("feeBps");
        jdbc.update("UPDATE price_history SET close=998");
        var changedPrices = tx.execute(s -> service.create(1, "new-prices", config));
        var mismatch = service.compareRuns(1, source, changedPrices.id());
        assertThat(mismatch.comparable()).isFalse();
        assertThat(mismatch.differences()).containsExactly("prices");
        assertThat(mismatch.leftResult()).isNull();
    }

    @Test void automaticReplayPersistsFrozenDataAndSurvivesReconnect() {
        long id = create().id();
        assertThat(create().id()).isEqualTo(id);
        jdbc.update("UPDATE price_history SET open=999,close=999");
        tick(id); tick(id);
        connect(); // Reopened file/database and new service.
        for (int i=0; i<5; i++) tick(id);
        var detail = service.detail(1, id);
        assertThat(detail.run().status()).isEqualTo("COMPLETED");
        assertThat(detail.journal()).hasSize(7);
        assertThat(detail.journal().get(2).payload().path("fillPrice").asText()).isEqualTo("120.12000000");
        assertThat(tx.execute(s -> service.verify(1, id)).get("matches")).isEqualTo(true);
        var comparison = service.comparison(1, id);
        assertThat(comparison.path("steps").asInt()).isEqualTo(7);
        assertThat(comparison.path("strategy").path("equity")).isEqualTo(detail.run().state().path("equity"));
        assertThat(comparison.path("entryDate").asText()).isEqualTo("2026-01-03");
        assertThatThrownBy(() -> service.comparison(2, id)).isInstanceOf(ResourceNotFoundException.class);
        tick(id);
        assertThat(service.detail(1, id).journal()).hasSize(7);
        assertThatThrownBy(() -> tx.execute(s -> service.control(1, id, "RESUME"))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void concurrentTicksAndRollbackCannotDuplicateOrSkipDates() throws Exception {
        long id = create().id();
        due(id);
        var pool = Executors.newFixedThreadPool(4);
        try {
            var futures = new ArrayList<Future<?>>();
            for (int i=0; i<4; i++) futures.add(pool.submit(() -> tx.executeWithoutResult(s -> service.tick(1, id))));
            for (var f : futures) f.get(15, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        assertThat(service.detail(1, id).run().cursor()).isEqualTo(1);
        due(id);
        tx.executeWithoutResult(s -> { service.tick(1, id); s.setRollbackOnly(); });
        assertThat(service.detail(1, id).journal()).hasSize(1);
        tick(id);
        assertThat(service.detail(1, id).run().cursor()).isEqualTo(2);
    }
    @Test void pauseAndResumeAreDurableAndIdempotent() {
        long id = create().id();
        tx.execute(s -> service.control(1, id, "PAUSE"));
        tx.execute(s -> service.control(1, id, "PAUSE"));
        tick(id);
        connect();
        assertThat(service.detail(1, id).journal()).isEmpty();
        assertThat(service.detail(1, id).controls()).hasSize(2); // START, PAUSE.
        tx.execute(s -> service.control(1, id, "RESUME"));
        tick(id);
        assertThat(service.detail(1, id).run().cursor()).isEqualTo(1);
    }
    @Test void collectorFailurePreservesCursorAndCanResume() {
        long id = create().id();
        tick(id);
        var offline = new AutoTradingService(jdbc, mapper, new CollectorClient(mapper, "http://127.0.0.1:1"));
        due(id);
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> offline.tick(1, id))).isInstanceOf(IllegalStateException.class);
        tx.executeWithoutResult(s -> service.fail(1, id));
        assertThat(service.detail(1, id).run().status()).isEqualTo("ERROR");
        assertThat(service.detail(1, id).journal()).hasSize(1);
        tx.execute(s -> service.control(1, id, "RESUME"));
        tick(id);
        assertThat(service.detail(1, id).run().cursor()).isEqualTo(2);
    }
    @Test void stopRetainsRecordsAndCannotBeResumed() {
        long id = create().id();
        tick(id);
        tx.execute(s -> service.control(1, id, "STOP"));
        tx.execute(s -> service.control(1, id, "STOP"));
        tick(id);
        assertThat(service.detail(1, id).run().status()).isEqualTo("STOPPED");
        assertThat(service.detail(1, id).journal()).hasSize(1);
        assertThat(service.detail(1, id).controls()).hasSize(2);
        assertThatThrownBy(() -> tx.execute(s -> service.control(1, id, "RESUME"))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void ownershipAndInputBoundariesApplyToAllPaths() {
        long id = create().id();
        assertThat(service.list(2)).isEmpty();
        assertThatThrownBy(() -> service.detail(2, id)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> tx.execute(s -> service.control(2, id, "PAUSE"))).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> tx.execute(s -> service.verify(2, id))).isInstanceOf(ResourceNotFoundException.class);
        jdbc.update("DELETE FROM price_history");
        assertThatThrownBy(() -> tx.execute(s -> service.create(1, "empty", config))).isInstanceOf(IllegalArgumentException.class);
        assertThat(service.list(1)).hasSize(1);
    }
}
