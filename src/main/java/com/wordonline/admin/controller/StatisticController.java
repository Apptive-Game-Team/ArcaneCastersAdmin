package com.wordonline.admin.controller;

import com.wordonline.admin.entity.magic.Magic;
import com.wordonline.admin.entity.statistic.GameType;
import com.wordonline.admin.service.StatisticService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/statistics")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('WORDONLINE_ADMIN')")
public class StatisticController {

    private final StatisticService statisticService;

    @GetMapping
    public String getStatistics(
            @RequestParam(required = false) String gameType,
            @RequestParam(required = false) Integer days,
            Model model) {

        GameType type = parseGameType(gameType);

        // Default to 7 days if not specified, with validation
        int daysFilter = (days != null && days > 0) ? days : 7;
        // Cap at 365 days to prevent performance issues
        if (daysFilter > 365) {
            daysFilter = 365;
        }
        LocalDateTime fromDate = LocalDateTime.now().minusDays(daysFilter);

        String gameTypeForUrl = (gameType != null && !"ALL".equalsIgnoreCase(gameType)) ? gameType : null;

        model.addAttribute("selectedGameType", gameType != null ? gameType : "ALL");
        model.addAttribute("gameTypeForUrl", gameTypeForUrl);
        model.addAttribute("selectedDays", daysFilter);
        model.addAttribute("deckWinCounts", toNameMap(statisticService.calculateDeckWinCounts(type, fromDate)));
        model.addAttribute("magicWinCounts", toNameMap(statisticService.calculateMagicWinCounts(type, fromDate)));
        model.addAttribute("deckGameCounts", toNameMap(statisticService.calculateDeckGameCounts(type, fromDate)));
        model.addAttribute("magicGameCounts", toNameMap(statisticService.calculateMagicGameCounts(type, fromDate)));
        model.addAttribute("deckUseCounts", toNameMap(statisticService.calculateDeckUseCounts(type, fromDate)));
        model.addAttribute("magicUseCounts", toNameMap(statisticService.calculateMagicUseCounts(type, fromDate)));

        // Per-player statistics
        model.addAttribute("playerWinCounts", statisticService.calculatePlayerWinCounts(type, fromDate));
        model.addAttribute("playerDeckUsage", convertPlayerUsage(statisticService.calculatePlayerDeckUsage(type, fromDate)));
        model.addAttribute("playerMagicUsage", convertPlayerUsage(statisticService.calculatePlayerMagicUsage(type, fromDate)));

        return "admin-statistics";
    }

    private GameType parseGameType(String gameType) {
        if (gameType == null || "ALL".equalsIgnoreCase(gameType)) {
            return null;
        }
        try {
            return GameType.valueOf(gameType.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // Deck win/game/use counts and magic cast win/game/use counts both key by Magic,
    // so both the "brought a deck containing this magic" track and the "cast this magic"
    // track share this conversion to a name-keyed map for the template.
    private Map<String, Integer> toNameMap(Map<Magic, Integer> map) {
        return map.entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getKey().getName(), Map.Entry::getValue));
    }

    private Map<Long, Map<String, Integer>> convertPlayerUsage(Map<Long, Map<Magic, Integer>> playerUsage) {
        return playerUsage.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().entrySet().stream()
                                .collect(Collectors.toMap(
                                        magicEntry -> magicEntry.getKey().getName(),
                                        Map.Entry::getValue
                                ))
                ));
    }
}
