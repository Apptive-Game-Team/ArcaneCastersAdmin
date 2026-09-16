package com.wordonline.admin.entity.statistic;

import java.time.LocalDateTime;
import java.util.Set;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import lombok.Getter;

@Table(name = "statistic_games")
@Entity
@Getter
public class StatisticGame {

    @Id
    private Long id;
    private Long winUserId;
    private Long lossUserId;
    private Long duration;
    private LocalDateTime createdAt;
    private String serverVersion;
    private Integer eventSchemaVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome")
    private GameOutcome outcome;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "game_type")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private GameType gameType;

    @OneToMany(mappedBy = "statisticGame")
    private Set<StatisticGameMagic> statisticGameMagics;

    @OneToMany(mappedBy = "statisticGame")
    private Set<StatisticGameDeck> statisticGameDecks;
}
