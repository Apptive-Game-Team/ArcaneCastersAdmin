package com.wordonline.admin.dto.llm;

public enum LlmProvider {
    JEV("jev-ai.pro", "jev-latest"),
    OPENROUTER("OpenRouter", "openrouter/auto");

    private final String label;
    private final String defaultModel;

    LlmProvider(String label, String defaultModel) {
        this.label = label;
        this.defaultModel = defaultModel;
    }

    public String getLabel() {
        return label;
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public static LlmProvider from(String value) {
        if (value == null) throw new IllegalArgumentException("Unsupported LLM provider");
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported LLM provider");
        }
    }
}
