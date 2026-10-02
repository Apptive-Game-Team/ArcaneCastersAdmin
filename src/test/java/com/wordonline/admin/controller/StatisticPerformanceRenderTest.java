package com.wordonline.admin.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.wordonline.admin.config.WebSecurityConfig;
import com.wordonline.admin.dto.statistic.GameFrameSummaryDto;
import com.wordonline.admin.dto.statistic.GameTimingDetailDto;
import com.wordonline.admin.dto.statistic.SystemTimingDto;
import com.wordonline.admin.dto.statistic.TimeSeriesPointDto;
import com.wordonline.admin.security.JwtAuthenticationFilter;
import com.wordonline.admin.service.StatisticPerformanceService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 템플릿을 실제로 렌더한다. 렌더 시점에 터지는 Thymeleaf 표현식도 컴파일은 되고 뷰 이름만 확인하는
 * 테스트는 통과하므로, 여기서는 만들어진 HTML을 직접 확인한다.
 * <p>
 * 권한 검사도 함께 본다. 통계 화면이 권한 없는 사용자에게 조용히 열리는 것이 가장 그럴듯한 실패
 * 모드다.
 */
@WebMvcTest(controllers = StatisticPerformanceController.class)
@Import({WebSecurityConfig.class, JwtAuthenticationFilter.class})
class StatisticPerformanceRenderTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StatisticPerformanceService service;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private final SystemTimingDto frame =
            new SystemTimingDto("Frame", 41_000_000L, 96_000_000L, 51_200_000.0, 63_400_000.0, 42,
                    50_000_000.0, 70_000_000.0, 20, 22);
    private final SystemTimingDto physics =
            new SystemTimingDto("PhysicSystem", 400_000L, 5_100_000L, 1_200_000.0, 2_400_000.0, 42,
                    null, null, 0, 0);

    /** 여러 이름을 합친 행이므로 단일 프레임 값인 min/max가 없다. */
    private final SystemTimingDto combined =
            new SystemTimingDto(SystemTimingDto.COMBINED_SYSTEMS_NAME, null, null,
                    3_100_000.0, 4_800_000.0, 42, 4_000_000.0, 6_000_000.0, 20, 22);

    private void stubPopulated() {
        when(service.findSystemTimings(any(), any(), any(), any())).thenReturn(List.of(frame, combined, physics));
        when(service.selectName(any(), any())).thenReturn("Frame");
        when(service.companionName(any(), any())).thenReturn(SystemTimingDto.COMBINED_SYSTEMS_NAME);
        when(service.frameTiming(any())).thenReturn(frame);
        when(service.findTimeSeries(any(), eq("Frame"), any(), any(), anyInt())).thenReturn(List.of(
                new TimeSeriesPointDto(LocalDateTime.of(2026, 8, 1, 10, 0), 47_000_000.0, 12L),
                new TimeSeriesPointDto(LocalDateTime.of(2026, 8, 2, 10, 0), 62_900_000.0, 9L)));
        // 짝 시계열은 구간이 하나 더 많다. 두 선의 x축이 다를 수 있다는 뜻이다.
        when(service.findTimeSeries(any(), eq(SystemTimingDto.COMBINED_SYSTEMS_NAME), any(), any(), anyInt()))
                .thenReturn(List.of(
                        new TimeSeriesPointDto(LocalDateTime.of(2026, 8, 1, 10, 0), 3_100_000.0, 12L),
                        new TimeSeriesPointDto(LocalDateTime.of(2026, 8, 2, 10, 0), 4_800_000.0, 9L),
                        new TimeSeriesPointDto(LocalDateTime.of(2026, 8, 3, 10, 0), 5_200_000.0, 4L)));
        when(service.findRecentGames(any(), any(), any(), anyInt(), anyInt())).thenReturn(List.of(
                new GameFrameSummaryDto(2L, LocalDateTime.of(2026, 8, 2, 10, 0), "PVP", 302L,
                        62_900_000.0, 96_000_000L),
                // 프레임 행이 없는 게임도 목록에 남아야 한다.
                new GameFrameSummaryDto(3L, LocalDateTime.of(2026, 8, 1, 10, 0), "Practice", 60L,
                        null, null)));
        when(service.countRecentGames(any(), any(), any())).thenReturn(45L);
        when(service.totalPages(anyLong(), anyInt())).thenReturn(3);
    }

    private String render(String path) throws Exception {
        return mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    @WithMockUser(authorities = "WORDONLINE_ADMIN")
    void performancePageRendersFiltersSummaryChartsAndTable() throws Exception {
        stubPopulated();

        String html = render("/admin/statistics/performance?gameType=PVP&days=7");

        // 레이아웃 데코레이터와 nav 프래그먼트가 실제로 적용됐는지.
        assertThat(html).contains("Arcane Casters Admin", "/admin/statistics/performance");
        // 51.2ms와 63.4ms는 예산 50ms를 넘으므로 빨갛게 표시된다.
        assertThat(html).contains("51.20 ms", "63.40 ms", "text-danger");
        // 느린 순 정렬이므로 Frame이 PhysicSystem보다 앞에 온다.
        assertThat(html.indexOf(">Frame<")).isLessThan(html.indexOf(">PhysicSystem<"));
        assertThat(html).contains("id=\"timingChart\"", "id=\"seriesChart\"");
        // 차트 데이터는 밀리초로 변환되어 페이지에 박혀 있다.
        assertThat(html).contains("\"median\":51.2", "\"p95\":63.4");
        // 최근 게임 표와 상세 링크, 그리고 프레임 데이터가 없는 게임의 표시.
        assertThat(html).contains("/admin/statistics/performance/games/2");
        assertThat(html).contains("Page 1 of 3");
        // 추세: Frame은 p95가 50ms → 70ms라 +40%로 악화, 빨갛게 표시되어야 한다.
        assertThat(html).contains("▲", "40%", "text-danger fw-semibold");
        assertThat(html).contains("전반부 p95 50.000ms (20게임) → 후반부 p95 70.000ms (22게임)");
        // PhysicSystem은 전·후반 표본이 없으므로 방향을 지어내지 않는다.
        assertThat(html).contains("표본 부족");
        // 합계도 한 행으로 나오고, 추세와 차트 데이터를 이름별 행과 똑같이 갖는다.
        assertThat(html).contains(SystemTimingDto.COMBINED_SYSTEMS_NAME);
        assertThat(html).contains("\"median\":3.1", "\"p95\":4.8");
        // Min/Max 자리는 비어 있어야 한다. 합이 실제로 관측된 프레임이 아니기 때문이다.
        assertThat(html).contains("<span class=\"text-muted\">–</span>");
        // 시계열 차트는 선택된 이름과 그 짝을 같은 plot에 그린다.
        assertThat(html).contains("id=\"companionSeriesData\"");
        assertThat(html).contains("data-primary-name=\"Frame\"");
        assertThat(html).contains("\"median\":3.1", "\"median\":5.2");
        // 렌더되지 않은 Thymeleaf 표현식이 출력에 남지 않았는지.
        assertThat(html).doesNotContain("${", "th:text");
    }

    @Test
    @WithMockUser(authorities = "WORDONLINE_ADMIN")
    void performancePageRendersWithNoDataAtAll() throws Exception {
        when(service.findSystemTimings(any(), any(), any(), any())).thenReturn(List.of());
        when(service.selectName(any(), any())).thenReturn(null);
        when(service.findRecentGames(any(), any(), any(), anyInt(), anyInt())).thenReturn(List.of());
        when(service.countRecentGames(any(), any(), any())).thenReturn(0L);
        when(service.totalPages(anyLong(), anyInt())).thenReturn(0);

        String html = render("/admin/statistics/performance");

        assertThat(html).contains("기록된 타이밍 통계가 없다.", "해당 기간에 게임이 없다.");
        assertThat(html).doesNotContain("${");
    }

    @Test
    @WithMockUser(authorities = "WORDONLINE_ADMIN")
    void gameDetailPageRendersEveryMeasuredName() throws Exception {
        when(service.findGame(any(), anyLong())).thenReturn(Optional.of(new GameFrameSummaryDto(
                2L, LocalDateTime.of(2026, 8, 2, 10, 0), "PVP", 302L, 62_900_000.0, 96_000_000L)));
        when(service.findGameTimings(any(), anyLong())).thenReturn(List.of(
                new GameTimingDetailDto("Frame", 41_000_000L, 96_000_000L, 62_900_000.0),
                new GameTimingDetailDto("PhysicSystem", 400_000L, 5_100_000L, 1_200_000.0)));

        String html = render("/admin/statistics/performance/games/2");

        assertThat(html).contains("Frame", "PhysicSystem", "62.90 ms", "302 s");
        assertThat(html).doesNotContain("${");
    }

    @Test
    @WithMockUser(authorities = "WORDONLINE_ADMIN")
    void gameDetailPageRendersAMessageForAnUnknownGame() throws Exception {
        when(service.findGame(any(), anyLong())).thenReturn(Optional.empty());

        String html = render("/admin/statistics/performance/games/404");

        assertThat(html).contains("Game not found");
        assertThat(html).doesNotContain("${");
    }

    @Test
    @WithMockUser(authorities = "PLAYER")
    void anOrdinaryUserCannotReachThePerformancePages() throws Exception {
        mockMvc.perform(get("/admin/statistics/performance")).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/admin/statistics/performance/games/1")).andExpect(status().is4xxClientError());
    }

    @Test
    @WithAnonymousUser
    void unauthenticatedCallersAreSentToLogin() throws Exception {
        mockMvc.perform(get("/admin/statistics/performance")).andExpect(status().is3xxRedirection());
    }
}
