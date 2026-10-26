package com.wordonline.admin.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BalanceReferenceServiceTest {
    private BalanceSheetService.Snapshot snapshot() {
        return new BalanceSheetService.Snapshot(Map.of(
                "unit", new BalanceSheetService.ObjectData(Map.of("damage", 20.0, "radius", 2.0), List.of()),
                "shot", new BalanceSheetService.ObjectData(Map.of("damage", 50.0, "radius", 3.0), List.of()),
                "swarm", new BalanceSheetService.ObjectData(Map.of("quantity", 3.0), List.of())),
                List.of(new BalanceSheetService.MagicData("swarm", "Spawn", "unit")), new TreeMap<>());
    }
    private BalanceReferenceService.Change change(String object, String parameter, String target, String targetParameter, double multiplier) {
        return new BalanceReferenceService.Change("primary", object, parameter, target, targetParameter, multiplier, false);
    }
    @Test
    void registersUpdatesAndRemovesReferenceWithoutChangingValues() {
        var s = snapshot();
        BalanceReferenceService.validateAndApply(s, change("unit", "attack_area_radius", "shot", "radius", 0.5));
        assertEquals(0.5, s.references().get("unit").get("attack_area_radius").multiplier());
        BalanceReferenceService.validateAndApply(s, change("unit", "attack_area_radius", "unit", "radius", 1));
        assertEquals("unit", s.references().get("unit").get("attack_area_radius").objectName());
        BalanceReferenceService.validateAndApply(s, new BalanceReferenceService.Change("primary", "unit", "attack_area_radius", null, null, null, true));
        assertTrue(s.references().get("unit").isEmpty());
        assertEquals(20.0, s.objects().get("unit").values().get("damage"));
    }
    @Test
    void rejectsDirectAndIndirectCyclesIncludingMagicAliases() {
        var s = snapshot();
        assertThrows(ResponseStatusException.class, () -> BalanceReferenceService.validateAndApply(s, change("unit", "damage", "unit", "damage", 1)));
        BalanceReferenceService.validateAndApply(s, change("unit", "damage", "shot", "damage", 1));
        assertThrows(ResponseStatusException.class, () -> BalanceReferenceService.validateAndApply(s, change("shot", "damage", "unit", "damage", 1)));
        assertThrows(ResponseStatusException.class, () -> BalanceReferenceService.validateAndApply(s, change("unit", "damage", "swarm", "damage", 1)));
        assertEquals("shot", s.references().get("unit").get("damage").objectName());
    }
    @Test
    void rejectsMissingTargetsDerivedMetricsAndInvalidMultipliers() {
        var s = snapshot();
        for (var invalid : List.of(change("unit", "damage", "missing", "damage", 1),
                change("unit", "damage", "shot", "missing", 1), change("unit", "total_dps", "shot", "damage", 1),
                change("unit", "damage", "shot", "damage", 0), change("unit", "damage", "shot", "damage", Double.POSITIVE_INFINITY))) {
            assertThrows(ResponseStatusException.class, () -> BalanceReferenceService.validateAndApply(s, invalid));
        }
    }
    @Test
    void allowsReferenceChainsAndPreventsDeletingRequiredAlias() {
        var s = snapshot();
        BalanceReferenceService.validateAndApply(s, change("unit", "area_alias", "shot", "radius", 1));
        BalanceReferenceService.validateAndApply(s, change("swarm", "attack_area_radius", "unit", "area_alias", 0.5));
        assertThrows(ResponseStatusException.class, () -> BalanceReferenceService.validateAndApply(s,
                new BalanceReferenceService.Change("primary", "unit", "area_alias", null, null, null, true)));
        assertTrue(s.references().get("unit").containsKey("area_alias"));
    }

    @Test
    void rejectsOverflowAcrossReferenceChain() {
        var s = snapshot();
        BalanceReferenceService.validateAndApply(s, change("unit", "area_alias", "shot", "radius", Double.MAX_VALUE));
        assertThrows(ResponseStatusException.class, () -> BalanceReferenceService.validateAndApply(s,
                change("swarm", "attack_area_radius", "unit", "area_alias", 2)));
    }
}
