package com.wordonline.admin.entity.statistic;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.wordonline.admin.entity.magic.Magic;

import lombok.Getter;

@Entity
@Table(name = "statistic_game_decks")
@Getter
public class StatisticGameDeck {

    @Id
    private Long id;

    @ManyToOne
    @JoinColumn(name = "statistic_game_id")
    private StatisticGame statisticGame;

    private Long userId;

    @ManyToOne
    @JoinColumn(name = "magic_id")
    private Magic magic;

    private Integer count;

}
