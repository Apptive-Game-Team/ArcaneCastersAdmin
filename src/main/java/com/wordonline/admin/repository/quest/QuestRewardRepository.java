package com.wordonline.admin.repository.quest;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.wordonline.admin.entity.quest.QuestReward;

public interface QuestRewardRepository extends JpaRepository<QuestReward, Long> {

    List<QuestReward> findByQuestIdOrderByIdAsc(Long questId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from QuestReward r where r.quest.id = :questId")
    void deleteByQuestId(@Param("questId") Long questId);
}
