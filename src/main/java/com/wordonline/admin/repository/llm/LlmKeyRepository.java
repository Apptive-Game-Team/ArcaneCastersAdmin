package com.wordonline.admin.repository.llm;

import com.wordonline.admin.dto.llm.LlmKeyStatusDto;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

/**
 * JdbcTemplate 을 쓴다. EntityManager 네이티브 쿼리는 org.hibernate.orm.jdbc.bind TRACE 설정 때문에
 * 바인딩 값(암호문)이 log 에 남는다. encrypted_key 는 읽지 않는다.
 */
@Repository
@RequiredArgsConstructor
public class LlmKeyRepository {

    private final JdbcTemplate jdbcTemplate;

    public List<LlmKeyStatusDto> findAll() {
        return jdbcTemplate.query("SELECT provider, key_hint, updated_at FROM llm_api_keys ORDER BY provider",
                (rs, rowNum) -> new LlmKeyStatusDto(rs.getString("provider"), rs.getString("key_hint"),
                        rs.getObject("updated_at", java.time.LocalDateTime.class)));
    }

    public void upsert(String provider, String encryptedKey, String keyHint) {
        jdbcTemplate.update("""
                INSERT INTO llm_api_keys(provider, encrypted_key, key_hint, updated_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (provider) DO UPDATE SET encrypted_key=EXCLUDED.encrypted_key,
                    key_hint=EXCLUDED.key_hint, updated_at=CURRENT_TIMESTAMP
                """, provider, encryptedKey, keyHint);
    }

    public void delete(String provider) {
        jdbcTemplate.update("DELETE FROM llm_api_keys WHERE provider=?", provider);
    }
}
