package com.wordonline.admin.controller;

import com.wordonline.admin.config.WebSecurityConfig;
import com.wordonline.admin.dto.llm.LlmKeyComparisonDto;
import com.wordonline.admin.dto.llm.LlmKeyStatusDto;
import com.wordonline.admin.dto.llm.LlmProvider;
import com.wordonline.admin.security.JwtAuthenticationFilter;
import com.wordonline.admin.service.BotDualDatabaseService.Target;
import com.wordonline.admin.service.LlmKeyDualDatabaseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = LlmKeyAdminController.class)
@Import({WebSecurityConfig.class, JwtAuthenticationFilter.class})
class LlmKeyAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LlmKeyDualDatabaseService keyService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @WithMockUser(authorities = "WORDONLINE_ADMIN")
    void pageShowsTheHintAndNeverTheKey() throws Exception {
        when(keyService.comparisons()).thenReturn(List.of(
                new LlmKeyComparisonDto(LlmProvider.JEV,
                        new LlmKeyStatusDto("JEV", "…1234", LocalDateTime.of(2026, 10, 15, 9, 30)), null),
                new LlmKeyComparisonDto(LlmProvider.OPENROUTER, null, null)));
        when(keyService.hasSecondary()).thenReturn(false);
        when(keyService.cipherConfigured()).thenReturn(true);

        String html = mockMvc.perform(get("/admin/llm-keys")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("…1234").contains("2026-10-15 09:30");
        assertThat(html).contains("type=\"password\"").contains("LLM_KEY_SECRET");
        assertThat(html).doesNotContain("th:text").doesNotContain("encrypted");
    }

    @Test
    @WithMockUser(authorities = "WORDONLINE_ADMIN")
    void pageWarnsAndDisablesSavingWhenTheSecretIsMissing() throws Exception {
        when(keyService.comparisons()).thenReturn(List.of(new LlmKeyComparisonDto(LlmProvider.JEV, null, null)));
        when(keyService.cipherConfigured()).thenReturn(false);

        String html = mockMvc.perform(get("/admin/llm-keys")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("key 를 저장할 수 없다");
        assertThat(html).contains("name=\"apiKey\"").contains("disabled=\"disabled\"");
    }

    @Test
    @WithMockUser(authorities = "WORDONLINE_ADMIN")
    void saveRedirectsWithAFlashMessage() throws Exception {
        mockMvc.perform(post("/admin/llm-keys/jev").param("apiKey", "sk-test-1234").param("db", "both").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/llm-keys"))
                .andExpect(flash().attribute("message", "JEV API key saved"));

        verify(keyService).save(LlmProvider.JEV, "sk-test-1234", Target.BOTH);
    }

    @Test
    @WithMockUser(authorities = "WORDONLINE_ADMIN")
    void rejectedSaveFlashesTheErrorWithoutTheKey() throws Exception {
        org.mockito.Mockito.doThrow(new IllegalArgumentException("API key is required"))
                .when(keyService).save(any(), any(), any());

        mockMvc.perform(post("/admin/llm-keys/OPENROUTER").param("apiKey", "   ").with(csrf()))
                .andExpect(redirectedUrl("/admin/llm-keys"))
                .andExpect(flash().attribute("error", "API key is required"));
    }

    @Test
    @WithMockUser(authorities = "WORDONLINE_ADMIN")
    void unknownProviderIsRejected() throws Exception {
        mockMvc.perform(post("/admin/llm-keys/gpt").param("apiKey", "x").with(csrf()))
                .andExpect(flash().attribute("error", "Unsupported LLM provider"));
        verify(keyService, never()).save(any(), any(), any());
    }

    @Test
    @WithMockUser(authorities = "WORDONLINE_ADMIN")
    void deleteCallsTheServiceAndRedirects() throws Exception {
        mockMvc.perform(post("/admin/llm-keys/JEV/delete").with(csrf()))
                .andExpect(redirectedUrl("/admin/llm-keys"))
                .andExpect(flash().attribute("message", "JEV API key deleted"));
        verify(keyService).delete(LlmProvider.JEV, Target.PRIMARY);
    }

    @Test
    @WithMockUser(authorities = "SOMEONE_ELSE")
    void nonAdminsAreForbidden() throws Exception {
        mockMvc.perform(get("/admin/llm-keys")).andExpect(status().isForbidden());
    }
}
