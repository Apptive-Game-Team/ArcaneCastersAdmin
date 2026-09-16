package com.wordonline.admin.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.wordonline.admin.dto.sheet.GameObjectDto;
import com.wordonline.admin.dto.ParameterDto;
import com.wordonline.admin.dto.sheet.GameObjectComparisonDto;
import com.wordonline.admin.dto.sheet.ParameterComparisonDto;
import com.wordonline.admin.controller.SpreadSheetApiController;
import com.wordonline.admin.entity.magic.Magic;
import com.wordonline.admin.entity.parameter.GameObject;
import com.wordonline.admin.entity.parameter.Parameter;
import com.wordonline.admin.entity.parameter.ParameterValue;
import com.wordonline.admin.repository.magic.MagicRepository;
import com.wordonline.admin.repository.parameter.GameObjectRepository;
import com.wordonline.admin.repository.tag.TagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class SpreadSheetServiceTest {

    @Mock
    private GameObjectRepository gameObjectRepository;

    @Mock
    private MagicRepository magicRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private ParameterService parameterService;

    @Mock
    private SecondaryParameterSyncService secondaryParameterSyncService;

    private SpreadSheetService spreadSheetService;

    @BeforeEach
    void setUp() {
        spreadSheetService = new SpreadSheetService(
                gameObjectRepository,
                magicRepository,
                tagRepository,
                parameterService,
                Optional.of(secondaryParameterSyncService)
        );
    }

    @Test
    void testGetGameObjects_withNullMagicId() {
        // Arrange
        GameObject gameObject = new GameObject("TestObject");
        Parameter parameter = new Parameter("magic_id");
        new ParameterValue(null, gameObject, parameter);

        when(gameObjectRepository.findAll(any(Sort.class))).thenReturn(List.of(gameObject));

        // Act
        List<GameObjectDto> results = spreadSheetService.getGameObjects(null);

        // Assert
        assertEquals(1, results.size());
        assertNull(results.get(0).manaCost());
    }

    @Test
    void testGetGameObjects_withMissingMagicId() {
        // Arrange
        GameObject gameObject = new GameObject("TestObject");

        when(gameObjectRepository.findAll(any(Sort.class))).thenReturn(List.of(gameObject));

        // Act
        List<GameObjectDto> results = spreadSheetService.getGameObjects(null);

        // Assert
        assertEquals(1, results.size());
        assertNull(results.get(0).manaCost());
    }

    @Test
    void testGetGameObjects_withNullManaCost() {
        // Arrange
        GameObject gameObject = new GameObject("TestObject");
        Parameter parameter = new Parameter("magic_id");
        new ParameterValue(42.0, gameObject, parameter);

        Magic magic = new Magic();
        magic.setName("TestMagic");

        GameObject magicGameObject = new GameObject("TestMagic");
        Parameter manaCostParam = new Parameter("mana_cost");
        new ParameterValue(null, magicGameObject, manaCostParam);

        when(gameObjectRepository.findAll(any(Sort.class))).thenReturn(List.of(gameObject));
        when(magicRepository.findById(42L)).thenReturn(Optional.of(magic));
        when(gameObjectRepository.findByName("TestMagic")).thenReturn(Optional.of(magicGameObject));

        // Act
        List<GameObjectDto> results = spreadSheetService.getGameObjects(null);

        // Assert
        assertEquals(1, results.size());
        assertNull(results.get(0).manaCost());
    }

    @Test
    void testGetGameObjects_readsManaCostFromMagicsOwnGameObject() {
        // Arrange
        GameObject gameObject = new GameObject("TestObject");
        Parameter parameter = new Parameter("magic_id");
        new ParameterValue(42.0, gameObject, parameter);

        Magic magic = new Magic();
        magic.setName("TestMagic");

        GameObject magicGameObject = new GameObject("TestMagic");
        Parameter manaCostParam = new Parameter("mana_cost");
        new ParameterValue(5.0, magicGameObject, manaCostParam);

        when(gameObjectRepository.findAll(any(Sort.class))).thenReturn(List.of(gameObject));
        when(magicRepository.findById(42L)).thenReturn(Optional.of(magic));
        when(gameObjectRepository.findByName("TestMagic")).thenReturn(Optional.of(magicGameObject));

        // Act
        List<GameObjectDto> results = spreadSheetService.getGameObjects(null);

        // Assert
        assertEquals(1, results.size());
        assertEquals(5, results.get(0).manaCost());
    }

    @Test
    void getGameObjectComparisons_mergesDatabasesByName() {
        GameObject primaryObject = new GameObject("archer");
        new ParameterValue(100.0, primaryObject, new Parameter("hp"));

        when(gameObjectRepository.findAll(any(Sort.class))).thenReturn(List.of(primaryObject));
        when(parameterService.getParameterDtos(false)).thenReturn(List.of(
                new ParameterDto(1L, "hp"),
                new ParameterDto(2L, "speed")
        ));
        when(parameterService.getParameterDtos(true)).thenReturn(List.of(
                new ParameterDto(10L, "damage"),
                new ParameterDto(11L, "hp")
        ));
        when(secondaryParameterSyncService.getGameObjects()).thenReturn(List.of(
                new SecondaryParameterSyncService.GameObjectRow(
                        50L,
                        "archer",
                        List.of(
                                new SecondaryParameterSyncService.ParameterValueRow(1L, 11L, "hp", 90.0),
                                new SecondaryParameterSyncService.ParameterValueRow(2L, 10L, "damage", 12.0)
                        )
                ),
                new SecondaryParameterSyncService.GameObjectRow(
                        51L,
                        "dev-only",
                        List.of()
                )
        ));

        List<GameObjectComparisonDto> result = spreadSheetService.getGameObjectComparisons(null);

        assertEquals(List.of("archer", "dev-only"), result.stream().map(GameObjectComparisonDto::name).toList());
        GameObjectComparisonDto archer = result.getFirst();
        assertTrue(archer.primaryPresent());
        assertTrue(archer.secondaryPresent());
        assertEquals(List.of("damage", "hp", "speed"), archer.parameters().stream()
                .map(ParameterComparisonDto::name)
                .toList());
        ParameterComparisonDto damage = archer.parameters().getFirst();
        assertFalse(damage.primaryPresent());
        assertTrue(damage.secondaryPresent());
        assertNull(damage.primaryValue());
        assertEquals(12.0, damage.secondaryValue());
        ParameterComparisonDto hp = archer.parameters().get(1);
        assertTrue(hp.primaryPresent());
        assertTrue(hp.secondaryPresent());
        assertEquals(100.0, hp.primaryValue());
        assertEquals(90.0, hp.secondaryValue());
        ParameterComparisonDto speed = archer.parameters().get(2);
        assertFalse(speed.primaryPresent());
        assertFalse(speed.secondaryPresent());
        assertNull(speed.primaryValue());
        assertNull(speed.secondaryValue());
        assertFalse(result.get(1).primaryPresent());
        assertTrue(result.get(1).secondaryPresent());
    }

    @Test
    void getGameObjectComparison_usesPresenceFlagsInsteadOfNullValues() {
        GameObject primaryObject = new GameObject("archer");
        new ParameterValue(100.0, primaryObject, new Parameter("hp"));
        new ParameterValue(null, primaryObject, new Parameter("nullable"));

        when(gameObjectRepository.findAll(any(Sort.class))).thenReturn(List.of(primaryObject));
        when(parameterService.getParameterDtos(false)).thenReturn(List.of(
                new ParameterDto(1L, "hp"),
                new ParameterDto(2L, "speed"),
                new ParameterDto(3L, "nullable")
        ));
        when(parameterService.getParameterDtos(true)).thenReturn(List.of(
                new ParameterDto(10L, "secondary-only")
        ));
        when(secondaryParameterSyncService.getGameObjects()).thenReturn(List.of(
                new SecondaryParameterSyncService.GameObjectRow(
                        50L,
                        "archer",
                        List.of(new SecondaryParameterSyncService.ParameterValueRow(1L, 10L, "secondary-only", 12.0))
                )
        ));

        GameObjectComparisonDto result = spreadSheetService.getGameObjectComparison("archer");

        assertEquals(List.of("hp", "nullable", "secondary-only"), result.parameters().stream()
                .map(ParameterComparisonDto::name)
                .toList());
        ParameterComparisonDto nullable = result.parameters().get(1);
        assertTrue(nullable.primaryPresent());
        assertFalse(nullable.secondaryPresent());
        assertNull(nullable.primaryValue());
        assertNull(nullable.secondaryValue());
        ParameterComparisonDto secondaryOnly = result.parameters().get(2);
        assertFalse(secondaryOnly.primaryPresent());
        assertTrue(secondaryOnly.secondaryPresent());
        assertNull(secondaryOnly.primaryValue());
        assertEquals(12.0, secondaryOnly.secondaryValue());
    }

    @Test
    void batchUpdateParametersByName_routesEachValueToSelectedDatabase() {
        List<SpreadSheetApiController.NamedParameterUpdateDto> updates = List.of(
                new SpreadSheetApiController.NamedParameterUpdateDto("archer", "hp", 100.0, "primary"),
                new SpreadSheetApiController.NamedParameterUpdateDto("archer", "hp", 90.0, "secondary")
        );

        spreadSheetService.batchUpdateParametersByName(updates);

        verify(parameterService).upsertParameterValue("archer", "hp", 100.0, false);
        verify(parameterService).upsertParameterValue("archer", "hp", 90.0, true);
    }
}
