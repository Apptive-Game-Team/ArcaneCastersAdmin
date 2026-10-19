package com.wordonline.admin.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wordonline.admin.dto.quest.QuestDto;
import com.wordonline.admin.dto.quest.QuestRequestDto;
import com.wordonline.admin.dto.quest.QuestRewardDto;
import com.wordonline.admin.entity.quest.Quest;
import com.wordonline.admin.entity.quest.QuestReward;
import com.wordonline.admin.repository.quest.QuestRepository;
import com.wordonline.admin.repository.quest.QuestRewardRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class QuestService {

    private final QuestRepository questRepository;
    private final QuestRewardRepository questRewardRepository;
    private final Optional<SecondaryAdminDataService> secondaryAdminDataService;

    public boolean hasSecondaryDatabase() {
        return secondaryAdminDataService.isPresent();
    }

    @Transactional(readOnly = true)
    public List<QuestDto> findAllQuests() {
        return findAllQuests(false);
    }

    @Transactional(readOnly = true)
    public List<QuestDto> findAllQuests(boolean secondary) {
        if (secondary) {
            return secondaryAdminDataService.orElseThrow().getQuests();
        }

        return questRepository.findAll(Sort.by("id")).stream()
                .map(quest -> {
                    List<QuestRewardDto> rewards = questRewardRepository.findByQuestIdOrderByIdAsc(quest.getId())
                            .stream()
                            .map(QuestRewardDto::new)
                            .collect(Collectors.toList());
                    return new QuestDto(quest, rewards);
                })
                .collect(Collectors.toList());
    }

    public Long createQuest(QuestRequestDto requestDto) {
        return createQuest(requestDto, false);
    }

    public Long createQuest(QuestRequestDto requestDto, boolean secondary) {
        validate(requestDto);
        if (secondary) {
            SecondaryAdminDataService secondaryService = secondaryAdminDataService.orElseThrow();
            Long questId = secondaryService.createQuest(
                    requestDto.conditionType(),
                    requestDto.conditionTargetId(),
                    requestDto.requireValue()
            );
            insertSecondaryRewards(secondaryService, questId, requestDto.rewards());
            return questId;
        }

        Quest savedQuest = questRepository.save(new Quest(
                null,
                requestDto.conditionType(),
                requestDto.conditionTargetId(),
                requestDto.requireValue()
        ));
        saveRewards(savedQuest, requestDto.rewards());
        return savedQuest.getId();
    }

    public void updateQuest(Long questId, QuestRequestDto requestDto) {
        updateQuest(questId, requestDto, false);
    }

    public void updateQuest(Long questId, QuestRequestDto requestDto, boolean secondary) {
        validate(requestDto);
        if (secondary) {
            SecondaryAdminDataService secondaryService = secondaryAdminDataService.orElseThrow();
            secondaryService.updateQuest(
                    questId,
                    requestDto.conditionType(),
                    requestDto.conditionTargetId(),
                    requestDto.requireValue()
            );
            secondaryService.deleteQuestRewards(questId);
            insertSecondaryRewards(secondaryService, questId, requestDto.rewards());
            return;
        }

        Quest quest = questRepository.findById(questId)
                .orElseThrow(() -> new IllegalArgumentException("Quest not found"));

        Quest updatedQuest = questRepository.save(new Quest(
                quest.getId(),
                requestDto.conditionType(),
                requestDto.conditionTargetId(),
                requestDto.requireValue()
        ));

        // The reward list is replaced as a whole.
        questRewardRepository.deleteByQuestId(questId);
        saveRewards(updatedQuest, requestDto.rewards());
    }

    public void deleteQuest(Long questId) {
        deleteQuest(questId, false);
    }

    public void deleteQuest(Long questId, boolean secondary) {
        if (secondary) {
            secondaryAdminDataService.orElseThrow().deleteQuest(questId);
            return;
        }
        // quest_rewards rows go with the quest through the foreign key cascade.
        questRepository.deleteById(questId);
    }

    public SyncResult syncToSecondary() {
        return secondaryAdminDataService.orElseThrow().syncQuestsToSecondary(findAllQuests(false));
    }

    public SyncResult syncToPrimary() {
        List<QuestDto> quests = secondaryAdminDataService.orElseThrow().getQuests();
        Map<Long, Quest> questsById = questRepository.findAll()
                .stream()
                .collect(Collectors.toMap(Quest::getId, quest -> quest));
        int created = 0;
        int updated = 0;
        int unchanged = 0;
        List<String> changed = new java.util.ArrayList<>();

        for (QuestDto questDto : quests) {
            Quest existing = questsById.get(questDto.id());
            Quest quest = new Quest(
                    questDto.id(),
                    questDto.conditionType(),
                    questDto.conditionTargetId(),
                    questDto.requireValue()
            );

            if (existing == null) {
                created++;
                changed.add("quest#" + questDto.id());
            } else if (!Objects.equals(existing.getConditionType(), questDto.conditionType())
                    || !Objects.equals(existing.getConditionTargetId(), questDto.conditionTargetId())
                    || !Objects.equals(existing.getRequireValue(), questDto.requireValue())) {
                updated++;
                changed.add("quest#" + questDto.id());
            } else {
                unchanged++;
            }

            Quest saved = questRepository.save(quest);
            questRewardRepository.deleteByQuestId(questDto.id());
            saveRewards(saved, questDto.rewards());
        }

        return new SyncResult(created, updated, unchanged, changed);
    }

    private void saveRewards(Quest quest, List<QuestRewardDto> rewards) {
        if (rewards == null) {
            return;
        }
        for (QuestRewardDto reward : rewards) {
            questRewardRepository.save(new QuestReward(
                    null,
                    quest,
                    reward.rewardType(),
                    reward.targetId(),
                    reward.amount()
            ));
        }
    }

    private void insertSecondaryRewards(SecondaryAdminDataService secondaryService, Long questId,
                                        List<QuestRewardDto> rewards) {
        if (rewards == null) {
            return;
        }
        for (QuestRewardDto reward : rewards) {
            secondaryService.createQuestReward(questId, reward.rewardType(), reward.targetId(), reward.amount());
        }
    }

    private void validate(QuestRequestDto requestDto) {
        if (requestDto.conditionType() == null || requestDto.conditionType().isBlank()) {
            throw new IllegalArgumentException("conditionType is required");
        }
        if (requestDto.requireValue() == null) {
            throw new IllegalArgumentException("requireValue is required");
        }
        if (requestDto.rewards() == null) {
            return;
        }
        for (QuestRewardDto reward : requestDto.rewards()) {
            if (reward.rewardType() == null || reward.rewardType().isBlank()) {
                throw new IllegalArgumentException("rewardType is required");
            }
            if (reward.amount() == null || reward.amount() <= 0) {
                throw new IllegalArgumentException("reward amount must be greater than 0");
            }
        }
    }
}
