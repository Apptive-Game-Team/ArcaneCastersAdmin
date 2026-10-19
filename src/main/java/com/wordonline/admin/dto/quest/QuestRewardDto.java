package com.wordonline.admin.dto.quest;

import com.wordonline.admin.entity.quest.QuestReward;

public record QuestRewardDto(
        Long id,
        String rewardType,
        Long targetId,
        Integer amount
) {

    public QuestRewardDto(QuestReward reward) {
        this(reward.getId(), reward.getRewardType(), reward.getTargetId(), reward.getAmount());
    }
}
