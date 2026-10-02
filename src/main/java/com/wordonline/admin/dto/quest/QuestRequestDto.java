package com.wordonline.admin.dto.quest;

import java.util.List;

public record QuestRequestDto(
        String conditionType,
        Long conditionTargetId,
        Integer requireValue,
        List<QuestRewardDto> rewards
) {
}
