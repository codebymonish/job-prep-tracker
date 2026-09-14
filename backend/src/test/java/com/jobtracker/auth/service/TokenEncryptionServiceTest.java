package com.jobtracker.auth.service;

import com.jobtracker.auth.config.TokenEncryptionProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.keygen.KeyGenerators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TokenEncryptionServiceTest {

    @Test
    void shouldEncryptAndDecryptSuccessfully() throws Exception {
        String key = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
        TokenEncryptionProperties properties = new TokenEncryptionProperties(key);
        TokenEncryptionService service = new TokenEncryptionService(properties);

        String plaintext = "secret_data";
        String encrypted = service.encrypt(plaintext);

        assertThat(encrypted).isNotEqualTo(plaintext);
        
        String decrypted = service.decrypt(encrypted);
        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void shouldFailOnTamperedCiphertext() throws Exception {
        String key = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
        TokenEncryptionProperties properties = new TokenEncryptionProperties(key);
        TokenEncryptionService service = new TokenEncryptionService(properties);

        String encrypted = service.encrypt("secret_data");
        // Tamper with the base64 string
        String tampered = encrypted.substring(0, encrypted.length() - 2) + "==";

        assertThrows(Exception.class, () -> service.decrypt(tampered));
    }

    @Test
    void shouldThrowWhenKeyLengthIsInvalid() {
        String key = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]);
        TokenEncryptionProperties properties = new TokenEncryptionProperties(key);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> new TokenEncryptionService(properties));
        assertThat(ex.getMessage()).contains("256-bit AES key");
    }

    @Test
    void shouldThrowExceptionWhenEncryptionFails() {
        String key = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
        TokenEncryptionProperties properties = new TokenEncryptionProperties(key);
        TokenEncryptionService service = new TokenEncryptionService(properties);

        // Force an exception by passing null (which throws NullPointerException in doFinal and is wrapped in RuntimeException)
        assertThrows(RuntimeException.class, () -> service.encrypt(null));
    }
}
