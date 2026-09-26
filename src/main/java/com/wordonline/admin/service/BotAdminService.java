package com.wordonline.admin.service;

import com.wordonline.admin.dto.bot.BotAdminDto;
import com.wordonline.admin.dto.bot.BotDeckCardDto;
import com.wordonline.admin.dto.bot.BotDeckForm;
import com.wordonline.admin.dto.bot.BotForm;
import com.wordonline.admin.repository.bot.BotAdminRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
@Transactional
public class BotAdminService {

    private final BotAdminRepository repository;

    @Transactional(readOnly = true)
    public List<BotAdminDto> findAll() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public BotAdminDto find(long userId) {
        requireBotId(userId);
        return repository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Bot not found: " + userId));
    }

    public long create(BotForm form) {
        validate(form);
        long userId = repository.allocateUserId();
        create(userId, form);
        return userId;
    }

    public void validateForm(BotForm form) {
        validate(form);
    }

    public void create(long userId, BotForm form) {
        validate(form);
        requireBotId(userId);
        repository.createUser(userId, form);
        long deckId = repository.createDeck(userId, requireText(form.getDeckName(), "Deck name"));
        repository.selectDeck(userId, deckId);
        repository.createPersona(userId, form);
    }

    public void update(long userId, BotForm form) {
        requireBotId(userId);
        validate(form);
        repository.update(userId, form);
    }

    public void setEnabled(long userId, boolean enabled) {
        requireBotId(userId);
        repository.setEnabled(userId, enabled);
    }

    public void replaceDeck(long userId, BotDeckForm form) {
        requireBotId(userId);
        if (!form.getMagicNames().isEmpty()) {
            validateDeckInputs(form.getMagicNames(), form.getCounts());
            repository.updateSelectedDeckName(userId, requireText(form.getDeckName(), "Deck name"));
            List<BotDeckCardDto> cards = new ArrayList<>();
            for (int i = 0; i < form.getMagicNames().size(); i++) {
                cards.add(new BotDeckCardDto(0, form.getMagicNames().get(i), form.getCounts().get(i)));
            }
            repository.replaceSelectedDeckCardsByName(userId, cards);
            return;
        }
        validateDeckInputs(form.getMagicIds(), form.getCounts());
        repository.replaceSelectedDeckCards(userId, form.getMagicIds(), form.getCounts());
    }

    private void validateDeckInputs(List<?> magics, List<Integer> counts) {
        if (magics.size() != counts.size()) {
            throw new IllegalArgumentException("Every magic requires a count");
        }
        if (magics.stream().distinct().count() != magics.size()) {
            throw new IllegalArgumentException("Duplicate magics are not allowed");
        }
        if (counts.stream().anyMatch(count -> count == null || count < 1)) {
            throw new IllegalArgumentException("Magic count must be positive");
        }
    }

    public void delete(long userId) {
        requireBotId(userId);
        repository.setEnabled(userId, false);
        repository.delete(userId);
    }

    public boolean exists(long userId) {
        return repository.findByUserId(userId).isPresent();
    }

    public void upsert(BotAdminDto source) {
        BotForm form = toForm(source);
        if (exists(source.userId())) {
            update(source.userId(), form);
            repository.updateSelectedDeckName(source.userId(), form.getDeckName());
        } else {
            create(source.userId(), form);
        }
        repository.replaceSelectedDeckCardsByName(source.userId(), source.cards());
    }

    private BotForm toForm(BotAdminDto source) {
        BotForm form = new BotForm();
        form.setName(source.name());
        form.setTier(source.tier());
        form.setThinkingTimeMs(source.thinkingTimeMs());
        form.setReactionIntervalFrames(source.reactionIntervalFrames());
        form.setCounterAggression(source.counterAggression());
        form.setEnabled(source.enabled());
        form.setHospitality(source.hospitality());
        form.setTemperament(source.temperament());
        form.setMmr(source.mmr());
        form.setStatus(source.status());
        form.setDeckName(source.selectedDeckName() == null ? "Bot Deck" : source.selectedDeckName());
        return form;
    }

    private void validate(BotForm form) {
        form.setName(requireText(form.getName(), "Name"));
        if (!List.of("INTRO", "BEGINNER", "INTERMEDIATE", "ADVANCED", "ELITE", "HOSPITALITY").contains(form.getTier())) {
            throw new IllegalArgumentException("Unsupported bot tier");
        }
        // temperament는 emote 선택/빈도만 고른다. 고르지 않으면 WARM으로 둔다.
        if (form.getTemperament() == null || form.getTemperament().isBlank()) {
            form.setTemperament("WARM");
        }
        if (!List.of("WARM", "SMUG", "TIMID", "STOIC").contains(form.getTemperament())) {
            throw new IllegalArgumentException("Unsupported bot temperament");
        }
        if (form.getThinkingTimeMs() < 0 || form.getReactionIntervalFrames() < 1) {
            throw new IllegalArgumentException("Invalid bot timing");
        }
        // 부호가 방향을 고른다. 음수는 접대 봇이 필드에 지는 수를 고르게 한다.
        if (form.getCounterAggression() < -1 || form.getCounterAggression() > 1) {
            throw new IllegalArgumentException("Counter aggression must be between -1 and 1");
        }
        if (!List.of("Online", "OnMatching", "OnPlaying").contains(form.getStatus())) {
            throw new IllegalArgumentException("Unsupported user status");
        }
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }

    private void requireBotId(long userId) {
        if (userId >= 0) throw new IllegalArgumentException("Bot user id must be negative");
    }
}
