package com.wordonline.admin.service;

import com.wordonline.admin.config.LlmKeyCipher;
import com.wordonline.admin.dto.llm.LlmKeyComparisonDto;
import com.wordonline.admin.dto.llm.LlmKeyStatusDto;
import com.wordonline.admin.dto.llm.LlmProvider;
import com.wordonline.admin.service.BotDualDatabaseService.Target;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LlmKeyDualDatabaseService {

    private final LlmKeyAdminService primary;
    private final ObjectProvider<SecondaryLlmKeyAdminService> secondaryProvider;
    private final LlmKeyCipher cipher;

    public boolean hasSecondary() {
        return secondaryProvider.getIfAvailable() != null;
    }

    public boolean cipherConfigured() {
        return cipher.isConfigured();
    }

    public List<LlmKeyComparisonDto> comparisons() {
        Map<String, LlmKeyStatusDto> primaryKeys = primary.findAll().stream()
                .collect(Collectors.toMap(LlmKeyStatusDto::provider, Function.identity()));
        SecondaryLlmKeyAdminService secondary = secondaryProvider.getIfAvailable();
        Map<String, LlmKeyStatusDto> secondaryKeys = secondary == null ? Map.of() : secondary.findAll().stream()
                .collect(Collectors.toMap(LlmKeyStatusDto::provider, Function.identity()));
        return Arrays.stream(LlmProvider.values())
                .map(provider -> new LlmKeyComparisonDto(provider,
                        primaryKeys.get(provider.name()), secondaryKeys.get(provider.name())))
                .toList();
    }

    /** 암호화는 한 번 하고, 같은 값을 저장 대상 DB 에 모두 넣는다. 평문은 이 메서드 밖으로 나가지 않는다. */
    public void save(LlmProvider provider, String apiKey, Target target) {
        if (!cipher.isConfigured()) throw new IllegalStateException("LLM_KEY_SECRET is not configured");
        String trimmed = apiKey == null ? "" : apiKey.trim();
        if (trimmed.isEmpty()) throw new IllegalArgumentException("API key is required");
        requireSecondary(target);
        String encrypted = cipher.encrypt(provider, trimmed);
        String hint = LlmKeyCipher.hint(trimmed);
        if (target != Target.SECONDARY) primary.save(provider, encrypted, hint);
        if (target != Target.PRIMARY) applySecondary(target, "save", () -> secondary().save(provider, encrypted, hint));
    }

    public void delete(LlmProvider provider, Target target) {
        requireSecondary(target);
        if (target != Target.SECONDARY) primary.delete(provider);
        if (target != Target.PRIMARY) applySecondary(target, "delete", () -> secondary().delete(provider));
    }

    private SecondaryLlmKeyAdminService secondary() {
        SecondaryLlmKeyAdminService service = secondaryProvider.getIfAvailable();
        if (service == null) throw new IllegalStateException("Dev database is not configured");
        return service;
    }

    private void requireSecondary(Target target) {
        if (target != Target.PRIMARY) secondary();
    }

    private void applySecondary(Target target, String operation, Runnable action) {
        if (target != Target.BOTH) {
            action.run();
            return;
        }
        try {
            action.run();
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Deploy succeeded but Dev " + operation + " failed");
        }
    }
}
