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
        return jdbcTemplate.query(LlmKeyStatusDto.SELECT, (rs, rowNum) -> LlmKeyStatusDto.from(rs));
    }

    public void save(LlmProvider provider, String encryptedKey, String keyHint) {
        jdbcTemplate.update("""
                INSERT INTO llm_api_keys(provider, encrypted_key, key_hint, updated_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (provider) DO UPDATE SET encrypted_key=EXCLUDED.encrypted_key,
                    key_hint=EXCLUDED.key_hint, updated_at=CURRENT_TIMESTAMP,
                    unavailable_until=NULL, unavailable_reason=NULL
                """, provider.name(), encryptedKey, keyHint);
    }

    public void clearUnavailable(LlmProvider provider) {
        jdbcTemplate.update("UPDATE llm_api_keys SET unavailable_until=NULL, unavailable_reason=NULL WHERE provider=?",
                provider.name());
    }

    public void delete(LlmProvider provider) {
        jdbcTemplate.update("DELETE FROM llm_api_keys WHERE provider=?", provider.name());
    }
}
