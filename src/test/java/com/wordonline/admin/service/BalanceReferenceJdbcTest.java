package com.wordonline.admin.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "BALANCE_TEST_DB_URL", matches = ".+")
class BalanceReferenceJdbcTest {
    private JdbcTemplate database(String schema) throws Exception {
        String url = System.getenv("BALANCE_TEST_DB_URL");
        String user = System.getenv("BALANCE_TEST_DB_USER");
        var setup = new JdbcTemplate(new DriverManagerDataSource(url, user, ""));
        setup.execute("create schema " + schema);
        var source = new DriverManagerDataSource(url + "?currentSchema=" + schema, user, "");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("""
                create table game_objects(id bigint primary key, name text unique);
                create table parameters(id bigint primary key, name text unique);
                create table parameter_values(game_object_id bigint, parameter_id bigint, value double precision);
                create table tags(id bigint, name text);
                create table game_object_tags(game_object_id bigint, tag_id bigint);
                create table magics(name text, cast_kind text);
                create table magic_game_object_aliases(magic_name text, game_object_name text);
                insert into game_objects values (1,'unit'),(2,'shot');
                insert into parameters values (1,'damage'),(2,'radius');
                insert into parameter_values values (1,1,20),(2,1,50),(2,2,4);
                """);
        try (var connection = source.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(System.getenv("BALANCE_TEST_MIGRATION")));
        }
        return jdbc;
    }

    @Test
    void persistsReferencesPerDatabaseAndSerializesConcurrentCycleChecks() throws Exception {
        var primary = database("balance_primary");
        var secondary = database("balance_secondary");
        var sheets = new BalanceSheetService(primary, Optional.of(secondary));
        var service = new BalanceReferenceService(primary, Optional.of(secondary), sheets);
        service.save(new BalanceReferenceService.Change("secondary", "unit", "attack_area_radius", "shot", "radius", 0.5, false));
        var reloaded = new BalanceSheetService(primary, Optional.of(secondary)).load();
        assertTrue(reloaded.primary().references().isEmpty());
        assertEquals(0.5, reloaded.secondary().references().get("unit").get("attack_area_radius").multiplier());
        assertEquals(20.0, reloaded.secondary().objects().get("unit").values().get("damage"));
        service.save(new BalanceReferenceService.Change("secondary", "unit", "attack_area_radius", null, null, null, true));
        assertTrue(sheets.load().secondary().references().isEmpty());
        assertThrows(ResponseStatusException.class, () -> service.save(new BalanceReferenceService.Change("primary", "unit", "damage", "unit", "damage", 1.0, false)));
        assertTrue(sheets.load().primary().references().isEmpty());
        var start = new CountDownLatch(1);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Callable<Boolean> forward = () -> saveAfter(start, service, "unit", "shot");
            Callable<Boolean> backward = () -> saveAfter(start, service, "shot", "unit");
            var first = pool.submit(forward); var second = pool.submit(backward); start.countDown();
            assertNotEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        }
        assertEquals(1, primary.queryForObject("select count(*) from balance_parameter_references", Integer.class));
    }

    private boolean saveAfter(CountDownLatch start, BalanceReferenceService service, String from, String to) throws Exception {
        start.await();
        try {
            service.save(new BalanceReferenceService.Change("primary", from, "damage", to, "damage", 1.0, false));
            return true;
        } catch (ResponseStatusException expected) {
            return false;
        }
    }
}
