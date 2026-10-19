package com.wordonline.admin.entity.quest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The legacy progress_checker and reward_giver columns are intentionally not mapped:
 * they are nullable in the schema and must be neither read nor written.
 */
@Entity
@Table(name = "quests")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Quest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "condition_type", nullable = false, length = 63)
    private String conditionType;

    @Column(name = "condition_target_id")
    private Long conditionTargetId;

    @Column(name = "require_value", nullable = false)
    private Integer requireValue;
}
