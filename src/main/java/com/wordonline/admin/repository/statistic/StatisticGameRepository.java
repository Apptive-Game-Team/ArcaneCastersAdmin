package com.wordonline.admin.repository.statistic;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.wordonline.admin.entity.statistic.GameType;
import com.wordonline.admin.entity.statistic.StatisticGame;

public interface StatisticGameRepository extends JpaRepository<StatisticGame, Long> {

    @EntityGraph(attributePaths = {
            "statisticGameMagics",
            "statisticGameMagics.magic",
            "statisticGameDecks",
            "statisticGameDecks.magic",
    })
    List<StatisticGame> findAll();

    @EntityGraph(attributePaths = {
            "statisticGameMagics",
            "statisticGameMagics.magic",
            "statisticGameDecks",
            "statisticGameDecks.magic",
    })
    List<StatisticGame> findByGameType(GameType gameType);

    @EntityGraph(attributePaths = {
            "statisticGameMagics",
            "statisticGameMagics.magic",
            "statisticGameDecks",
            "statisticGameDecks.magic",
    })
    List<StatisticGame> findByCreatedAtAfter(LocalDateTime date);

    @EntityGraph(attributePaths = {
            "statisticGameMagics",
            "statisticGameMagics.magic",
            "statisticGameDecks",
            "statisticGameDecks.magic",
    })
    List<StatisticGame> findByGameTypeAndCreatedAtAfter(GameType gameType, LocalDateTime date);
}
