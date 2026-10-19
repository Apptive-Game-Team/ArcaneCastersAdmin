package com.wordonline.admin.service;

import com.wordonline.admin.config.LlmKeyCipher;
import com.wordonline.admin.dto.llm.LlmProvider;
import com.wordonline.admin.service.BotDualDatabaseService.Target;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LlmKeyServiceTest {

    @Mock
    private LlmKeyAdminService primary;
    @Mock
    private SecondaryLlmKeyAdminService secondary;
    @Mock
    private ObjectProvider<SecondaryLlmKeyAdminService> secondaryProvider;
    @Mock
    private LlmKeyCipher cipher;
    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void clearUnavailableWorksWithoutTheCipherSecret() {
        when(secondaryProvider.getIfAvailable()).thenReturn(secondary);
        new LlmKeyDualDatabaseService(primary, secondaryProvider, cipher)
                .clearUnavailable(LlmProvider.JEV, Target.BOTH);

        verify(primary).clearUnavailable(LlmProvider.JEV);
        verify(secondary).clearUnavailable(LlmProvider.JEV);
        verify(cipher, never()).isConfigured();
    }

    @Test
    void clearUnavailableOnDevFailsWhenDevDatabaseIsMissing() {
        when(secondaryProvider.getIfAvailable()).thenReturn(null);
        LlmKeyDualDatabaseService service = new LlmKeyDualDatabaseService(primary, secondaryProvider, cipher);

        assertThatThrownBy(() -> service.clearUnavailable(LlmProvider.JEV, Target.SECONDARY))
                .isInstanceOf(IllegalStateException.class);
        verify(primary, never()).clearUnavailable(any());
    }

    @Test
    void secondaryUpsertClearsTheUnavailableColumns() {
        new SecondaryLlmKeyAdminService(jdbcTemplate).save(LlmProvider.JEV, "cipher", "…1234");
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sql.capture(), eq("JEV"), eq("cipher"), eq("…1234"));
        assertThat(sql.getValue()).contains("unavailable_until=NULL").contains("unavailable_reason=NULL");
    }

    @Test
    void secondaryClearUnavailableAndFindAll() {
        SecondaryLlmKeyAdminService service = new SecondaryLlmKeyAdminService(jdbcTemplate);
        service.clearUnavailable(LlmProvider.JEV);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sql.capture(), eq("JEV"));
        assertThat(sql.getValue()).contains("unavailable_until=NULL").doesNotContain("encrypted_key");

        service.findAll();
        ArgumentCaptor<String> query = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(query.capture(), any(RowMapper.class));
        assertThat(query.getValue()).contains("unavailable_until > CURRENT_TIMESTAMP");
    }
}
