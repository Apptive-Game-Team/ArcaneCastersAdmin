package com.wordonline.admin.service;

import com.wordonline.admin.dto.llm.LlmKeyStatusDto;
import com.wordonline.admin.dto.llm.LlmProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@ConditionalOnBean(name = "secondaryJdbcTemplate")
@RequiredArgsConstructor
@Transactional(transactionManager = "secondaryTransactionManager")
public class SecondaryLlmKeyAdminService {

    @Qualifier("secondaryJdbcTemplate")
    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true, transactionManager = "secondaryTransactionManager")
    public List<LlmKeyStatusDto> findAll() {
        return jdbcTemplate.query("SELECT provider, key_hint, updated_at FROM llm_api_keys ORDER BY provider",
                (rs, rowNum) -> new LlmKeyStatusDto(rs.getString("provider"), rs.getString("key_hint"),
                        rs.getObject("updated_at", java.time.LocalDateTime.class)));
    }

    public void save(LlmProvider provider, String encryptedKey, String keyHint) {
        jdbcTemplate.update("""
                INSERT INTO llm_api_keys(provider, encrypted_key, key_hint, updated_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (provider) DO UPDATE SET encrypted_key=EXCLUDED.encrypted_key,
                    key_hint=EXCLUDED.key_hint, updated_at=CURRENT_TIMESTAMP
                """, provider.name(), encryptedKey, keyHint);
    }

    public void delete(LlmProvider provider) {
        jdbcTemplate.update("DELETE FROM llm_api_keys WHERE provider=?", provider.name());
    }
}
