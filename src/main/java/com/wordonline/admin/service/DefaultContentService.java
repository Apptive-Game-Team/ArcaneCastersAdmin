package com.wordonline.admin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DefaultContentService {

    private final JdbcTemplate primaryJdbcTemplate;
    private final Optional<SecondaryAdminDataService> secondaryAdminDataService;

    public boolean hasSecondaryDatabase() {
        return secondaryAdminDataService.isPresent();
    }

    public void grantDefaultContentsToAllUsers(boolean secondary) {
        log.info("[DefaultContentService.grantDefaultContentsToAllUsers] START: secondary={}", secondary);
        if (secondary) {
            log.info("  -> Granting defaults in Secondary database");
            try {
                secondaryAdminDataService.orElseThrow(() -> new IllegalArgumentException("Secondary database is not configured"))
                        .grantDefaultContents();
                log.info("  -> Secondary database grant SUCCESSFUL");
            } catch (Exception e) {
                log.error("  -> Secondary database grant FAILED: {}", e.getMessage(), e);
                throw e;
            }
            return;
        }

        // 1. Grant default magics
        log.info("  -> 1. Granting default magics in Primary DB");
        String grantMagicsSql = """
            INSERT INTO user_magics(user_id, magic_id)
            SELECT u.id, m.id
            FROM users u, magics m
            WHERE m.access_type = 'DEFAULT' AND m.player_castable = true AND
                NOT EXISTS(
                    SELECT 1
                    FROM user_magics um
                    WHERE um.user_id = u.id AND um.magic_id = m.id
                )
            """;
        try {
            int magicsGranted = primaryJdbcTemplate.update(grantMagicsSql);
            log.info("     -> Magics grant query execution completed: rowsUpdated={}", magicsGranted);
        } catch (Exception e) {
            log.error("     -> Magics grant query FAILED: {}", e.getMessage(), e);
            throw e;
        }

        // 2. Grant free adventures (scenarios)
        log.info("  -> 2. Granting free adventures in Primary DB");
        String grantAdventuresSql = """
            INSERT INTO user_scenarios(user_id, scenario_id)
            SELECT u.id, s.id
            FROM users u, scenarios s
            JOIN stages st ON s.stage_id = st.id
            JOIN adventures a ON st.adventure_id = a.id
            WHERE a.access_type = 'FREE' AND
                NOT EXISTS(
                    SELECT 1
                    FROM user_scenarios us
                    WHERE us.user_id = u.id AND us.scenario_id = s.id
                )
            """;
        try {
            int adventuresGranted = primaryJdbcTemplate.update(grantAdventuresSql);
            log.info("     -> Adventures grant query execution completed: rowsUpdated={}", adventuresGranted);
        } catch (Exception e) {
            log.error("     -> Adventures grant query FAILED: {}", e.getMessage(), e);
            throw e;
        }
        log.info("[DefaultContentService.grantDefaultContentsToAllUsers] COMPLETE: Primary DB");
    }
}
