package com.wordonline.admin.config;

import com.wordonline.admin.dto.llm.LlmProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmKeyCipherTest {

    private static final String VECTOR = "v1:AAECAwQFBgcICQoLNPLn4fKoUrn0jGzk9jqoG8X9GnxnKrgGMmqw9Q==";
    private static final byte[] IV = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};

    private final LlmKeyCipher cipher = new LlmKeyCipher("test-secret");

    @Test
    void encryptingWithTheFixedIvReproducesTheContractVector() {
        assertEquals(VECTOR, cipher.encrypt(LlmProvider.JEV, "sk-test-1234", IV));
    }

    @Test
    void decryptingTheContractVectorReturnsThePlaintext() {
        assertEquals("sk-test-1234", cipher.decrypt(LlmProvider.JEV, VECTOR));
    }

    @Test
    void decryptingUnderAnotherProviderFails() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> cipher.decrypt(LlmProvider.OPENROUTER, VECTOR));
        assertFalse(exception.getMessage().contains("sk-test"));
    }

    @Test
    void randomIvRoundTrips() {
        String first = cipher.encrypt(LlmProvider.OPENROUTER, "sk-or-abcdef");
        assertTrue(first.startsWith("v1:"));
        assertEquals("sk-or-abcdef", cipher.decrypt(LlmProvider.OPENROUTER, first));
        assertFalse(first.equals(cipher.encrypt(LlmProvider.OPENROUTER, "sk-or-abcdef")));
    }

    @Test
    void blankSecretIsNotConfiguredAndRefusesToEncrypt() {
        LlmKeyCipher blank = new LlmKeyCipher("  ");
        assertFalse(blank.isConfigured());
        assertThrows(IllegalStateException.class, () -> blank.encrypt(LlmProvider.JEV, "sk-test-1234"));
    }

    @Test
    void hintKeepsAtMostTheLastFourCharacters() {
        assertEquals("…" + "1234", LlmKeyCipher.hint("sk-test-1234"));
        assertEquals("…" + "2345", LlmKeyCipher.hint("12345678".replace("1678", "2345").substring(0, 4) + "2345"));
        assertEquals("…", LlmKeyCipher.hint("short12"));
        assertEquals("…" + "5678", LlmKeyCipher.hint("12345678"));
    }
}
