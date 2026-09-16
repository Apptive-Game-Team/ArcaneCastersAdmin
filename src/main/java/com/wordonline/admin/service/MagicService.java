package com.wordonline.admin.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wordonline.admin.dto.MagicDto;
import com.wordonline.admin.dto.MagicComparisonDto;

import com.wordonline.admin.entity.magic.Magic;
import com.wordonline.admin.repository.magic.MagicRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class MagicService {

    private final MagicRepository magicRepository;
    private final Optional<SecondaryAdminDataService> secondaryAdminDataService;

    public boolean hasSecondaryDatabase() {
        return secondaryAdminDataService.isPresent();
    }

    @Transactional(readOnly = true)
    public List<MagicDto> getAllMagic() {
        return getAllMagic(false);
    }

    @Transactional(readOnly = true)
    public List<MagicDto> getAllMagic(boolean secondary) {
        if (secondary) {
            return secondaryAdminDataService.orElseThrow().getMagics();
        }

        return magicRepository.findAllByOrderByIdAsc()
                .stream()
                .map(MagicDto::new)
                .toList();
    }

    public void removeMagic(long magicId) {
        removeMagic(magicId, false);
    }

    public void removeMagic(long magicId, boolean secondary) {
        if (secondary) {
            secondaryAdminDataService.orElseThrow().deleteMagic(magicId);
            return;
        }
        magicRepository.deleteById(magicId);
    }

    public void updateMagicName(long magicId, String name) {
        updateMagicName(magicId, name, false);
    }

    public void updateMagicName(long magicId, String name, boolean secondary) {
        if (secondary) {
            secondaryAdminDataService.orElseThrow().updateMagicName(magicId, name);
            return;
        }

        magicRepository.findById(magicId)
                .ifPresent(magic -> magic.setName(name));
    }

    public void createMagic(String name) {
        createMagic(name, false);
    }

    public void createMagic(String name, boolean secondary) {
        createMagic(name, "None", "DEFAULT", secondary);
    }

    public void createMagic(String name, String element, String accessType, boolean secondary) {
        if (secondary) {
            secondaryAdminDataService.orElseThrow().createMagic(name, element, accessType);
            return;
        }

        Magic magic = new Magic();
        magic.setName(name);
        magic.setElement(element);
        magic.setAccessType(accessType);
        magicRepository.save(magic);
    }

    public void updateMagicByName(
            String currentName,
            String newName,
            String element,
            String accessType,
            boolean secondary
    ) {
        if (secondary) {
            secondaryAdminDataService.orElseThrow()
                    .updateMagic(currentName, newName, element, accessType);
            return;
        }

        Magic magic = magicRepository.findByName(currentName)
                .orElseThrow(() -> new IllegalArgumentException("Magic not found: " + currentName));
        magic.setName(newName);
        magic.setElement(element);
        magic.setAccessType(accessType);
    }

    public void removeMagic(String name, boolean secondary) {
        if (secondary) {
            secondaryAdminDataService.orElseThrow().deleteMagic(name);
            return;
        }

        Magic magic = magicRepository.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("Magic not found: " + name));
        magicRepository.delete(magic);
    }

    @Transactional(readOnly = true)
    public List<MagicComparisonDto> getMagicComparisons() {
        Map<String, MagicDto> primaryMagicsByName = getAllMagic(false).stream()
                .collect(Collectors.toMap(
                        MagicDto::name,
                        magic -> magic,
                        (current, replacement) -> {
                            throw new IllegalStateException("Duplicate magic name in primary database");
                        },
                        TreeMap::new
                ));
        Map<String, MagicDto> secondaryMagicsByName = secondaryAdminDataService
                .map(service -> service.getMagics().stream()
                        .collect(Collectors.toMap(
                                MagicDto::name,
                                magic -> magic,
                                (current, replacement) -> {
                                    throw new IllegalStateException("Duplicate magic name in secondary database");
                                },
                                TreeMap::new
                        )))
                .orElseGet(TreeMap::new);
        Set<String> names = new TreeSet<>(primaryMagicsByName.keySet());
        names.addAll(secondaryMagicsByName.keySet());

        return names.stream()
                .map(name -> {
                    MagicDto primaryMagic = primaryMagicsByName.get(name);
                    MagicDto secondaryMagic = secondaryMagicsByName.get(name);
                    return new MagicComparisonDto(
                            name,
                            primaryMagic != null,
                            secondaryMagic != null,
                            primaryMagic != null ? primaryMagic.element() : null,
                            secondaryMagic != null ? secondaryMagic.element() : null,
                            primaryMagic != null ? primaryMagic.accessType() : null,
                            secondaryMagic != null ? secondaryMagic.accessType() : null
                    );
                })
                .toList();
    }

    public SyncResult syncToSecondary() {
        return secondaryAdminDataService.orElseThrow().syncMagicsToSecondary(getAllMagic(false));
    }

    public SyncResult syncToPrimary() {
        List<MagicDto> magics = secondaryAdminDataService.orElseThrow().getMagics();
        List<String> changedNames = new java.util.ArrayList<>();
        int created = 0;
        int updated = 0;
        int unchanged = 0;
        Map<String, Magic> existingMagicsByName = magicRepository.findAllBy().stream()
                .collect(Collectors.toMap(
                        Magic::getName,
                        magic -> magic,
                        (current, replacement) -> {
                            throw new IllegalStateException("Duplicate magic name in primary database");
                        }
                ));

        for (MagicDto magic : magics) {
            Magic targetMagic = existingMagicsByName.get(magic.name());
            if (targetMagic == null) {
                targetMagic = new Magic();
                targetMagic.setName(magic.name());
                targetMagic.setElement(magic.element());
                targetMagic.setAccessType(magic.accessType());
                magicRepository.save(targetMagic);
                created++;
                changedNames.add(magic.name());
            } else if (!java.util.Objects.equals(targetMagic.getElement(), magic.element())
                    || !java.util.Objects.equals(targetMagic.getAccessType(), magic.accessType())) {
                targetMagic.setElement(magic.element());
                targetMagic.setAccessType(magic.accessType());
                updated++;
                changedNames.add(magic.name());
            } else {
                unchanged++;
            }
        }

        return new SyncResult(
                created,
                updated,
                unchanged,
                changedNames
        );
    }
}
