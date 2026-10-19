package com.wordonline.admin.dto.llm;

public record LlmKeyComparisonDto(LlmProvider provider, LlmKeyStatusDto primary, LlmKeyStatusDto secondary) {
    public String getName() {
        return provider.name();
    }

    public String getLabel() {
        return provider.getLabel();
    }

    public String getDefaultModel() {
        return provider.getDefaultModel();
    }

    public boolean primarySet() {
        return primary != null;
    }

    public boolean secondarySet() {
        return secondary != null;
    }
}
