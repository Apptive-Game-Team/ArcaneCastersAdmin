package com.wordonline.admin.repository;

import com.wordonline.admin.repository.llm.LlmKeyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LlmKeyRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void upsertClearsTheUnavailableColumns() {
        new LlmKeyRepository(jdbcTemplate).upsert("JEV", "cipher", "…1234");
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sql.capture(), eq("JEV"), eq("cipher"), eq("…1234"));
        assertThat(sql.getValue()).contains("unavailable_until=NULL").contains("unavailable_reason=NULL");
    }

    @Test
    void clearUnavailableLeavesTheKeyAlone() {
        new LlmKeyRepository(jdbcTemplate).clearUnavailable("JEV");
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sql.capture(), eq("JEV"));
        assertThat(sql.getValue()).contains("unavailable_until=NULL", "unavailable_reason=NULL")
                .doesNotContain("encrypted_key");
    }

    @Test
    void findAllComputesUnavailableInSql() {
        new LlmKeyRepository(jdbcTemplate).findAll();
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sql.capture(), any(org.springframework.jdbc.core.RowMapper.class));
        assertThat(sql.getValue()).contains("unavailable_until > CURRENT_TIMESTAMP").contains("unavailable_reason")
                .doesNotContain("encrypted_key");
    }
}
