package com.wordonline.admin.service;

import com.wordonline.admin.dto.bot.BotAdminDto;
import com.wordonline.admin.dto.bot.BotForm;
import com.wordonline.admin.repository.magic.MagicRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BotDualDatabaseServiceTest {

    @Mock BotAdminService primary;
    @Mock SecondaryBotAdminService secondary;
    @Mock ObjectProvider<SecondaryBotAdminService> secondaryProvider;
    @Mock MagicRepository magicRepository;

    @Test
    void comparesBotsByUserIdAndKeepsMissingSide() {
        when(secondaryProvider.getIfAvailable()).thenReturn(secondary);
        when(primary.findAll()).thenReturn(List.of(bot(-1, "Deploy")));
        when(secondary.findAll()).thenReturn(List.of(bot(-2, "Dev")));

        var comparisons = service().comparisons();

        assertEquals(List.of(-2L, -1L), comparisons.stream().map(c -> c.userId()).sorted().toList());
        assertNull(comparisons.stream().filter(c -> c.userId() == -1).findFirst().orElseThrow().secondary());
        assertNull(comparisons.stream().filter(c -> c.userId() == -2).findFirst().orElseThrow().primary());
    }

    @Test
    void bothUpdateWritesDeployThenDev() {
        when(secondaryProvider.getIfAvailable()).thenReturn(secondary);
        BotForm form = new BotForm();

        service().update(-1, form, BotDualDatabaseService.Target.BOTH);

        verify(primary).update(-1, form);
        verify(secondary).update(-1, form);
    }

    @Test
    void secondaryTargetFailsWhenDevDatabaseIsMissing() {
        when(secondaryProvider.getIfAvailable()).thenReturn(null);

        assertThrows(IllegalStateException.class,
                () -> service().update(-1, new BotForm(), BotDualDatabaseService.Target.SECONDARY));
    }

    @Test
    void syncToSecondaryUpsertsEveryDeployBot() {
        when(secondaryProvider.getIfAvailable()).thenReturn(secondary);
        BotAdminDto created = bot(-1, "Created");
        BotAdminDto updated = bot(-2, "Updated");
        when(primary.findAll()).thenReturn(List.of(created, updated));
        when(secondary.upsert(created)).thenReturn(true);
        when(secondary.upsert(updated)).thenReturn(false);

        var result = service().syncToSecondary();

        assertEquals(1, result.created());
        assertEquals(1, result.updated());
        assertEquals(List.of(-1L, -2L), result.userIds());
    }

    @Test
    void syncToPrimaryPassesTheHospitalityBotThroughUnchanged() {
        BotAdminDto hospitalityBot = new BotAdminDto(-5L, "Warm Welcome", "HOSPITALITY", 1200, 30, -1.0,
                true, true, (short) 600, "Online", 9L, "Warm Welcome", List.of());
        when(secondaryProvider.getIfAvailable()).thenReturn(secondary);
        when(secondary.findAll()).thenReturn(List.of(hospitalityBot));

        var result = service().syncToPrimary();

        verify(primary).upsert(hospitalityBot);
        assertEquals(List.of(-5L), result.userIds());
    }

    private BotDualDatabaseService service() {
        return new BotDualDatabaseService(primary, secondaryProvider, magicRepository);
    }

    private BotAdminDto bot(long id, String name) {
        return new BotAdminDto(id, name, "BEGINNER", 250, 8, 0.25, true, false,
                (short) 1000, "Online", 1L, "Deck", List.of());
    }
}
