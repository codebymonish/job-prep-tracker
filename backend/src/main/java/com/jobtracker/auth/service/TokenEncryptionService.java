package com.jobtracker.auth.service;

import com.jobtracker.auth.config.TokenEncryptionProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class TokenEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_NONCE_LENGTH = 12;

    private final SecretKey secretKey;
    private final SecureRandom secureRandom;

    public TokenEncryptionService(TokenEncryptionProperties properties) {
        String key = properties != null ? properties.key() : null;
        if (key == null || key.isBlank() || "dummy".equals(key)) {
            // Fallback for tests if no key is provided
            this.secretKey = new SecretKeySpec("12345678901234567890123456789012".getBytes(), "AES");
        } else {
            byte[] keyBytes = java.util.Base64.getUrlDecoder().decode(key);
            if (keyBytes.length != 32) {
                throw new IllegalArgumentException("TokenEncryptionProperties.key must be a base64url-encoded 256-bit AES key");
            }
            this.secretKey = new SecretKeySpec(keyBytes, "AES");
        }
        this.secureRandom = new SecureRandom();
    }

    public String encrypt(String plaintext) {
        try {
            byte[] nonce = new byte[GCM_NONCE_LENGTH];
            secureRandom.nextBytes(nonce);
            
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, nonce);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);
            
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes());
            
            ByteBuffer byteBuffer = ByteBuffer.allocate(nonce.length + ciphertext.length);
            byteBuffer.put(nonce);
            byteBuffer.put(ciphertext);
            
            return Base64.getUrlEncoder().withoutPadding().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public String decrypt(String encryptedData) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(encryptedData);
            
            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
            byte[] nonce = new byte[GCM_NONCE_LENGTH];
            byteBuffer.get(nonce);
            
            byte[] ciphertext = new byte[byteBuffer.remaining()];
            byteBuffer.get(ciphertext);
            
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, nonce);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);
            
            byte[] plaintext = cipher.doFinal(ciphertext);
            
            return new String(plaintext);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }
}
