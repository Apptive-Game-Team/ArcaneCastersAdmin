package com.wordonline.admin.service;

import com.wordonline.admin.controller.SpreadSheetApiController;
import com.wordonline.admin.dto.sheet.GameObjectComparisonDto;
import com.wordonline.admin.dto.sheet.GameObjectDto;
import com.wordonline.admin.dto.sheet.ParameterComparisonDto;
import com.wordonline.admin.entity.magic.Magic;
import com.wordonline.admin.entity.parameter.GameObject;
import com.wordonline.admin.entity.parameter.ParameterValue;
import com.wordonline.admin.repository.magic.MagicRepository;
import com.wordonline.admin.repository.parameter.GameObjectRepository;
import com.wordonline.admin.repository.tag.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpreadSheetService {

    private final GameObjectRepository gameObjectRepository;
    private final MagicRepository magicRepository;
    private final TagRepository tagRepository;
    private final ParameterService parameterService;
    private final Optional<SecondaryParameterSyncService> secondaryParameterSyncService;

    public boolean hasSecondaryDatabase() {
        return secondaryParameterSyncService.isPresent();
    }

    public List<GameObjectDto> getGameObjects(List<String> tagNames) {
        List<GameObject> allGameObjects = gameObjectRepository.findAll(Sort.by("id"));

        if (tagNames == null || tagNames.isEmpty()) {
            return allGameObjects.stream()
                    .map(this::fromGameObject)
                    .collect(Collectors.toList());
        }

        return allGameObjects.stream()
                .filter(gameObject -> {
                    List<String> gameObjectTagNames = gameObject.getGameObjectTags().stream()
                            .map(gameObjectTag -> gameObjectTag.getTag().getName())
                            .toList();
                    return gameObjectTagNames.containsAll(tagNames);
                })
                .map(this::fromGameObject)
                .collect(Collectors.toList());
    }

    public List<GameObjectDto> getGameObjects(List<String> tagNames, boolean secondary) {
        if (!secondary) {
            return getGameObjects(tagNames);
        }

        return secondaryParameterSyncService.orElseThrow().getGameObjects()
                .stream()
                .map(this::fromSecondaryGameObject)
                .collect(Collectors.toList());
    }

    public List<GameObjectComparisonDto> getGameObjectComparisons(List<String> tagNames) {
        List<GameObject> primaryGameObjects = gameObjectRepository.findAll(Sort.by("name"));
        Set<String> filteredPrimaryNames = primaryGameObjects.stream()
                .filter(gameObject -> hasAllTags(gameObject, tagNames))
                .map(GameObject::getName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, Map<String, ParameterValuePresence>> primaryValues = primaryGameObjects.stream()
                .filter(gameObject -> filteredPrimaryNames.contains(gameObject.getName()))
                .collect(Collectors.toMap(
                        GameObject::getName,
                        this::getParameterValuePresenceByName,
                        (current, replacement) -> {
                            throw new IllegalStateException("Duplicate primary game object name");
                        },
                        TreeMap::new
                ));

        List<SecondaryParameterSyncService.GameObjectRow> secondaryGameObjects = secondaryParameterSyncService
                .map(SecondaryParameterSyncService::getGameObjects)
                .orElseGet(List::of);
        Map<String, Map<String, ParameterValuePresence>> secondaryValues = new TreeMap<>();
        for (SecondaryParameterSyncService.GameObjectRow gameObject : secondaryGameObjects) {
            if (tagNames != null
                    && !tagNames.isEmpty()
                    && !filteredPrimaryNames.contains(gameObject.name())) {
                continue;
            }
            if (secondaryValues.put(gameObject.name(), getSecondaryParameterValuePresenceByName(gameObject)) != null) {
                throw new IllegalStateException("Duplicate secondary game object name: " + gameObject.name());
            }
        }
        Set<String> parameterNames = new TreeSet<>(getComparisonParameterNames());

        Set<String> gameObjectNames = new TreeSet<>(primaryValues.keySet());
        gameObjectNames.addAll(secondaryValues.keySet());

        return gameObjectNames.stream()
                .map(gameObjectName -> {
                    Map<String, ParameterValuePresence> primary = primaryValues.getOrDefault(gameObjectName, Map.of());
                    Map<String, ParameterValuePresence> secondary = secondaryValues.getOrDefault(gameObjectName, Map.of());
                    List<ParameterComparisonDto> parameters = parameterNames.stream()
                            .map(parameterName -> toParameterComparison(parameterName, primary, secondary))
                            .toList();
                    return new GameObjectComparisonDto(
                            gameObjectName,
                            primaryValues.containsKey(gameObjectName),
                            secondaryValues.containsKey(gameObjectName),
                            parameters
                    );
                })
                .toList();
    }

    public GameObjectComparisonDto getGameObjectComparison(String gameObjectName) {
        GameObjectComparisonDto comparison = getGameObjectComparisons(null).stream()
                .filter(gameObject -> gameObject.name().equals(gameObjectName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("GameObject not found: " + gameObjectName));
        return new GameObjectComparisonDto(
                comparison.name(),
                comparison.primaryPresent(),
                comparison.secondaryPresent(),
                comparison.parameters().stream()
                        .filter(parameter ->
                                parameter.primaryPresent()
                                    || parameter.secondaryPresent()
                        )
                        .toList()
        );
    }

    public List<String> getComparisonParameterNames() {
        Set<String> parameterNames = new TreeSet<>(parameterService.getParameterDtos(false).stream()
                .map(com.wordonline.admin.dto.ParameterDto::name)
                .toList());
        if (hasSecondaryDatabase()) {
            parameterNames.addAll(parameterService.getParameterDtos(true).stream()
                    .map(com.wordonline.admin.dto.ParameterDto::name)
                    .toList());
        }
        return parameterNames.stream().toList();
    }

    private GameObjectDto fromGameObject(GameObject gameObject) {
        try {
            Double magicIdVal = gameObject.getParameterValue("magic_id")
                    .map(ParameterValue::getValue)
                    .orElse(null);
            if (magicIdVal == null) {
                throw new IllegalArgumentException("Magic Not Found");
            }
            long magicId = magicIdVal.longValue();
            Magic magic = magicRepository.findById(magicId)
                    .orElseThrow(() -> new IllegalArgumentException("Magic Not Found"));

            // The magic's own game object shares its name with the magic (magics.name == game_objects.name)
            // and carries the mana_cost parameter directly; there is no longer a card recipe to sum.
            GameObject magicGameObject = gameObjectRepository.findByName(magic.getName())
                    .orElseThrow(() -> new IllegalArgumentException("Magic game object not found: " + magic.getName()));
            int manaCost = magicGameObject.getParameterValue("mana_cost")
                    .map(ParameterValue::getValue)
                    .orElseThrow(() -> new IllegalArgumentException("Mana Cost not Found"))
                    .intValue();

            return GameObjectDto.fromGameObject(gameObject, manaCost);
        } catch (IllegalArgumentException e) {
            return GameObjectDto.fromGameObject(gameObject, null);
        }
    }

    @Transactional
    public void batchUpdateParameters(
            List<SpreadSheetApiController.ParameterUpdateDto> updates,
            boolean secondary,
            boolean syncSecondary
    ) {
        if (secondary) {
            SecondaryParameterSyncService service = secondaryParameterSyncService.orElseThrow();
            for (SpreadSheetApiController.ParameterUpdateDto update : updates) {
                SecondaryParameterSyncService.GameObjectRow gameObject = service.getGameObject(update.gameObjectId());
                service.upsertParameterValue(gameObject.name(), update.parameterName(), update.value());
            }
            return;
        }

        for (SpreadSheetApiController.ParameterUpdateDto update : updates) {
            parameterService.updateParameterValue(
                    update.gameObjectId(),
                    update.parameterName(),
                    update.value(),
                    syncSecondary
            );
        }
    }

    @Transactional
    public void batchUpdateParametersByName(List<SpreadSheetApiController.NamedParameterUpdateDto> updates) {
        log.info("[SpreadSheetService.batchUpdateParametersByName] Process started: updatesCount={}", updates != null ? updates.size() : 0);
        if (updates == null || updates.isEmpty()) {
            log.info("[SpreadSheetService.batchUpdateParametersByName] Empty updates list. Exiting.");
            return;
        }
        for (int i = 0; i < updates.size(); i++) {
            SpreadSheetApiController.NamedParameterUpdateDto update = updates.get(i);
            log.info("  -> Processing item {}/{}: name='{}', param='{}', val={}, db='{}'",
                    i + 1, updates.size(), update.gameObjectName(), update.parameterName(), update.value(), update.db());
            try {
                parameterService.upsertParameterValue(
                        update.gameObjectName(),
                        update.parameterName(),
                        update.value(),
                        "secondary".equalsIgnoreCase(update.db())
                );
                log.info("  -> Item {}/{} processed successfully", i + 1, updates.size());
            } catch (Exception e) {
                log.error("  -> Item {}/{} failed: {}", i + 1, updates.size(), e.getMessage(), e);
                throw e;
            }
        }
        log.info("[SpreadSheetService.batchUpdateParametersByName] All items processed successfully");
    }

    @Transactional
    public String syncToSecondary() {
        SyncResult parameterResult = parameterService.syncParametersToSecondary();
        SyncResult valueResult = parameterService.syncParameterValuesToSecondary();
        return parameterResult.toMessage("Parameters: Deploy -> Dev")
                + "\n\n"
                + valueResult.toMessage("Parameter values: Deploy -> Dev");
    }

    @Transactional
    public String syncToPrimary() {
        SyncResult parameterResult = parameterService.syncParametersToPrimary();
        SyncResult valueResult = parameterService.syncParameterValuesToPrimary();
        return parameterResult.toMessage("Parameters: Dev -> Deploy")
                + "\n\n"
                + valueResult.toMessage("Parameter values: Dev -> Deploy");
    }

    private GameObjectDto fromSecondaryGameObject(SecondaryParameterSyncService.GameObjectRow gameObject) {
        Map<String, Double> parameterValues = gameObject.parameterValues()
                .stream()
                .collect(Collectors.toMap(
                        SecondaryParameterSyncService.ParameterValueRow::parameterName,
                        SecondaryParameterSyncService.ParameterValueRow::value,
                        (current, replacement) -> replacement
                ));

        Double manaCost = parameterValues.get("mana_cost");

        return new GameObjectDto(
                gameObject.id(),
                gameObject.name(),
                List.of(),
                manaCost != null ? manaCost.intValue() : null,
                toInteger(parameterValues.get("hp")),
                toInteger(parameterValues.get("quantity")),
                toFloat(parameterValues.get("attack_range")),
                toInteger(parameterValues.get("damage")),
                toFloat(parameterValues.get("attack_interval")),
                toFloat(parameterValues.get("speed")),
                toFloat(parameterValues.get("mass")),
                toFloat(parameterValues.get("radius")),
                toLong(parameterValues.get("magic_id"))
        );
    }

    private boolean hasAllTags(GameObject gameObject, List<String> tagNames) {
        if (tagNames == null || tagNames.isEmpty()) {
            return true;
        }

        Set<String> gameObjectTagNames = gameObject.getGameObjectTags().stream()
                .map(gameObjectTag -> gameObjectTag.getTag().getName())
                .collect(Collectors.toSet());
        return gameObjectTagNames.containsAll(tagNames);
    }

    private ParameterComparisonDto toParameterComparison(
            String parameterName,
            Map<String, ParameterValuePresence> primary,
            Map<String, ParameterValuePresence> secondary
    ) {
        ParameterValuePresence primaryPresence = primary.get(parameterName);
        ParameterValuePresence secondaryPresence = secondary.get(parameterName);

        return new ParameterComparisonDto(
                parameterName,
                primaryPresence != null,
                secondaryPresence != null,
                primaryPresence != null ? primaryPresence.value() : null,
                secondaryPresence != null ? secondaryPresence.value() : null
        );
    }

    private Map<String, ParameterValuePresence> getParameterValuePresenceByName(GameObject gameObject) {
        Map<String, ParameterValuePresence> values = new LinkedHashMap<>();
        for (ParameterValue value : gameObject.getParameterValues()) {
            String parameterName = value.getParameter().getName();
            if (values.containsKey(parameterName)) {
                throw new IllegalStateException(
                        "Duplicate primary parameter value: " + gameObject.getName() + "." + parameterName
                );
            }
            values.put(parameterName, new ParameterValuePresence(value.getValue()));
        }
        return values;
    }

    private Map<String, ParameterValuePresence> getSecondaryParameterValuePresenceByName(
            SecondaryParameterSyncService.GameObjectRow gameObject
    ) {
        Map<String, ParameterValuePresence> values = new TreeMap<>();
        for (SecondaryParameterSyncService.ParameterValueRow value : gameObject.parameterValues()) {
            if (values.containsKey(value.parameterName())) {
                throw new IllegalStateException(
                        "Duplicate secondary parameter value: " + gameObject.name() + "." + value.parameterName()
                );
            }
            values.put(value.parameterName(), new ParameterValuePresence(value.value()));
        }
        return values;
    }

    private Integer toInteger(Double value) {
        return value != null ? value.intValue() : null;
    }

    private Long toLong(Double value) {
        return value != null ? value.longValue() : null;
    }

    private Float toFloat(Double value) {
        return value != null ? value.floatValue() : null;
    }

    private record ParameterValuePresence(Double value) {
    }
}
