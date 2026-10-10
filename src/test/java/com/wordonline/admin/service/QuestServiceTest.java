package com.wordonline.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import com.wordonline.admin.dto.quest.QuestDto;
import com.wordonline.admin.dto.quest.QuestRequestDto;
import com.wordonline.admin.dto.quest.QuestRewardDto;
import com.wordonline.admin.entity.quest.Quest;
import com.wordonline.admin.entity.quest.QuestReward;
import com.wordonline.admin.repository.quest.QuestRepository;
import com.wordonline.admin.repository.quest.QuestRewardRepository;

class QuestServiceTest {

    private final QuestRepository questRepository = mock(QuestRepository.class);
    private final QuestRewardRepository rewardRepository = mock(QuestRewardRepository.class);
    private final SecondaryAdminDataService secondary = mock(SecondaryAdminDataService.class);
    private final QuestService service =
            new QuestService(questRepository, rewardRepository, Optional.of(secondary));

    private final QuestRequestDto request = new QuestRequestDto("STAGE_CLEAR", 3L, 1, List.of(
            new QuestRewardDto(null, "MAGIC", 9L, 2),
            new QuestRewardDto(null, "COIN", null, 100)));

    @Test
    void createSavesQuestWithConditionAndEveryReward() {
        when(questRepository.save(any(Quest.class))).thenAnswer(inv -> {
            Quest q = inv.getArgument(0);
            return new Quest(42L, q.getConditionType(), q.getConditionTargetId(), q.getRequireValue());
        });

        Long id = service.createQuest(request);

        assertThat(id).isEqualTo(42L);
        ArgumentCaptor<QuestReward> rewards = ArgumentCaptor.forClass(QuestReward.class);
        verify(rewardRepository, org.mockito.Mockito.times(2)).save(rewards.capture());
        assertThat(rewards.getAllValues()).extracting(QuestReward::getRewardType).containsExactly("MAGIC", "COIN");
        assertThat(rewards.getAllValues().get(1).getTargetId()).isNull();
        assertThat(rewards.getAllValues().get(0).getQuest().getId()).isEqualTo(42L);
    }

    @Test
    void updateReplacesRewardListBeforeInserting() {
        when(questRepository.findById(5L)).thenReturn(Optional.of(new Quest(5L, "TOTAL_WIN", null, 1)));
        when(questRepository.save(any(Quest.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateQuest(5L, request);

        InOrder order = inOrder(rewardRepository);
        order.verify(rewardRepository).deleteByQuestId(5L);
        order.verify(rewardRepository, org.mockito.Mockito.times(2)).save(any(QuestReward.class));
    }

    @Test
    void secondaryCreateAndUpdateUseTheSecondaryService() {
        when(secondary.createQuest("STAGE_CLEAR", 3L, 1)).thenReturn(8L);

        assertThat(service.createQuest(request, true)).isEqualTo(8L);
        verify(secondary).createQuestReward(8L, "MAGIC", 9L, 2);
        verify(secondary).createQuestReward(8L, "COIN", null, 100);

        service.updateQuest(8L, request, true);
        InOrder order = inOrder(secondary);
        order.verify(secondary).updateQuest(8L, "STAGE_CLEAR", 3L, 1);
        order.verify(secondary).deleteQuestRewards(8L);
        verify(questRepository, never()).save(any());
    }

    @Test
    void invalidRequestsAreRejected() {
        assertThatThrownBy(() -> service.createQuest(new QuestRequestDto(" ", null, 1, List.of())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createQuest(new QuestRequestDto("TOTAL_WIN", null, 1,
                List.of(new QuestRewardDto(null, "MAGIC", 1L, 0)))))
                .isInstanceOf(IllegalArgumentException.class);
        verify(questRepository, never()).save(any());
    }

    @Test
    void syncToSecondaryPassesRewardsFromPrimary() {
        Quest quest = new Quest(1L, "TOTAL_WIN", null, 10);
        when(questRepository.findAll(org.springframework.data.domain.Sort.by("id"))).thenReturn(List.of(quest));
        when(rewardRepository.findByQuestIdOrderByIdAsc(1L))
                .thenReturn(List.of(new QuestReward(3L, quest, "DECORATION", 4L, 1)));
        when(secondary.syncQuestsToSecondary(any())).thenReturn(new SyncResult(1, 0, 0, List.of("quest#1")));

        service.syncToSecondary();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<QuestDto>> captor = ArgumentCaptor.forClass(List.class);
        verify(secondary).syncQuestsToSecondary(captor.capture());
        assertThat(captor.getValue().get(0).rewards())
                .containsExactly(new QuestRewardDto(3L, "DECORATION", 4L, 1));
    }
}
