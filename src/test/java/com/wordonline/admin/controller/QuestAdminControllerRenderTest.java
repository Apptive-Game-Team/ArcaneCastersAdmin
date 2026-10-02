package com.wordonline.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.wordonline.admin.config.WebSecurityConfig;
import com.wordonline.admin.dto.quest.QuestDto;
import com.wordonline.admin.dto.quest.QuestRewardDto;
import com.wordonline.admin.security.JwtAuthenticationFilter;
import com.wordonline.admin.service.QuestService;

/** Renders the real template, since Thymeleaf expression errors only show up in the produced HTML. */
@WebMvcTest(controllers = QuestAdminController.class)
@Import({WebSecurityConfig.class, JwtAuthenticationFilter.class})
class QuestAdminControllerRenderTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QuestService questService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @WithMockUser(authorities = "WORDONLINE_ADMIN")
    void pageRendersConditionAndRewardsWithoutLegacyFields() throws Exception {
        when(questService.hasSecondaryDatabase()).thenReturn(true);
        when(questService.findAllQuests(false)).thenReturn(List.of(
                new QuestDto(1L, "STAGE_CLEAR", 12L, 1, List.of(
                        new QuestRewardDto(5L, "MAGIC", 7L, 1),
                        new QuestRewardDto(6L, "DECORATION", null, 3))),
                new QuestDto(2L, "TOTAL_WIN", null, 10, List.of())));

        String html = mockMvc.perform(get("/admin/quest"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("STAGE_CLEAR", "TOTAL_WIN", "MAGIC #7 x1", "DECORATION x3");
        assertThat(html).contains("data-rewards=\"MAGIC|7|1,DECORATION||3\"");
        assertThat(html).contains("list=\"conditionTypeOptions\"", "id=\"rewardTypeOptions\"",
                "createRewardsList", "editRewardsList", "createConditionTargetId");
        assertThat(html).doesNotContain("Progress Checker", "Reward Giver", "rewardParams");
    }
}
