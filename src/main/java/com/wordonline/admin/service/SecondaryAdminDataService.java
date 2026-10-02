package com.wordonline.admin.service;

import com.wordonline.admin.dto.MagicDto;
import com.wordonline.admin.dto.adventure.AdventureDto;
import com.wordonline.admin.dto.adventure.ScenarioDto;
import com.wordonline.admin.dto.adventure.StageDto;
import com.wordonline.admin.dto.quest.QuestDto;
import com.wordonline.admin.dto.quest.QuestRewardDto;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@ConditionalOnBean(name = "secondaryJdbcTemplate")
@RequiredArgsConstructor
@Transactional(transactionManager = "secondaryTransactionManager")
public class SecondaryAdminDataService {

    @Qualifier("secondaryJdbcTemplate")
    private final JdbcTemplate jdbcTemplate;

    public record AdventureRow(Long id, String name, String accessType) {}
    public record StageRow(Long id, Long adventureId) {}
    public record ScenarioRow(Long id, Long stageId) {}
    public record QuestRow(Long id, String conditionType, Long conditionTargetId, Integer requireValue) {}
    public record QuestRewardRow(Long id, Long questId, String rewardType, Long targetId, Integer amount) {}
    public record MagicRow(Long id, String name, String element, String accessType) {}

    public List<AdventureDto> getAdventures() {
        Map<Long, List<ScenarioDto>> scenariosByStageId = getScenarioRows().stream()
                .map(row -> new ScenarioDto(row.id(), row.stageId()))
                .collect(Collectors.groupingBy(ScenarioDto::stageId));
        Map<Long, List<StageDto>> stagesByAdventureId = getStageRows().stream()
                .map(row -> new StageDto(row.id(), row.adventureId(), scenariosByStageId.getOrDefault(row.id(), List.of())))
                .collect(Collectors.groupingBy(StageDto::adventureId));

        return getAdventureRows().stream()
                .map(row -> new AdventureDto(
                        row.id(),
                        row.name(),
                        row.accessType(),
                        stagesByAdventureId.getOrDefault(row.id(), List.of())
                                .stream()
                                .sorted(Comparator.comparing(StageDto::id))
                                .toList()
                ))
                .toList();
    }

    public List<QuestDto> getQuests() {
        Map<Long, List<QuestRewardDto>> rewardsByQuestId = getQuestRewardRows().stream()
                .collect(Collectors.groupingBy(
                        QuestRewardRow::questId,
                        Collectors.mapping(
                                row -> new QuestRewardDto(row.id(), row.rewardType(), row.targetId(), row.amount()),
                                Collectors.toList()
                        )
                ));

        return getQuestRows().stream()
                .map(row -> new QuestDto(
                        row.id(),
                        row.conditionType(),
                        row.conditionTargetId(),
                        row.requireValue(),
                        rewardsByQuestId.getOrDefault(row.id(), List.of())
                ))
                .toList();
    }

    public List<MagicDto> getMagics() {
        return getMagicRows().stream()
                .map(row -> new MagicDto(row.id(), row.name(), row.element(), row.accessType()))
                .toList();
    }

    public Long createAdventure(String name, String accessType) {
        return jdbcTemplate.queryForObject(
                "insert into adventures (name, access_type) values (?, ?) returning id",
                Long.class,
                name,
                accessType
        );
    }

    public void updateAdventure(Long id, String name, String accessType) {
        jdbcTemplate.update("update adventures set name = ?, access_type = ? where id = ?", name, accessType, id);
    }

    public void deleteAdventure(Long id) {
        jdbcTemplate.update("delete from adventures where id = ?", id);
    }

    public Long createStage(Long adventureId) {
        return jdbcTemplate.queryForObject(
                "insert into stages (adventure_id) values (?) returning id",
                Long.class,
                adventureId
        );
    }

    public void deleteStage(Long stageId) {
        jdbcTemplate.update("delete from stages where id = ?", stageId);
    }

    public Long createScenario(Long stageId) {
        return jdbcTemplate.queryForObject(
                "insert into scenarios (stage_id) values (?) returning id",
                Long.class,
                stageId
        );
    }

    public void deleteScenario(Long scenarioId) {
        jdbcTemplate.update("delete from scenarios where id = ?", scenarioId);
    }

    public Long createQuest(String conditionType, Long conditionTargetId, Integer requireValue) {
        return jdbcTemplate.queryForObject(
                "insert into quests (condition_type, condition_target_id, require_value) values (?, ?, ?) returning id",
                Long.class,
                conditionType,
                conditionTargetId,
                requireValue
        );
    }

    public void updateQuest(Long id, String conditionType, Long conditionTargetId, Integer requireValue) {
        jdbcTemplate.update(
                "update quests set condition_type = ?, condition_target_id = ?, require_value = ? where id = ?",
                conditionType,
                conditionTargetId,
                requireValue,
                id
        );
    }

    public void deleteQuest(Long id) {
        jdbcTemplate.update("delete from quests where id = ?", id);
    }

    public void deleteQuestRewards(Long questId) {
        jdbcTemplate.update("delete from quest_rewards where quest_id = ?", questId);
    }

    public void createQuestReward(Long questId, String rewardType, Long targetId, Integer amount) {
        jdbcTemplate.update(
                "insert into quest_rewards (quest_id, reward_type, target_id, amount) values (?, ?, ?, ?)",
                questId, rewardType, targetId, amount
        );
    }

    public void createMagic(String name) {
        createMagic(name, "None", "DEFAULT");
    }

    public void createMagic(String name, String element, String accessType) {
        jdbcTemplate.update(
                "insert into magics (name, element, access_type) values (?, ?, ?)",
                name,
                element,
                accessType
        );
    }

    public void updateMagicName(Long id, String name) {
        jdbcTemplate.update("update magics set name = ? where id = ?", name, id);
    }

    public void deleteMagic(Long id) {
        jdbcTemplate.update("delete from magics where id = ?", id);
    }

    public void updateMagicName(String currentName, String newName) {
        int updated = jdbcTemplate.update(
                "update magics set name = ? where name = ?",
                newName,
                currentName
        );
        if (updated == 0) {
            throw new IllegalArgumentException("Magic not found in secondary database: " + currentName);
        }
    }

    public void updateMagic(String currentName, String newName, String element, String accessType) {
        int updated = jdbcTemplate.update(
                "update magics set name = ?, element = ?, access_type = ? where name = ?",
                newName,
                element,
                accessType,
                currentName
        );
        if (updated == 0) {
            throw new IllegalArgumentException("Magic not found in secondary database: " + currentName);
        }
    }

    public void deleteMagic(String name) {
        int deleted = jdbcTemplate.update("delete from magics where name = ?", name);
        if (deleted == 0) {
            throw new IllegalArgumentException("Magic not found in secondary database: " + name);
        }
    }

    public SyncResult syncAdventuresToSecondary(List<AdventureDto> adventures) {
        int created = 0;
        int updated = 0;
        int unchanged = 0;
        List<String> changed = new ArrayList<>();
        Map<Long, AdventureRow> existingAdventures = getAdventureRows().stream()
                .collect(Collectors.toMap(AdventureRow::id, row -> row));
        Map<Long, StageRow> existingStages = getStageRows().stream()
                .collect(Collectors.toMap(StageRow::id, row -> row));
        Map<Long, ScenarioRow> existingScenarios = getScenarioRows().stream()
                .collect(Collectors.toMap(ScenarioRow::id, row -> row));

        for (AdventureDto adventure : adventures) {
            AdventureRow existing = existingAdventures.get(adventure.id());
            if (existing == null) {
                jdbcTemplate.update(
                        "insert into adventures (id, name, access_type) values (?, ?, ?)",
                        adventure.id(),
                        adventure.name(),
                        adventure.accessType()
                );
                created++;
                changed.add("adventure#" + adventure.id());
            } else if (!Objects.equals(existing.name(), adventure.name())
                    || !Objects.equals(existing.accessType(), adventure.accessType())) {
                updateAdventure(adventure.id(), adventure.name(), adventure.accessType());
                updated++;
                changed.add("adventure#" + adventure.id());
            } else {
                unchanged++;
            }

            for (StageDto stage : adventure.stages()) {
                StageRow existingStage = existingStages.get(stage.id());
                if (existingStage == null) {
                    jdbcTemplate.update("insert into stages (id, adventure_id) values (?, ?)", stage.id(), adventure.id());
                    created++;
                    changed.add("stage#" + stage.id());
                } else if (!Objects.equals(existingStage.adventureId(), adventure.id())) {
                    jdbcTemplate.update("update stages set adventure_id = ? where id = ?", adventure.id(), stage.id());
                    updated++;
                    changed.add("stage#" + stage.id());
                } else {
                    unchanged++;
                }

                for (ScenarioDto scenario : stage.scenarios()) {
                    ScenarioRow existingScenario = existingScenarios.get(scenario.id());
                    if (existingScenario == null) {
                        jdbcTemplate.update("insert into scenarios (id, stage_id) values (?, ?)", scenario.id(), stage.id());
                        created++;
                        changed.add("scenario#" + scenario.id());
                    } else if (!Objects.equals(existingScenario.stageId(), stage.id())) {
                        jdbcTemplate.update("update scenarios set stage_id = ? where id = ?", stage.id(), scenario.id());
                        updated++;
                        changed.add("scenario#" + scenario.id());
                    } else {
                        unchanged++;
                    }
                }
            }
        }

        return new SyncResult(created, updated, unchanged, changed);
    }

    public SyncResult syncQuestsToSecondary(List<QuestDto> quests) {
        int created = 0;
        int updated = 0;
        int unchanged = 0;
        List<String> changed = new ArrayList<>();
        Map<Long, QuestRow> existingQuests = getQuestRows().stream()
                .collect(Collectors.toMap(QuestRow::id, row -> row));

        for (QuestDto quest : quests) {
            QuestRow existing = existingQuests.get(quest.id());
            if (existing == null) {
                jdbcTemplate.update(
                        "insert into quests (id, condition_type, condition_target_id, require_value) values (?, ?, ?, ?)",
                        quest.id(),
                        quest.conditionType(),
                        quest.conditionTargetId(),
                        quest.requireValue()
                );
                created++;
                changed.add("quest#" + quest.id());
            } else if (!Objects.equals(existing.conditionType(), quest.conditionType())
                    || !Objects.equals(existing.conditionTargetId(), quest.conditionTargetId())
                    || !Objects.equals(existing.requireValue(), quest.requireValue())) {
                updateQuest(quest.id(), quest.conditionType(), quest.conditionTargetId(), quest.requireValue());
                updated++;
                changed.add("quest#" + quest.id());
            } else {
                unchanged++;
            }

            // Rewards are replaced as a whole; ids are left to the secondary sequence.
            deleteQuestRewards(quest.id());
            for (QuestRewardDto reward : quest.rewards()) {
                createQuestReward(quest.id(), reward.rewardType(), reward.targetId(), reward.amount());
            }
        }

        return new SyncResult(created, updated, unchanged, changed);
    }

    public SyncResult syncMagicsToSecondary(List<MagicDto> magics) {
        int created = 0;
        int updated = 0;
        int unchanged = 0;
        List<String> changed = new ArrayList<>();
        Map<String, MagicRow> existingMagics = getMagicRows().stream()
                .collect(Collectors.toMap(
                        MagicRow::name,
                        row -> row,
                        (current, replacement) -> {
                            throw new IllegalStateException("Duplicate magic name in secondary database");
                        }
                ));

        for (MagicDto magic : magics) {
            MagicRow existing = existingMagics.get(magic.name());
            if (existing == null) {
                jdbcTemplate.update(
                        "insert into magics (name, element, access_type) values (?, ?, ?)",
                        magic.name(),
                        magic.element(),
                        magic.accessType()
                );
                created++;
                changed.add(magic.name());
            } else if (!Objects.equals(existing.element(), magic.element())
                    || !Objects.equals(existing.accessType(), magic.accessType())) {
                jdbcTemplate.update(
                        "update magics set element = ?, access_type = ? where name = ?",
                        magic.element(),
                        magic.accessType(),
                        magic.name()
                );
                updated++;
                changed.add(magic.name());
            } else {
                unchanged++;
            }
        }

        return new SyncResult(created, updated, unchanged, changed);
    }

    public List<AdventureRow> getAdventureRows() {
        return jdbcTemplate.query(
                "select id, name, access_type from adventures order by id",
                (rs, rowNum) -> new AdventureRow(rs.getLong("id"), rs.getString("name"), rs.getString("access_type"))
        );
    }

    public List<StageRow> getStageRows() {
        return jdbcTemplate.query(
                "select id, adventure_id from stages order by id",
                (rs, rowNum) -> new StageRow(rs.getLong("id"), rs.getLong("adventure_id"))
        );
    }

    public List<ScenarioRow> getScenarioRows() {
        return jdbcTemplate.query(
                "select id, stage_id from scenarios order by id",
                (rs, rowNum) -> new ScenarioRow(rs.getLong("id"), rs.getLong("stage_id"))
        );
    }

    public List<QuestRow> getQuestRows() {
        return jdbcTemplate.query(
                "select id, condition_type, condition_target_id, require_value from quests order by id",
                (rs, rowNum) -> new QuestRow(
                        rs.getLong("id"),
                        rs.getString("condition_type"),
                        rs.getObject("condition_target_id", Long.class),
                        rs.getObject("require_value", Integer.class)
                )
        );
    }

    public List<QuestRewardRow> getQuestRewardRows() {
        return jdbcTemplate.query(
                "select id, quest_id, reward_type, target_id, amount from quest_rewards order by id",
                (rs, rowNum) -> new QuestRewardRow(
                        rs.getLong("id"),
                        rs.getLong("quest_id"),
                        rs.getString("reward_type"),
                        rs.getObject("target_id", Long.class),
                        rs.getObject("amount", Integer.class)
                )
        );
    }

    public List<MagicRow> getMagicRows() {
        return jdbcTemplate.query(
                "select id, name, element, access_type from magics order by id",
                (rs, rowNum) -> new MagicRow(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("element"),
                        rs.getString("access_type")
                )
        );
    }

    public void grantDefaultContents() {
        // 1. Grant default magics
        String grantMagicsSql = """
            INSERT INTO user_magics(user_id, magic_id)
            SELECT u.id, m.id
            FROM users u, magics m
            WHERE m.access_type = 'DEFAULT' AND m.purpose = 'PLAYER' AND
                NOT EXISTS(
                    SELECT 1
                    FROM user_magics um
                    WHERE um.user_id = u.id AND um.magic_id = m.id
                )
            """;
        jdbcTemplate.update(grantMagicsSql);

        // 2. Grant free adventures (scenarios)
        String grantAdventuresSql = """
            INSERT INTO user_scenarios(user_id, scenario_id)
            SELECT u.id, s.id
            FROM users u, scenarios s
            JOIN stages st ON s.stage_id = st.id
            JOIN adventures a ON st.adventure_id = a.id
            WHERE a.access_type = 'FREE' AND
                NOT EXISTS(
                    SELECT 1
                    FROM user_scenarios us
                    WHERE us.user_id = u.id AND us.scenario_id = s.id
                )
            """;
        jdbcTemplate.update(grantAdventuresSql);
    }
}
