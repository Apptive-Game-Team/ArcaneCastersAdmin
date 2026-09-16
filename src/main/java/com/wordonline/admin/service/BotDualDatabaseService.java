package com.wordonline.admin.service;

import com.wordonline.admin.dto.MagicDto;
import com.wordonline.admin.dto.bot.BotAdminDto;
import com.wordonline.admin.dto.bot.BotComparisonDto;
import com.wordonline.admin.dto.bot.BotDeckForm;
import com.wordonline.admin.dto.bot.BotForm;
import com.wordonline.admin.dto.bot.BotSyncResult;
import com.wordonline.admin.repository.magic.MagicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BotDualDatabaseService {

    public enum Target { PRIMARY, SECONDARY, BOTH }

    private final BotAdminService primary;
    private final ObjectProvider<SecondaryBotAdminService> secondaryProvider;
    private final MagicRepository magicRepository;

    public boolean hasSecondary() {
        return secondaryProvider.getIfAvailable() != null;
    }

    public List<BotComparisonDto> comparisons() {
        Map<Long, BotAdminDto> primaryBots = primary.findAll().stream()
                .collect(Collectors.toMap(BotAdminDto::userId, Function.identity()));
        SecondaryBotAdminService secondary = secondaryProvider.getIfAvailable();
        Map<Long, BotAdminDto> secondaryBots = secondary == null ? Map.of() : secondary.findAll().stream()
                .collect(Collectors.toMap(BotAdminDto::userId, Function.identity()));
        TreeSet<Long> ids = new TreeSet<>(primaryBots.keySet());
        ids.addAll(secondaryBots.keySet());
        return ids.descendingSet().stream()
                .map(id -> new BotComparisonDto(id, primaryBots.get(id), secondaryBots.get(id)))
                .toList();
    }

    public BotComparisonDto comparison(long userId) {
        BotAdminDto primaryBot = primary.findAll().stream().filter(bot -> bot.userId() == userId).findFirst().orElse(null);
        SecondaryBotAdminService secondary = secondaryProvider.getIfAvailable();
        BotAdminDto secondaryBot = secondary == null ? null : secondary.find(userId).orElse(null);
        if (primaryBot == null && secondaryBot == null) throw new IllegalArgumentException("Bot not found: " + userId);
        return new BotComparisonDto(userId, primaryBot, secondaryBot);
    }

    public List<MagicDto> primaryMagics() {
        return magicRepository.findAllByOrderByIdAsc().stream().map(MagicDto::new).toList();
    }

    public List<MagicDto> secondaryMagics() {
        SecondaryBotAdminService secondary = secondaryProvider.getIfAvailable();
        return secondary == null ? List.of() : secondary.getMagics();
    }

    public long create(BotForm form, Target target) {
        primary.validateForm(form);
        requireSecondary(target);
        if (target == Target.SECONDARY) {
            SecondaryBotAdminService secondary = secondary();
            long userId = secondary.allocateUserId();
            secondary.create(userId, form);
            return userId;
        }
        long userId = primary.create(form);
        if (target == Target.BOTH) {
            runSecondaryAfterPrimary("create", () -> secondary().create(userId, form));
        }
        return userId;
    }

    public void update(long userId, BotForm form, Target target) {
        primary.validateForm(form);
        requireSecondary(target);
        if (target != Target.SECONDARY) primary.update(userId, form);
        if (target != Target.PRIMARY) applySecondary(target, "update", () -> secondary().update(userId, form));
    }

    public void setEnabled(long userId, boolean enabled, Target target) {
        requireSecondary(target);
        if (target != Target.SECONDARY) primary.setEnabled(userId, enabled);
        if (target != Target.PRIMARY) applySecondary(target, "enabled update", () -> secondary().setEnabled(userId, enabled));
    }

    public void replaceDeck(long userId, BotDeckForm form, Target target) {
        requireSecondary(target);
        validateDeckForm(form);
        if (target != Target.SECONDARY) primary.replaceDeck(userId, form);
        if (target != Target.PRIMARY) applySecondary(target, "deck update", () -> {
            if (!form.getMagicNames().isEmpty()) {
                secondary().replaceDeckByNames(userId, form.getDeckName(), form.getMagicNames(), form.getCounts());
            } else {
                secondary().replaceDeck(userId, form.getDeckName(), form.getMagicIds(), form.getCounts());
            }
        });
    }

    public void delete(long userId, Target target) {
        requireSecondary(target);
        if (target != Target.SECONDARY) primary.delete(userId);
        if (target != Target.PRIMARY) applySecondary(target, "delete", () -> secondary().delete(userId));
    }

    public BotSyncResult syncToSecondary() {
        SecondaryBotAdminService secondary = secondary();
        int created = 0;
        int updated = 0;
        List<Long> ids = new ArrayList<>();
        for (BotAdminDto bot : primary.findAll()) {
            if (secondary.upsert(bot)) created++; else updated++;
            ids.add(bot.userId());
        }
        return new BotSyncResult(created, updated, ids);
    }

    public BotSyncResult syncToPrimary() {
        int created = 0;
        int updated = 0;
        List<Long> ids = new ArrayList<>();
        for (BotAdminDto bot : secondary().findAll()) {
            boolean existed = primary.exists(bot.userId());
            primary.upsert(bot);
            if (existed) updated++; else created++;
            ids.add(bot.userId());
        }
        return new BotSyncResult(created, updated, ids);
    }

    public Target target(String db) {
        return switch (db == null ? "primary" : db.toLowerCase()) {
            case "secondary" -> Target.SECONDARY;
            case "both" -> Target.BOTH;
            default -> Target.PRIMARY;
        };
    }

    private SecondaryBotAdminService secondary() {
        SecondaryBotAdminService service = secondaryProvider.getIfAvailable();
        if (service == null) throw new IllegalStateException("Dev database is not configured");
        return service;
    }

    private void requireSecondary(Target target) {
        if (target != Target.PRIMARY) secondary();
    }

    private void runSecondaryAfterPrimary(String operation, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Deploy succeeded but Dev " + operation + " failed", exception);
        }
    }

    private void applySecondary(Target target, String operation, Runnable action) {
        if (target == Target.BOTH) runSecondaryAfterPrimary(operation, action); else action.run();
    }

    private void validateDeckForm(BotDeckForm form) {
        List<?> magics = form.getMagicNames().isEmpty() ? form.getMagicIds() : form.getMagicNames();
        if (form.getDeckName() == null || form.getDeckName().isBlank()) {
            throw new IllegalArgumentException("Deck name is required");
        }
        if (magics.size() != form.getCounts().size() || magics.stream().distinct().count() != magics.size()) {
            throw new IllegalArgumentException("Invalid deck magic inputs");
        }
        if (form.getCounts().stream().anyMatch(count -> count == null || count < 1)) {
            throw new IllegalArgumentException("Magic count must be positive");
        }
    }
}
