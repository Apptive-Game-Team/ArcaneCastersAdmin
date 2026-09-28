package com.wordonline.admin.controller;

import com.wordonline.admin.config.WebSecurityConfig;
import com.wordonline.admin.dto.bot.BotAdminDto;
import com.wordonline.admin.dto.bot.BotComparisonDto;
import com.wordonline.admin.security.JwtAuthenticationFilter;
import com.wordonline.admin.service.BotDualDatabaseService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 봇 화면을 실제로 렌더한다. 여기서 조용히 유실되던 것이 접대 봇 설정이었으므로, 확인 대상은
 * 티어 드롭다운에 HOSPITALITY가 있는지와 hospitality 체크박스·음수 지수 입력이 실제 HTML에
 * 나오는지다.
 */
@WebMvcTest(controllers = BotAdminController.class)
@Import({WebSecurityConfig.class, JwtAuthenticationFilter.class})
class BotAdminRenderTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BotDualDatabaseService botService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private final BotAdminDto hospitalityBot = new BotAdminDto(-5L, "Warm Welcome", "HOSPITALITY",
            1200, 30, -1.0, true, true, "STOIC", (short) 600, "Online", 9L, "Warm Welcome", List.of());

    @Test
    @WithMockUser(authorities = "WORDONLINE_ADMIN")
    void listPageOffersTheHospitalityTierAndTheWidenedAggressionRange() throws Exception {
        when(botService.comparisons()).thenReturn(List.of(
                new BotComparisonDto(-5L, hospitalityBot, null)));
        when(botService.hasSecondary()).thenReturn(false);

        String html = render("/admin/bot");

        assertThat(html).contains("value=\"HOSPITALITY\"");
        assertThat(html).contains("name=\"hospitality\"");
        assertThat(html).contains("min=\"-1\" max=\"1\"");
        // 접대 봇은 enabled이므로 목록에서 일반 봇과 구분되는 표시가 따로 있어야 한다.
        assertThat(html).contains(">Hospitality<");
        // temperament는 emote 선택/빈도만 정한다는 문구와 함께 목록에도 값이 보여야 한다.
        assertThat(html).contains("Temperament: STOIC");
        assertThat(html).contains("실력과는 무관하다");
        // 이 페이지의 동기화 스크립트가 JS 템플릿 리터럴을 쓰므로 "${"는 정상적으로 남는다.
        assertThat(html).doesNotContain("th:text");
    }

    @Test
    @WithMockUser(authorities = "WORDONLINE_ADMIN")
    void detailPageKeepsTheHospitalityFlagAndTheNegativeAggressionOnTheForm() throws Exception {
        when(botService.comparison(-5L)).thenReturn(new BotComparisonDto(-5L, hospitalityBot, null));
        when(botService.primaryMagics()).thenReturn(List.of());
        when(botService.secondaryMagics()).thenReturn(List.of());
        when(botService.hasSecondary()).thenReturn(false);

        String html = render("/admin/bot/-5");

        // 현재 티어가 선택된 상태로 남아야 한다. 남지 않으면 저장 한 번에 다른 티어로 덮인다.
        assertThat(html).contains("value=\"HOSPITALITY\" selected=\"selected\"");
        assertThat(html).contains("name=\"hospitality\" value=\"true\" checked=\"checked\"");
        // 체크를 해제한 제출도 false로 바인딩되도록 빈 값 마커가 함께 나가야 한다.
        assertThat(html).contains("name=\"_hospitality\"");
        assertThat(html).contains("name=\"counterAggression\" type=\"number\" min=\"-1\" max=\"1\"");
        assertThat(html).contains("value=\"-1.0\"");
        // 현재 temperament가 선택된 상태로 남아야 저장 한 번에 다른 temperament로 덮이지 않는다.
        assertThat(html).contains("name=\"temperament\"");
        assertThat(html).contains("value=\"STOIC\" selected=\"selected\"");
        assertThat(html).doesNotContain("${", "th:text");
    }

    private String render(String path) throws Exception {
        return mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }
}
