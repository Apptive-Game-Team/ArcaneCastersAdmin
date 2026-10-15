package com.wordonline.admin.config;

import com.wordonline.admin.dto.llm.LlmProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * LLM API key 를 DB 에 넣기 전에 암호화한다. 형식은 game 서버가 복호화하는 형식과 같다.
 * key = SHA-256(secret), AES-256-GCM, 12바이트 IV, 128비트 tag, AAD = provider 이름,
 * 저장값 = "v1:" + Base64(IV || ciphertext+tag).
 * 평문과 secret 은 예외 메시지와 log 에 넣지 않는다.
 */
@Component
public class LlmKeyCipher {

    static final String VERSION_PREFIX = "v1:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final byte[] keyBytes;
    private final SecureRandom random = new SecureRandom();

    public LlmKeyCipher(@Value("${word-online.llm.key-secret:}") String secret) {
        this.keyBytes = secret == null || secret.isBlank() ? null : sha256(secret);
    }

    public boolean isConfigured() {
        return keyBytes != null;
    }

    public String encrypt(LlmProvider provider, String plaintext) {
        byte[] iv = new byte[IV_LENGTH];
        random.nextBytes(iv);
        return encrypt(provider, plaintext, iv);
    }

    String encrypt(LlmProvider provider, String plaintext, byte[] iv) {
        requireConfigured();
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(provider.name().getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return VERSION_PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("LLM key encryption failed");
        }
    }

    String decrypt(LlmProvider provider, String stored) {
        requireConfigured();
        try {
            if (stored == null || !stored.startsWith(VERSION_PREFIX)) {
                throw new IllegalArgumentException("Unsupported stored key format");
            }
            byte[] payload = Base64.getDecoder().decode(stored.substring(VERSION_PREFIX.length()));
            if (payload.length <= IV_LENGTH) throw new IllegalArgumentException("Stored key is too short");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "AES"),
                    new GCMParameterSpec(TAG_BITS, payload, 0, IV_LENGTH));
            cipher.updateAAD(provider.name().getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(payload, IV_LENGTH, payload.length - IV_LENGTH), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("LLM key decryption failed");
        }
    }

    /** "…" + 끝 4글자. 8자 미만이면 plaintext 를 하나도 싣지 않는다. */
    public static String hint(String plaintext) {
        if (plaintext.length() < 8) return "\u2026";
        return "\u2026" + plaintext.substring(plaintext.length() - 4);
    }

    private void requireConfigured() {
        if (keyBytes == null) throw new IllegalStateException("LLM_KEY_SECRET is not configured");
    }

    private static byte[] sha256(String secret) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
