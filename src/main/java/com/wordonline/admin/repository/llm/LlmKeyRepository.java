package com.wordonline.admin.repository.llm;

import com.wordonline.admin.dto.llm.LlmKeyStatusDto;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

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
        return jdbcTemplate.query(LlmKeyStatusDto.SELECT, (rs, rowNum) -> LlmKeyStatusDto.from(rs));
    }

    public void upsert(String provider, String encryptedKey, String keyHint) {
        jdbcTemplate.update("""
                INSERT INTO llm_api_keys(provider, encrypted_key, key_hint, updated_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (provider) DO UPDATE SET encrypted_key=EXCLUDED.encrypted_key,
                    key_hint=EXCLUDED.key_hint, updated_at=CURRENT_TIMESTAMP,
                    unavailable_until=NULL, unavailable_reason=NULL
                """, provider, encryptedKey, keyHint);
    }

    public void clearUnavailable(String provider) {
        jdbcTemplate.update("UPDATE llm_api_keys SET unavailable_until=NULL, unavailable_reason=NULL WHERE provider=?",
                provider);
    }

    public void delete(String provider) {
        jdbcTemplate.update("DELETE FROM llm_api_keys WHERE provider=?", provider);
    }
}
