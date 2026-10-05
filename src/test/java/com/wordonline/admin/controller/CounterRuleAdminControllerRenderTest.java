package com.wordonline.admin.controller;

import com.wordonline.admin.config.WebSecurityConfig;
import com.wordonline.admin.dto.counter.CounterRuleComparisonDto;
import com.wordonline.admin.dto.counter.CounterRuleDto;
import com.wordonline.admin.dto.counter.CounterRuleForm;
import com.wordonline.admin.dto.counter.MagicTagComparisonDto;
import com.wordonline.admin.dto.counter.MagicTagDto;
import com.wordonline.admin.security.JwtAuthenticationFilter;
import com.wordonline.admin.service.CounterRuleDualDatabaseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 템플릿을 실제로 렌더한다. Thymeleaf 표현식 오류는 뷰 이름만 확인하는 테스트를 그대로 통과하므로
 * 만들어진 HTML을 직접 본다. 태그가 없는 마법 목록은 이 화면의 존재 이유라 특히 확인한다.
 */
@WebMvcTest(controllers = CounterRuleAdminController.class)
@Import({WebSecurityConfig.class, JwtAuthenticationFilter.class})
class CounterRuleAdminControllerRenderTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CounterRuleDualDatabaseService counterRuleService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @WithMockUser(authorities = "WORDONLINE_ADMIN")
    void pageRendersRulesMagicTagsAndTheUntaggedMagicList() throws Exception {
        when(counterRuleService.hasSecondary()).thenReturn(true);
        when(counterRuleService.tagNames()).thenReturn(List.of("CAT_AoE", "CAT_Small"));
        when(counterRuleService.comparisons()).thenReturn(List.of(new CounterRuleComparisonDto(
                "CAT_AoE", "CAT_Small",
                new CounterRuleDto(1L, "CAT_AoE", "CAT_Small", 2.0),
                new CounterRuleDto(77L, "CAT_AoE", "CAT_Small", 1.0))));
        when(counterRuleService.magicTagComparisons()).thenReturn(List.of(
                new MagicTagComparisonDto("bubble_spirit",
                        new MagicTagDto(1, "bubble_spirit", List.of("CAT_Small", "TYPE_Unit")), null),
                new MagicTagComparisonDto("rallying_torch",
                        new MagicTagDto(2, "rallying_torch", List.of()), null)));
        when(counterRuleService.untaggedPrimaryMagics())
                .thenReturn(List.of(new MagicTagDto(2, "rallying_torch", List.of())));
        when(counterRuleService.untaggedSecondaryMagics()).thenReturn(List.of());

        String html = mockMvc.perform(get("/admin/counter-rule"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Arcane Casters Admin", "/admin/counter-rule");
        assertThat(html).contains("CAT_AoE", "CAT_Small", "table-warning");
        assertThat(html).contains("bubble_spirit", "CAT_Small, TYPE_Unit");
        // 이 화면의 핵심. 태그가 없는 마법은 어떤 봇의 상성 판단에도 들어가지 않는다.
        assertThat(html).contains("rallying_torch", "태그가 하나도 없는 마법");
        assertThat(html).contains("sync_magic_tags_from_game_objects()");
        // 태그를 직접 달고 뗄 수 있어야 도출이 닿지 않는 마법을 고칠 수 있다.
        assertThat(html).contains("/admin/counter-rule/magic-tag", "/admin/counter-rule/magic-tag/delete");
        // 손으로 뗀 도출 태그가 재동기화에서 돌아온다는 사실을 화면이 말하지 않으면 화면이 고장 난 것처럼 보인다.
        assertThat(html).contains("손으로 뗀 태그는 재동기화에서 돌아온다");
        // 수정이 캐시 무효화 전까지 반영되지 않는다는 사실과, 그 버튼으로 가는 링크는 화면에 남아 있어야 한다.
        assertThat(html).contains("캐시를 비워야 반영된다", "/admin/invalidate-cache");
        // 페이지 스크립트가 자바스크립트 템플릿 리터럴을 쓰므로 마크업 부분만 검사한다.
        assertThat(markup(html)).doesNotContain("${", "th:text");
    }

    @Test
    @WithMockUser(authorities = "WORDONLINE_ADMIN")
    void emptyPageExplainsThatTheTablesMayNotExistYet() throws Exception {
        String html = mockMvc.perform(get("/admin/counter-rule"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("database #63");
        assertThat(markup(html)).doesNotContain("${", "th:text");
    }

    @Test
    @WithMockUser(authorities = "WORDONLINE_ADMIN")
    void duplicatePairComesBackAsAFlashMessageInsteadOfAnErrorPage() throws Exception {
        when(counterRuleService.target("primary")).thenReturn(CounterRuleDualDatabaseService.Target.PRIMARY);
        doThrow(new IllegalArgumentException("Counter rule already exists for CAT_AoE -> CAT_Small"))
                .when(counterRuleService).create(any(CounterRuleForm.class), any());

        mockMvc.perform(post("/admin/counter-rule")
                        .param("attackerTagName", "CAT_AoE")
                        .param("targetTagName", "CAT_Small")
                        .param("weight", "2.0")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/counter-rule"))
                .andExpect(flash().attribute("error", "Counter rule already exists for CAT_AoE -> CAT_Small"));
    }

    @Test
    @WithMockUser(authorities = "WORDONLINE_ADMIN")
    void attachingATagTheMagicAlreadyHasComesBackAsAFlashMessageInsteadOfAnErrorPage() throws Exception {
        when(counterRuleService.target("both")).thenReturn(CounterRuleDualDatabaseService.Target.BOTH);
        doThrow(new IllegalArgumentException("Magic rallying_torch already has tag CAT_Buff"))
                .when(counterRuleService).attachTag("rallying_torch", "CAT_Buff",
                        CounterRuleDualDatabaseService.Target.BOTH);

        mockMvc.perform(post("/admin/counter-rule/magic-tag")
                        .param("magicName", "rallying_torch")
                        .param("tagName", "CAT_Buff")
                        .param("db", "both")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/counter-rule"))
                .andExpect(flash().attribute("error", "Magic rallying_torch already has tag CAT_Buff"));
    }

    @Test
    @WithMockUser(authorities = "WORDONLINE_ADMIN")
    void detachingATagRoutesToTheChosenDatabaseAndWarnsThatAResyncRestoresIt() throws Exception {
        when(counterRuleService.target("secondary")).thenReturn(CounterRuleDualDatabaseService.Target.SECONDARY);

        mockMvc.perform(post("/admin/counter-rule/magic-tag/delete")
                        .param("magicName", "rallying_torch")
                        .param("tagName", "CAT_Buff")
                        .param("db", "secondary")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("message",
                        "Tag CAT_Buff detached from rallying_torch; a derived tag returns on the next resync"));

        verify(counterRuleService).detachTag("rallying_torch", "CAT_Buff",
                CounterRuleDualDatabaseService.Target.SECONDARY);
    }

    /** 렌더되지 않은 Thymeleaf 표현식을 찾기 위해 페이지 스크립트 앞까지만 본다. */
    private String markup(String html) {
        return html.substring(0, html.indexOf("<script>"));
    }

    @Test
    @WithMockUser(authorities = "PLAYER")
    void anOrdinaryUserCannotReachTheCounterRulePage() throws Exception {
        mockMvc.perform(get("/admin/counter-rule")).andExpect(status().is4xxClientError());
    }
}
