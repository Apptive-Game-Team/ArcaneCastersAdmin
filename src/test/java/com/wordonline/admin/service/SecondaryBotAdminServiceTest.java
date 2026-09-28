package com.wordonline.admin.service;

import com.wordonline.admin.dto.bot.BotAdminDto;
import com.wordonline.admin.dto.bot.BotForm;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecondaryBotAdminServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void readsHospitalityFromTheDevProjection() throws Exception {
        SecondaryBotAdminService service = new SecondaryBotAdminService(jdbcTemplate);
        ArgumentCaptor<RowMapper<BotAdminDto>> mapper = captor();
        when(jdbcTemplate.query(contains("bp.hospitality"), mapper.capture())).thenReturn(List.of());

        service.findAll();

        ResultSet resultSet = mock(ResultSet.class, withSettings().strictness(Strictness.LENIENT));
        when(resultSet.getBoolean("hospitality")).thenReturn(true);
        when(resultSet.getString("tier")).thenReturn("HOSPITALITY");
        when(resultSet.getDouble("counter_aggression")).thenReturn(-1.0);
        when(resultSet.getString("temperament")).thenReturn("STOIC");
        BotAdminDto bot = mapper.getValue().mapRow(resultSet, 0);

        assertTrue(bot.hospitality());
        assertEquals("HOSPITALITY", bot.tier());
        assertEquals(-1.0, bot.counterAggression());
        assertEquals("STOIC", bot.temperament());
    }

    @Test
    void writesHospitalityWhenSyncingIntoDev() {
        SecondaryBotAdminService service = new SecondaryBotAdminService(jdbcTemplate);
        when(jdbcTemplate.queryForObject(contains("INSERT INTO decks"), eq(Long.class), any(Object[].class)))
                .thenReturn(9L);

        service.create(-5L, hospitalityForm());

        verify(jdbcTemplate).update(contains("INSERT INTO bot_personas"), eq(-5L), eq("Warm Welcome"),
                eq("HOSPITALITY"), eq(1200), eq(30), eq(-1.0), eq(true), eq(true), eq("STOIC"));
    }

    @Test
    void keepsHospitalityOnTheDevUpdatePath() {
        SecondaryBotAdminService service = new SecondaryBotAdminService(jdbcTemplate);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        service.update(-5L, hospitalityForm());

        verify(jdbcTemplate).update(contains("UPDATE bot_personas"), eq("Warm Welcome"), eq("HOSPITALITY"),
                eq(1200), eq(30), eq(-1.0), eq(true), eq(true), eq("STOIC"), eq(-5L));
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<RowMapper<BotAdminDto>> captor() {
        return ArgumentCaptor.forClass(RowMapper.class);
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
