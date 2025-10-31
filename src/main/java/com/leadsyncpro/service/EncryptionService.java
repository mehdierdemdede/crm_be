package com.leadsyncpro.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Service
public class EncryptionService {

    private static final String AES = "AES";
    private static final String AES_GCM_NO_PADDING = "AES/GCM/NoPadding";
    private static final String AES_ECB_PKCS5_PADDING = "AES/ECB/PKCS5Padding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final String CURRENT_VERSION = "v2";
    private static final String VERSION_DELIMITER = ":";
    private static final String CURRENT_VERSION_PREFIX = CURRENT_VERSION + VERSION_DELIMITER;

    private final SecretKeySpec secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public EncryptionService(@Value("${app.encryption.key}") String encryptionKey) {
        if (encryptionKey == null || encryptionKey.isBlank()) {
            throw new IllegalArgumentException("Encryption key must not be null or blank");
        }
        this.secretKey = deriveAesKey(encryptionKey);
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }

        byte[] iv = new byte[GCM_IV_LENGTH];
        secureRandom.nextBytes(iv);

        byte[] cipherBytes = doAesGcm(Cipher.ENCRYPT_MODE, iv, plaintext.getBytes(StandardCharsets.UTF_8));
        byte[] payload = new byte[iv.length + cipherBytes.length];
        System.arraycopy(iv, 0, payload, 0, iv.length);
        System.arraycopy(cipherBytes, 0, payload, iv.length, cipherBytes.length);

        String encodedPayload = Base64.getEncoder().encodeToString(payload);
        return CURRENT_VERSION_PREFIX + encodedPayload;
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null) {
            return null;
        }

        if (ciphertext.startsWith(CURRENT_VERSION_PREFIX)) {
            return decryptCurrentVersion(ciphertext);
        }

        int versionDelimiterIndex = ciphertext.indexOf(VERSION_DELIMITER);
        if (versionDelimiterIndex > 0 && ciphertext.startsWith("v")) {
            throw new IllegalStateException("Unsupported encryption version: " + ciphertext.substring(0, versionDelimiterIndex));
        }

        return decryptLegacy(ciphertext);
    }

    private String decryptCurrentVersion(String ciphertext) {
        String encodedPayload = ciphertext.substring(CURRENT_VERSION_PREFIX.length());
        byte[] payload = decodeBase64(encodedPayload);

        if (payload.length <= GCM_IV_LENGTH) {
            throw new IllegalStateException("Encrypted payload is too short");
        }

        byte[] iv = Arrays.copyOfRange(payload, 0, GCM_IV_LENGTH);
        byte[] cipherBytes = Arrays.copyOfRange(payload, GCM_IV_LENGTH, payload.length);

        byte[] plainBytes = doAesGcm(Cipher.DECRYPT_MODE, iv, cipherBytes);
        return new String(plainBytes, StandardCharsets.UTF_8);
    }

    private String decryptLegacy(String ciphertext) {
        Cipher cipher = getCipher(AES_ECB_PKCS5_PADDING);
        try {
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decoded = decodeBase64(ciphertext);
            byte[] original = cipher.doFinal(decoded);
            return new String(original, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to decrypt legacy ciphertext", e);
        }
    }

    private byte[] doAesGcm(int mode, byte[] iv, byte[] input) {
        Cipher cipher = getCipher(AES_GCM_NO_PADDING);
        try {
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(mode, secretKey, spec);
            return cipher.doFinal(input);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to process AES-GCM", e);
        }
    }

    private Cipher getCipher(String transformation) {
        try {
            return Cipher.getInstance(transformation);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cipher algorithm not available", e);
        }
    }

    private byte[] decodeBase64(String value) {
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Encrypted payload is not valid Base64", e);
        }
    }

    private static SecretKeySpec deriveAesKey(String keyValue) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-1");
            byte[] keyBytes = sha.digest(keyValue.getBytes(StandardCharsets.UTF_8));
            byte[] truncatedKey = Arrays.copyOf(keyBytes, 16); // 128-bit key
            return new SecretKeySpec(truncatedKey, AES);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to derive encryption key", e);
        }
    }
}