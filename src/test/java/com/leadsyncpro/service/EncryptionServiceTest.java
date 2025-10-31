package com.leadsyncpro.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EncryptionServiceTest {

    private static final String TEST_KEY = "super-secret-key";

    private EncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        encryptionService = new EncryptionService(TEST_KEY);
    }

    @Test
    void encryptAndDecryptRoundTripWithCurrentVersion() {
        String plaintext = "Sensitive data";

        String encrypted = encryptionService.encrypt(plaintext);
        String decrypted = encryptionService.decrypt(encrypted);

        assertThat(encrypted).startsWith("v2:");
        assertThat(encrypted).isNotEqualTo(plaintext);
        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void decryptsLegacyCipherText() throws Exception {
        String plaintext = "legacy-value";
        String legacyCipher = legacyEncrypt(plaintext, TEST_KEY);

        String decrypted = encryptionService.decrypt(legacyCipher);

        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void decryptFailsForTamperedPayload() {
        String encrypted = encryptionService.encrypt("top secret");
        String payload = encrypted.substring(3); // remove version prefix
        byte[] combined = Base64.getDecoder().decode(payload);
        combined[combined.length - 1] ^= 0xFF; // flip last byte
        String tampered = "v2:" + Base64.getEncoder().encodeToString(combined);

        assertThrows(IllegalStateException.class, () -> encryptionService.decrypt(tampered));
    }

    @Test
    void decryptFailsForUnsupportedVersion() {
        String payload = Base64.getEncoder().encodeToString("payload".getBytes(StandardCharsets.UTF_8));
        String cipher = "v3:" + payload;

        assertThrows(IllegalStateException.class, () -> encryptionService.decrypt(cipher));
    }

    @Test
    void decryptFailsForInvalidBase64() {
        assertThrows(IllegalStateException.class, () -> encryptionService.decrypt("not_base64"));
    }

    @Test
    void constructorRejectsBlankKeys() {
        assertThrows(IllegalArgumentException.class, () -> new EncryptionService("   "));
    }

    private static String legacyEncrypt(String plaintext, String key) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-1");
        byte[] keyBytes = sha.digest(key.getBytes(StandardCharsets.UTF_8));
        byte[] truncatedKey = Arrays.copyOf(keyBytes, 16);
        SecretKeySpec secretKey = new SecretKeySpec(truncatedKey, "AES");

        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }
}
