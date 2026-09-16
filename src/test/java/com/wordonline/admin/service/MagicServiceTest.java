package com.wordonline.admin.service;

import com.wordonline.admin.dto.MagicDto;
import com.wordonline.admin.repository.magic.MagicRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MagicServiceTest {

    @Mock
    private MagicRepository magicRepository;
    @Mock
    private SecondaryAdminDataService secondaryAdminDataService;
    private MagicService magicService;

    @BeforeEach
    void setUp() {
        magicService = new MagicService(
                magicRepository,
                Optional.of(secondaryAdminDataService)
        );
    }

    @Test
    void getMagicComparisons_mergesMagicsByName() {
        when(magicRepository.findAllByOrderByIdAsc()).thenReturn(List.of());
        when(secondaryAdminDataService.getMagics()).thenReturn(List.of(
                new MagicDto(20L, "fireball", "Fire", "DEFAULT")
        ));

        var comparisons = magicService.getMagicComparisons();

        assertEquals(1, comparisons.size());
        assertEquals("fireball", comparisons.getFirst().name());
        assertFalse(comparisons.getFirst().primaryPresent());
        assertTrue(comparisons.getFirst().secondaryPresent());
        assertEquals("Fire", comparisons.getFirst().secondaryElement());
        assertEquals("DEFAULT", comparisons.getFirst().secondaryAccessType());
        assertNull(comparisons.getFirst().primaryElement());
        assertNull(comparisons.getFirst().primaryAccessType());
    }
}
