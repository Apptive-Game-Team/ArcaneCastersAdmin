package com.wordonline.admin.dto.quest;

import java.util.List;

import com.wordonline.admin.entity.quest.Quest;

public record QuestDto(
        Long id,
        String conditionType,
        Long conditionTargetId,
        Integer requireValue,
        List<QuestRewardDto> rewards
) {

    public QuestDto(Quest quest, List<QuestRewardDto> rewards) {
        this(quest.getId(), quest.getConditionType(), quest.getConditionTargetId(),
                quest.getRequireValue(), rewards);
    }
}
