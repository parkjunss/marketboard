package org.juns.marketboardbackend.execution;

import static org.assertj.core.api.Assertions.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.juns.marketboardbackend.collector.CollectorClient;
import org.juns.marketboardbackend.common.exception.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

class ExecutionServiceTest {
    @TempDir Path directory;
    static Process python;
    static int port;
    JdbcTemplate jdbc;
    TransactionTemplate transaction;
    ObjectMapper mapper = new ObjectMapper();
    ExecutionService service;
    ExecutionOperations operations;
    final String mysqlUrl = System.getenv("EXECUTION_TEST_MYSQL_URL");
    final String mysqlSchema = "execution_test_" + UUID.randomUUID().toString().replace("-", "");
    DriverManagerDataSource mysqlAdmin() {
        return new DriverManagerDataSource(mysqlUrl, "root", System.getenv("EXECUTION_TEST_MYSQL_PASSWORD"));
    }
    final ExecutionController.Configuration configuration = new ExecutionController.Configuration("execution-v1", "10000", "5", "5000", "0.8");

    static void startPython(int requestedPort) throws Exception {
        python = new ProcessBuilder(System.getProperty("execution.python", "python"),
                "../collector/tests/execution_http_fixture.py", String.valueOf(requestedPort))
                .redirectError(ProcessBuilder.Redirect.INHERIT).start();
        var reader = new BufferedReader(new InputStreamReader(python.getInputStream()));
        var ready = CompletableFuture.supplyAsync(() -> {
            try { return reader.readLine(); } catch (IOException ex) { throw new UncheckedIOException(ex); }
        });
        port = Integer.parseInt(ready.get(15, TimeUnit.SECONDS));
    }
    @BeforeAll static void server() throws Exception { startPython(0); }
    @AfterAll static void stop() { if (python != null) python.destroyForcibly(); }
    void connect() {
        var source = new DriverManagerDataSource("jdbc:h2:file:" + directory.resolve("execution").toAbsolutePath() + ";MODE=MySQL;LOCK_TIMEOUT=15000", "sa", "");
        if (mysqlUrl != null) source = new DriverManagerDataSource(
                mysqlUrl + mysqlSchema + "?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC",
                "root", System.getenv("EXECUTION_TEST_MYSQL_PASSWORD"));
        jdbc = new JdbcTemplate(source);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        operations = new ExecutionOperations(jdbc, new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        service = new ExecutionService(jdbc, mapper, new CollectorClient(mapper, "http://127.0.0.1:" + port), operations);
    }
    @BeforeEach void setup() throws Exception {
        if (mysqlUrl != null) new JdbcTemplate(mysqlAdmin()).execute("CREATE DATABASE " + mysqlSchema);
        connect();
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY)");
        jdbc.update("INSERT INTO users VALUES(1),(2)");
        for (var sql : Files.readString(Path.of("src/main/resources/db/migration/V40__create_execution_runs.sql")).split(";"))
            if (!sql.isBlank()) jdbc.execute(sql);
        for (var sql : Files.readString(Path.of("src/main/resources/db/migration/V41__create_execution_incidents.sql")).split(";"))
            if (!sql.isBlank()) jdbc.execute(sql);
    }
    @AfterEach void removeIsolatedMysqlSchema() {
        if (mysqlUrl != null) new JdbcTemplate(mysqlAdmin()).execute("DROP DATABASE IF EXISTS " + mysqlSchema);
    }
    ExecutionService.Run create() { return transaction.execute(s -> service.create(1, "start", configuration)); }
    ExecutionService.Run apply(long id, String key, String operation, String order, String quantity) {
        return transaction.execute(s -> service.apply(1, id, new ExecutionController.Command(key, operation, order, quantity)));
    }
    @Test void processRestartRestoresUncertainOrderFromRealDatabase() throws Exception {
        long id = create().id();
        apply(id, "buy", "BUY", null, "10");
        apply(id, "dispatch", "DISPATCH", "buy", null);
        apply(id, "timeout", "TIMEOUT", "buy", null);
        apply(id, "partial", "PARTIAL_FILL", "buy", "4");
        apply(id, "cancel", "CANCEL", "buy", null);
        python.destroyForcibly();
        assertThat(python.waitFor(10, TimeUnit.SECONDS)).isTrue();
        startPython(port);
        connect(); // New service/client, reopened on-disk database, fresh Python process.
        var result = apply(id, "recover", "REVERSE", "buy", null);
        assertThat(result.state().path("cash").asText()).isEqualTo("9599.80000000");
        assertThat(result.state().path("reservedCash").asText()).isEqualTo("0.00000000");
        assertThat(result.state().path("orders").path("buy").path("status").asText()).isEqualTo("CANCELLED");
        assertThat(transaction.execute(s -> service.verify(1, id)).matches()).isTrue();
    }
    @Test void concurrentWorkersApplySameCommandOnceAndCreationIsIdempotent() throws Exception {
        long id = create().id();
        assertThat(create().id()).isEqualTo(id);
        var pool = Executors.newFixedThreadPool(4);
        try {
            var futures = new ArrayList<Future<ExecutionService.Run>>();
            for (int i = 0; i < 4; i++) futures.add(pool.submit(() -> apply(id, "buy", "BUY", null, "10")));
            for (var future : futures) assertThat(future.get(20, TimeUnit.SECONDS).version()).isEqualTo(1);
        } finally { pool.shutdownNow(); }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM execution_commands", Integer.class)).isEqualTo(1);
        assertThat(service.get(1, id).state().path("reservedCash").asText()).isEqualTo("1000.50000000");
        assertThatThrownBy(() -> apply(id, "buy", "BUY", null, "20")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void transactionRollbackLeavesNoJournalOrStateChange() {
        long id = create().id();
        var original = service.get(1, id);
        transaction.executeWithoutResult(s -> {
            service.apply(1, id, new ExecutionController.Command("buy", "BUY", null, "10"));
            s.setRollbackOnly(); // Failure after both SQL writes, before commit.
        });
        assertThat(service.get(1, id)).isEqualTo(original);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM execution_commands", Integer.class)).isZero();
        assertThat(apply(id, "buy", "BUY", null, "10").version()).isEqualTo(1);
    }
    @Test void invalidCommandAndCollectorOutagePreserveCommittedState() {
        long id = create().id();
        var original = service.get(1, id);
        assertThatThrownBy(() -> apply(id, "bad", "PARTIAL_FILL", "missing", "4")).isInstanceOf(IllegalArgumentException.class);
        var offline = new ExecutionService(jdbc, mapper, new CollectorClient(mapper, "http://127.0.0.1:1"), operations);
        assertThatThrownBy(() -> transaction.execute(s -> offline.apply(1, id,
                new ExecutionController.Command("buy", "BUY", null, "10")))).isInstanceOf(IllegalStateException.class);
        assertThat(service.get(1, id)).isEqualTo(original);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM execution_commands", Integer.class)).isZero();
    }
    @Test void ownershipAppliesToReadWriteAndReplay() {
        long id = create().id();
        assertThat(service.list(2)).isEmpty();
        assertThatThrownBy(() -> service.get(2, id)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> transaction.execute(s -> service.apply(2, id,
                new ExecutionController.Command("stop", "STOP", null, null)))).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> transaction.execute(s -> service.verify(2, id))).isInstanceOf(ResourceNotFoundException.class);
    }

    List<ExecutionOperations.Incident> incidents(long id) { return transaction.execute(s -> operations.list(1, id)); }

    @Test void incidentsDeduplicateSurviveReconnectResolveAndReopen() {
        long id = create().id();
        apply(id, "buy", "BUY", null, "10");
        apply(id, "d", "DISPATCH", "buy", null);
        apply(id, "t", "TIMEOUT", "buy", null);
        var first = transaction.execute(s -> operations.list(1, id));
        assertThat(first).hasSize(2).allMatch(ExecutionOperations.Incident::active);
        apply(id, "t2", "TIMEOUT", "buy", null);
        connect();
        var second = transaction.execute(s -> operations.list(1, id));
        assertThat(second.stream().map(ExecutionOperations.Incident::openedAt)).containsExactlyElementsOf(first.stream().map(ExecutionOperations.Incident::openedAt).toList());
        assertThat(second).allMatch(i -> i.occurrences() == 1);
        apply(id, "r", "RECONCILE", "buy", null);
        assertThat(incidents(id)).noneMatch(ExecutionOperations.Incident::active);
        apply(id, "t3", "TIMEOUT", "buy", null);
        assertThat(transaction.execute(s -> operations.list(1, id)).stream().filter(i -> i.kind().equals("UNKNOWN")).findFirst().orElseThrow().occurrences()).isEqualTo(2);
        assertThatThrownBy(() -> transaction.execute(s -> operations.list(2, id))).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test void verificationShowsExactDifferenceAndResolvesAfterRepair() {
        long id = create().id();
        var original = service.get(1, id).state();
        var corrupted = original.deepCopy();
        ((tools.jackson.databind.node.ObjectNode) corrupted).put("cash", "9999.00000000");
        jdbc.update("UPDATE execution_runs SET state_json=? WHERE id=?", mapper.writeValueAsString(corrupted), id);
        var check = transaction.execute(s -> service.verify(1, id));
        assertThat(check.matches()).isFalse();
        assertThat(check.differences()).anyMatch(d -> d.path().equals("/cash"));
        assertThatThrownBy(() -> apply(id, "b", "BUY", null, "1")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(incidents(id)).anyMatch(i -> i.kind().equals("REPLAY_MISMATCH") && i.active());
        jdbc.update("UPDATE execution_runs SET state_json=? WHERE id=?", mapper.writeValueAsString(original), id);
        assertThat(transaction.execute(s -> service.verify(1, id)).matches()).isTrue();
        assertThat(incidents(id)).noneMatch(ExecutionOperations.Incident::active);
    }

    @Test void failureAlertSurvivesRollbackAndClearsOnlyOnSuccess() {
        long id = create().id();
        transaction.executeWithoutResult(s -> operations.failure(1, id));
        transaction.executeWithoutResult(s -> operations.failure(1, id));
        assertThat(transaction.execute(s -> operations.list(1, id)).get(0).occurrences()).isEqualTo(1);
        apply(id, "b", "BUY", null, "1");
        assertThat(incidents(id)).noneMatch(ExecutionOperations.Incident::active);
    }
    @Test void staleRiskAndMetricsTrackRecovery() {
        long id = create().id();
        var registry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        var metrics = new ExecutionOperations(jdbc, registry);
        apply(id, "stale", "STALE_BUY", null, "1");
        metrics.sample();
        assertThat(registry.get("marketboard.execution.incidents").tag("kind", "STALE_PRICE").gauge().value()).isEqualTo(1);
        apply(id, "normal", "BUY", null, "1");
        metrics.sample();
        assertThat(registry.get("marketboard.execution.incidents").tag("kind", "STALE_PRICE").gauge().value()).isZero();
        assertThat(service.timeline(1, id)).hasSize(2);
        assertThatThrownBy(() -> service.timeline(2, id)).isInstanceOf(ResourceNotFoundException.class);
    }
}
