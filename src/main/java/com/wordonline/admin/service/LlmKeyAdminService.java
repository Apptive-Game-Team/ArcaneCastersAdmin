package com.wordonline.admin.service;

import com.wordonline.admin.config.LlmKeyCipher;
import com.wordonline.admin.dto.llm.LlmKeyStatusDto;
import com.wordonline.admin.dto.llm.LlmProvider;
import com.wordonline.admin.repository.llm.LlmKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class LlmKeyAdminService {

    private final LlmKeyRepository repository;

    @Transactional(readOnly = true)
    public List<LlmKeyStatusDto> findAll() {
        return repository.findAll();
    }

    public void save(LlmProvider provider, String encryptedKey, String keyHint) {
        repository.upsert(provider.name(), encryptedKey, keyHint);
    }

    public void delete(LlmProvider provider) {
        repository.delete(provider.name());
    }
}
