package com.wordonline.admin.dto.llm;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

/**
 * 저장된 key 의 상태만 담는다. 평문과 암호문은 이 객체로 나가지 않는다.
 * unavailable 은 SQL 에서 unavailable_until > CURRENT_TIMESTAMP 로 계산한 값이다.
 */
public record LlmKeyStatusDto(String provider, String keyHint, LocalDateTime updatedAt,
                              LocalDateTime unavailableUntil, String unavailableReason, boolean unavailable) {

    public static final String SELECT = """
            SELECT provider, key_hint, updated_at, unavailable_until, unavailable_reason,
                   COALESCE(unavailable_until > CURRENT_TIMESTAMP, FALSE) AS unavailable
            FROM llm_api_keys ORDER BY provider
            """;

    public static LlmKeyStatusDto from(ResultSet rs) throws SQLException {
        return new LlmKeyStatusDto(rs.getString("provider"), rs.getString("key_hint"),
                rs.getObject("updated_at", LocalDateTime.class),
                rs.getObject("unavailable_until", LocalDateTime.class),
                rs.getString("unavailable_reason"), rs.getBoolean("unavailable"));
    }
}
