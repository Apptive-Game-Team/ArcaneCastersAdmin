package com.wordonline.admin.repository.bot;

import com.wordonline.admin.dto.bot.BotAdminDto;
import com.wordonline.admin.dto.bot.BotForm;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BotAdminRepositoryTest {

    @Mock
    private EntityManager entityManager;

    private BotAdminRepository repository;

    @BeforeEach
    void injectEntityManager() {
        repository = new BotAdminRepository();
        ReflectionTestUtils.setField(repository, "entityManager", entityManager);
    }

    @Test
    void readsHospitalityFromThePersonaProjection() {
        Query personaQuery = mock(Query.class);
        Query deckQuery = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenAnswer(invocation ->
                invocation.getArgument(0, String.class).contains("bot_personas") ? personaQuery : deckQuery);
        when(personaQuery.getResultList()).thenReturn(Collections.singletonList(new Object[]{
                -5L, "Warm Welcome", "HOSPITALITY", 1200, 30, -1.0, true, true, "STOIC",
                (short) 600, "Online", 7L, "Warm Welcome"
        }));
        when(deckQuery.setParameter(anyString(), any())).thenReturn(deckQuery);
        when(deckQuery.getResultList()).thenReturn(List.of());

        BotAdminDto bot = repository.findAll().getFirst();

        assertTrue(bot.hospitality());
        // 접대 봇은 enabled로 남는다. 컬럼 하나를 끼워 넣었으니 뒤따르는 인덱스도 같이 확인한다.
        assertTrue(bot.enabled());
        assertEquals("HOSPITALITY", bot.tier());
        assertEquals(-1.0, bot.counterAggression());
        assertEquals("STOIC", bot.temperament());
        assertEquals((short) 600, bot.mmr());
        assertEquals("Online", bot.status());
        assertEquals(7L, bot.selectedDeckId());
        assertEquals("Warm Welcome", bot.selectedDeckName());
    }

    @Test
    void writesHospitalityOnCreateAndUpdate() {
        Query query = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.executeUpdate()).thenReturn(1);

        repository.createPersona(-5L, hospitalityForm());
        repository.update(-5L, hospitalityForm());

        ArgumentCaptor<String> statements = ArgumentCaptor.forClass(String.class);
        verify(entityManager, org.mockito.Mockito.atLeastOnce()).createNativeQuery(statements.capture());
        assertTrue(statements.getAllValues().stream()
                .anyMatch(sql -> sql.contains("INSERT INTO bot_personas") && sql.contains("hospitality")));
        assertTrue(statements.getAllValues().stream()
                .anyMatch(sql -> sql.contains("UPDATE bot_personas") && sql.contains("hospitality=:hospitality")));
        verify(query, org.mockito.Mockito.times(2)).setParameter(eq("hospitality"), eq(true));
        assertTrue(statements.getAllValues().stream()
                .anyMatch(sql -> sql.contains("INSERT INTO bot_personas") && sql.contains("temperament")));
        assertTrue(statements.getAllValues().stream()
                .anyMatch(sql -> sql.contains("UPDATE bot_personas") && sql.contains("temperament=CAST(:temperament AS bot_temperament)")));
        verify(query, org.mockito.Mockito.times(2)).setParameter(eq("temperament"), eq("STOIC"));
    }

    private BotForm hospitalityForm() {
        BotForm form = new BotForm();
        form.setName("Warm Welcome");
        form.setTier("HOSPITALITY");
        form.setThinkingTimeMs(1200);
        form.setReactionIntervalFrames(30);
        form.setCounterAggression(-1.0);
        form.setEnabled(true);
        form.setHospitality(true);
        form.setTemperament("STOIC");
        form.setMmr((short) 600);
        form.setStatus("Online");
        form.setDeckName("Warm Welcome");
        return form;
    }
}
