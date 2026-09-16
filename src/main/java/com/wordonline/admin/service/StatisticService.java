package com.wordonline.admin.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.wordonline.admin.entity.magic.Magic;
import com.wordonline.admin.entity.statistic.GameOutcome;
import com.wordonline.admin.entity.statistic.GameType;
import com.wordonline.admin.entity.statistic.StatisticGame;
import com.wordonline.admin.entity.statistic.StatisticGameDeck;
import com.wordonline.admin.entity.statistic.StatisticGameMagic;
import com.wordonline.admin.repository.statistic.StatisticGameRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StatisticService {

    private final StatisticGameRepository statisticGameRepository;

    // Deck statistics: which deck (a set of magics) a player brought to a match.
    // This is a distinct measurement from magic cast counts below.

    public Map<Magic, Integer> calculateDeckWinCounts() {
        return calculateDeckWinCounts(null, null);
    }

    public Map<Magic, Integer> calculateDeckWinCounts(GameType gameType) {
        return calculateDeckWinCounts(gameType, null);
    }

    public Map<Magic, Integer> calculateDeckWinCounts(GameType gameType, LocalDateTime fromDate) {
        List<StatisticGame> statisticGames = getStatisticGames(gameType, fromDate);

        Map<Magic, Integer> deckCounts = new HashMap<>();

        statisticGames
                .forEach(statisticGame -> {
                    statisticGame.getStatisticGameDecks()
                            .stream()
                            .filter(statisticGameDeck ->
                                    Objects.equals(
                                            statisticGameDeck.getUserId(),
                                            statisticGame.getWinUserId()
                                    ))
                            .map(StatisticGameDeck::getMagic)
                            .forEach(magic -> {
                                int count = deckCounts.getOrDefault(magic, 0);
                                deckCounts.put(magic, count + 1);
                            });
                });
        return deckCounts;
    }

    // Magic statistics: how many times each magic was cast.

    public Map<Magic, Integer> calculateMagicWinCounts() {
        return calculateMagicWinCounts(null, null);
    }

    public Map<Magic, Integer> calculateMagicWinCounts(GameType gameType) {
        return calculateMagicWinCounts(gameType, null);
    }

    public Map<Magic, Integer> calculateMagicWinCounts(GameType gameType, LocalDateTime fromDate) {
        List<StatisticGame> statisticGames = getStatisticGames(gameType, fromDate);

        Map<Magic, Integer> magicCounts = new HashMap<>();

        statisticGames
                .forEach(statisticGame -> {
                    statisticGame.getStatisticGameMagics()
                            .stream()
                            .filter(statisticGameMagic ->
                                    Objects.equals(
                                            statisticGameMagic.getUserId(),
                                            statisticGame.getWinUserId()
                                    ))
                            .map(StatisticGameMagic::getMagic)
                            .forEach(magic -> {
                                int count = magicCounts.getOrDefault(magic, 0);
                                magicCounts.put(magic, count + 1);
                            });
                });
        return magicCounts;
    }

    public Map<Magic, Integer> calculateDeckGameCounts() {
        return calculateDeckGameCounts(null, null);
    }

    public Map<Magic, Integer> calculateDeckGameCounts(GameType gameType) {
        return calculateDeckGameCounts(gameType, null);
    }

    public Map<Magic, Integer> calculateDeckGameCounts(GameType gameType, LocalDateTime fromDate) {
        List<StatisticGame> statisticGames = getStatisticGames(gameType, fromDate);

        Map<Magic, Integer> deckCounts = new HashMap<>();

        statisticGames
                .forEach(statisticGame -> {
                    statisticGame.getStatisticGameDecks()
                            .stream()
                            .filter(statisticGameDeck ->
                                    Objects.equals(
                                            statisticGameDeck.getUserId(),
                                            statisticGame.getWinUserId()
                                    ))
                            .map(StatisticGameDeck::getMagic)
                            .forEach(magic -> {
                                int count = deckCounts.getOrDefault(magic, 0);
                                deckCounts.put(magic, count + 1);
                            });
                });
        return deckCounts;
    }

    public Map<Magic, Integer> calculateMagicGameCounts() {
        return calculateMagicGameCounts(null, null);
    }

    public Map<Magic, Integer> calculateMagicGameCounts(GameType gameType) {
        return calculateMagicGameCounts(gameType, null);
    }

    public Map<Magic, Integer> calculateMagicGameCounts(GameType gameType, LocalDateTime fromDate) {
        List<StatisticGame> statisticGames = getStatisticGames(gameType, fromDate);

        Map<Magic, Integer> magicCounts = new HashMap<>();

        statisticGames
                .forEach(statisticGame -> {
                    statisticGame.getStatisticGameMagics()
                            .stream()
                            .map(StatisticGameMagic::getMagic)
                            .forEach(magic -> {
                                int count = magicCounts.getOrDefault(magic, 0);
                                magicCounts.put(magic, count + 1);
                            });
                });
        return magicCounts;
    }

    public Map<Magic, Integer> calculateDeckUseCounts() {
        return calculateDeckUseCounts(null, null);
    }

    public Map<Magic, Integer> calculateDeckUseCounts(GameType gameType) {
        return calculateDeckUseCounts(gameType, null);
    }

    public Map<Magic, Integer> calculateDeckUseCounts(GameType gameType, LocalDateTime fromDate) {
        List<StatisticGame> statisticGames = getStatisticGames(gameType, fromDate);

        Map<Magic, Integer> deckCounts = new HashMap<>();

        statisticGames
                .forEach(statisticGame -> {
                    statisticGame.getStatisticGameDecks()
                            .forEach(statisticGameDeck -> {
                                Magic magic = statisticGameDeck.getMagic();
                                int count = deckCounts.getOrDefault(magic, 0);
                                deckCounts.put(magic, count + statisticGameDeck.getCount());
                            });
                });
        return deckCounts;
    }

    public Map<Magic, Integer> calculateMagicUseCounts() {
        return calculateMagicUseCounts(null, null);
    }

    public Map<Magic, Integer> calculateMagicUseCounts(GameType gameType) {
        return calculateMagicUseCounts(gameType, null);
    }

    public Map<Magic, Integer> calculateMagicUseCounts(GameType gameType, LocalDateTime fromDate) {
        List<StatisticGame> statisticGames = getStatisticGames(gameType, fromDate);

        Map<Magic, Integer> magicCounts = new HashMap<>();

        statisticGames
                .forEach(statisticGame -> {
                    statisticGame.getStatisticGameMagics()
                            .forEach(statisticGameMagic -> {
                                Magic magic = statisticGameMagic.getMagic();
                                int count = magicCounts.getOrDefault(magic, 0);
                                magicCounts.put(magic, count + statisticGameMagic.getCount());
                            });
                });
        return magicCounts;
    }

    // Per-player statistics

    /**
     * Calculates the number of wins per player.
     *
     * @return Map where keys are player user IDs (Long) and values are win counts (Integer)
     */
    public Map<Long, Integer> calculatePlayerWinCounts() {
        return calculatePlayerWinCounts(null, null);
    }

    /**
     * Calculates the number of wins per player, filtered by game type.
     *
     * @param gameType The type of game to filter by (PVP, Practice), or null for all games
     * @return Map where keys are player user IDs (Long) and values are win counts (Integer)
     */
    public Map<Long, Integer> calculatePlayerWinCounts(GameType gameType) {
        return calculatePlayerWinCounts(gameType, null);
    }

    /**
     * Calculates the number of wins per player, filtered by game type and date.
     *
     * @param gameType The type of game to filter by (PVP, Practice), or null for all games
     * @param fromDate The date to filter from (inclusive), or null for all dates
     * @return Map where keys are player user IDs (Long) and values are win counts (Integer)
     */
    public Map<Long, Integer> calculatePlayerWinCounts(GameType gameType, LocalDateTime fromDate) {
        List<StatisticGame> statisticGames = getStatisticGames(gameType, fromDate);

        Map<Long, Integer> winCounts = new HashMap<>();

        statisticGames.forEach(statisticGame -> {
            Long winUserId = statisticGame.getWinUserId();
            if (winUserId != null) {
                winCounts.put(winUserId, winCounts.getOrDefault(winUserId, 0) + 1);
            }
        });

        return winCounts;
    }

    /**
     * Calculates deck usage statistics per player: which magics each player brought to their matches.
     *
     * @return Map where keys are player user IDs (Long) and values are Maps of Magic to deck-slot count (Integer)
     */
    public Map<Long, Map<Magic, Integer>> calculatePlayerDeckUsage() {
        return calculatePlayerDeckUsage(null, null);
    }

    /**
     * Calculates deck usage statistics per player, filtered by game type.
     *
     * @param gameType The type of game to filter by (PVP, Practice), or null for all games
     * @return Map where keys are player user IDs (Long) and values are Maps of Magic to deck-slot count (Integer)
     */
    public Map<Long, Map<Magic, Integer>> calculatePlayerDeckUsage(GameType gameType) {
        return calculatePlayerDeckUsage(gameType, null);
    }

    /**
     * Calculates deck usage statistics per player, filtered by game type and date.
     *
     * @param gameType The type of game to filter by (PVP, Practice), or null for all games
     * @param fromDate The date to filter from (inclusive), or null for all dates
     * @return Map where keys are player user IDs (Long) and values are Maps of Magic to deck-slot count (Integer)
     */
    public Map<Long, Map<Magic, Integer>> calculatePlayerDeckUsage(GameType gameType, LocalDateTime fromDate) {
        List<StatisticGame> statisticGames = getStatisticGames(gameType, fromDate);

        Map<Long, Map<Magic, Integer>> playerDeckUsage = new HashMap<>();

        statisticGames.forEach(statisticGame -> {
            statisticGame.getStatisticGameDecks().forEach(statisticGameDeck -> {
                Long userId = statisticGameDeck.getUserId();
                Magic magic = statisticGameDeck.getMagic();

                playerDeckUsage.computeIfAbsent(userId, k -> new HashMap<>());
                Map<Magic, Integer> deckUsage = playerDeckUsage.get(userId);
                deckUsage.put(magic, deckUsage.getOrDefault(magic, 0) + statisticGameDeck.getCount());
            });
        });

        return playerDeckUsage;
    }

    /**
     * Calculates magic usage statistics per player: how many times each player cast each magic.
     *
     * @return Map where keys are player user IDs (Long) and values are Maps of Magic to usage count (Integer)
     */
    public Map<Long, Map<Magic, Integer>> calculatePlayerMagicUsage() {
        return calculatePlayerMagicUsage(null, null);
    }

    /**
     * Calculates magic usage statistics per player, filtered by game type.
     *
     * @param gameType The type of game to filter by (PVP, Practice), or null for all games
     * @return Map where keys are player user IDs (Long) and values are Maps of Magic to usage count (Integer)
     */
    public Map<Long, Map<Magic, Integer>> calculatePlayerMagicUsage(GameType gameType) {
        return calculatePlayerMagicUsage(gameType, null);
    }

    /**
     * Calculates magic usage statistics per player, filtered by game type and date.
     *
     * @param gameType The type of game to filter by (PVP, Practice), or null for all games
     * @param fromDate The date to filter from (inclusive), or null for all dates
     * @return Map where keys are player user IDs (Long) and values are Maps of Magic to usage count (Integer)
     */
    public Map<Long, Map<Magic, Integer>> calculatePlayerMagicUsage(GameType gameType, LocalDateTime fromDate) {
        List<StatisticGame> statisticGames = getStatisticGames(gameType, fromDate);

        Map<Long, Map<Magic, Integer>> playerMagicUsage = new HashMap<>();

        statisticGames.forEach(statisticGame -> {
            statisticGame.getStatisticGameMagics().forEach(statisticGameMagic -> {
                Long userId = statisticGameMagic.getUserId();
                Magic magic = statisticGameMagic.getMagic();

                playerMagicUsage.computeIfAbsent(userId, k -> new HashMap<>());
                Map<Magic, Integer> magicUsage = playerMagicUsage.get(userId);
                magicUsage.put(magic, magicUsage.getOrDefault(magic, 0) + statisticGameMagic.getCount());
            });
        });

        return playerMagicUsage;
    }

    private List<StatisticGame> getStatisticGames(GameType gameType, LocalDateTime fromDate) {
        List<StatisticGame> statisticGames;
        if (gameType == null && fromDate == null) {
            statisticGames = statisticGameRepository.findAll();
        } else if (gameType == null) {
            statisticGames = statisticGameRepository.findByCreatedAtAfter(fromDate);
        } else if (fromDate == null) {
            statisticGames = statisticGameRepository.findByGameType(gameType);
        } else {
            statisticGames = statisticGameRepository.findByGameTypeAndCreatedAtAfter(gameType, fromDate);
        }
        // Abandoned games are partial recordings cut off by the watchdog; including them
        // would skew play counts and win rates. They are visible on the Game Sessions page.
        return statisticGames.stream()
                .filter(statisticGame -> statisticGame.getOutcome() != GameOutcome.ABANDONED)
                .toList();
    }
}
