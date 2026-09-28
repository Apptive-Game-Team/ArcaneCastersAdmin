package com.wordonline.admin.service;

import com.wordonline.admin.dto.bot.BotAdminDto;
import com.wordonline.admin.dto.bot.BotDeckForm;
import com.wordonline.admin.dto.bot.BotForm;
import com.wordonline.admin.repository.bot.BotAdminRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BotAdminServiceTest {

    @Mock
    private BotAdminRepository repository;

    @Test
    void createsUserDeckSelectionAndPersonaInOrder() {
        BotAdminService service = new BotAdminService(repository);
        BotForm form = validForm();
        when(repository.allocateUserId()).thenReturn(-7L);
        when(repository.createDeck(-7L, "Bot Deck")).thenReturn(42L);

        assertEquals(-7L, service.create(form));

        InOrder order = inOrder(repository);
        order.verify(repository).allocateUserId();
        order.verify(repository).createUser(-7L, form);
        order.verify(repository).createDeck(-7L, "Bot Deck");
        order.verify(repository).selectDeck(-7L, 42L);
        order.verify(repository).createPersona(-7L, form);
    }

    @Test
    void rejectsNonNegativeAllocatedId() {
        BotAdminService service = new BotAdminService(repository);
        when(repository.allocateUserId()).thenReturn(1L);

        assertThrows(IllegalArgumentException.class, () -> service.create(validForm()));
    }

    @Test
    void rejectsMismatchedDeckCardInputs() {
        BotAdminService service = new BotAdminService(repository);
        BotDeckForm form = new BotDeckForm();
        form.setMagicIds(List.of(1L, 2L));
        form.setCounts(List.of(1));

        assertThrows(IllegalArgumentException.class, () -> service.replaceDeck(-1L, form));
    }

    @Test
    void disablesBeforePermanentDelete() {
        BotAdminService service = new BotAdminService(repository);

        service.delete(-3L);

        InOrder order = inOrder(repository);
        order.verify(repository).setEnabled(-3L, false);
        order.verify(repository).delete(-3L);
    }

    @Test
    void acceptsTheHospitalityTierAndANegativeCounterAggression() {
        BotAdminService service = new BotAdminService(repository);
        BotForm form = validForm();
        form.setTier("HOSPITALITY");
        form.setCounterAggression(-1.0);
        form.setHospitality(true);
        when(repository.allocateUserId()).thenReturn(-7L);
        when(repository.createDeck(-7L, "Bot Deck")).thenReturn(42L);

        assertEquals(-7L, service.create(form));

        verify(repository).createPersona(-7L, form);
    }

    @Test
    void rejectsACounterAggressionOutsideTheWidenedRange() {
        BotAdminService service = new BotAdminService(repository);
        BotForm belowRange = validForm();
        belowRange.setCounterAggression(-1.5);
        BotForm aboveRange = validForm();
        aboveRange.setCounterAggression(1.5);

        assertThrows(IllegalArgumentException.class, () -> service.create(belowRange));
        assertThrows(IllegalArgumentException.class, () -> service.create(aboveRange));
    }

    @Test
    void upsertCarriesHospitalityIntoTheDeployUpdate() {
        BotAdminService service = new BotAdminService(repository);
        BotAdminDto source = new BotAdminDto(-5L, "Warm Welcome", "HOSPITALITY", 1200, 30, -1.0,
                true, true, "STOIC", (short) 600, "Online", 9L, "Warm Welcome", List.of());
        when(repository.findByUserId(-5L)).thenReturn(Optional.of(source));

        service.upsert(source);

        ArgumentCaptor<BotForm> form = ArgumentCaptor.forClass(BotForm.class);
        verify(repository).update(eq(-5L), form.capture());
        assertTrue(form.getValue().isHospitality());
        assertEquals("HOSPITALITY", form.getValue().getTier());
        assertEquals(-1.0, form.getValue().getCounterAggression());
        assertEquals("STOIC", form.getValue().getTemperament());
    }

    @Test
    void defaultsTemperamentToWarmWhenNotChosen() {
        BotAdminService service = new BotAdminService(repository);
        BotForm form = validForm();
        form.setTemperament("");
        when(repository.allocateUserId()).thenReturn(-7L);
        when(repository.createDeck(-7L, "Bot Deck")).thenReturn(42L);

        service.create(form);

        assertEquals("WARM", form.getTemperament());
    }

    @Test
    void rejectsAnUnsupportedTemperament() {
        BotAdminService service = new BotAdminService(repository);
        BotForm form = validForm();
        form.setTemperament("GRUMPY");

        assertThrows(IllegalArgumentException.class, () -> service.create(form));
    }

    private BotForm validForm() {
        BotForm form = new BotForm();
        form.setName("Test Bot");
        form.setDeckName("Bot Deck");
        return form;
    }
}
