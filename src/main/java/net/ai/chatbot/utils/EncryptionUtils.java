package net.ai.chatbot.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Utility class for encrypting and decrypting sensitive data using AES-256-GCM
 */
@Component
@Slf4j
public class EncryptionUtils {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 16; // 128 bits
    private static final int GCM_TAG_LENGTH = 128; // 128 bits
    
    private final byte[] encryptionKey;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EncryptionUtils(@Value("${encryption.key}") String encryptionKeyHex) {
        // Convert hex string to bytes
        this.encryptionKey = hexStringToByteArray(encryptionKeyHex);
        if (this.encryptionKey.length != 32) {
            throw new IllegalArgumentException("Encryption key must be 32 bytes (256 bits)");
        }
    }

    /**
     * Encrypted data container
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EncryptedData {
        private String iv;
        private String encryptedData;
        private String authTag;
    }

    /**
     * Encrypt plain text using AES-256-GCM
     * 
     * @param plainText The text to encrypt
     * @return JSON string containing IV, encrypted data, and auth tag
     */
    public String encrypt(String plainText) {
        try {
            // Generate random IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom secureRandom = new SecureRandom();
            secureRandom.nextBytes(iv);

            // Initialize cipher
            SecretKeySpec keySpec = new SecretKeySpec(encryptionKey, "AES");
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmParameterSpec);

            // Encrypt the data
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // The GCM tag is appended to the ciphertext by Java's Cipher
            // We need to separate them for storage
            int encryptedLength = cipherText.length - (GCM_TAG_LENGTH / 8);
            byte[] encrypted = new byte[encryptedLength];
            byte[] authTag = new byte[GCM_TAG_LENGTH / 8];
            
            System.arraycopy(cipherText, 0, encrypted, 0, encryptedLength);
            System.arraycopy(cipherText, encryptedLength, authTag, 0, authTag.length);

            // Create encrypted data object
            EncryptedData encryptedData = new EncryptedData(
                Base64.getEncoder().encodeToString(iv),
                Base64.getEncoder().encodeToString(encrypted),
                Base64.getEncoder().encodeToString(authTag)
            );

            // Return as JSON string
            return objectMapper.writeValueAsString(encryptedData);

        } catch (Exception e) {
            log.error("Encryption failed", e);
            throw new RuntimeException("Failed to encrypt data", e);
        }
    }

    /**
     * Decrypt encrypted data
     * 
     * @param encryptedJson JSON string containing IV, encrypted data, and auth tag
     * @return Decrypted plain text
     */
    public String decrypt(String encryptedJson) {
        try {
            // Parse JSON
            EncryptedData encryptedData = objectMapper.readValue(encryptedJson, EncryptedData.class);

            // Decode Base64 strings
            byte[] iv = Base64.getDecoder().decode(encryptedData.getIv());
            byte[] encrypted = Base64.getDecoder().decode(encryptedData.getEncryptedData());
            byte[] authTag = Base64.getDecoder().decode(encryptedData.getAuthTag());

            // Combine encrypted data and auth tag
            byte[] cipherText = new byte[encrypted.length + authTag.length];
            System.arraycopy(encrypted, 0, cipherText, 0, encrypted.length);
            System.arraycopy(authTag, 0, cipherText, encrypted.length, authTag.length);

            // Initialize cipher for decryption
            SecretKeySpec keySpec = new SecretKeySpec(encryptionKey, "AES");
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmParameterSpec);

            // Decrypt
            byte[] decryptedBytes = cipher.doFinal(cipherText);
            return new String(decryptedBytes, StandardCharsets.UTF_8);

        } catch (JsonProcessingException e) {
            log.error("Failed to parse encrypted data JSON", e);
            throw new RuntimeException("Invalid encrypted data format", e);
        } catch (Exception e) {
            log.error("Decryption failed", e);
            throw new RuntimeException("Failed to decrypt data", e);
        }
    }

    /**
     * Convert hex string to byte array
     */
    private byte[] hexStringToByteArray(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    /**
     * Generate a random 32-byte (256-bit) encryption key in hex format
     * Use this once to generate a key for your environment
     */
    public static String generateEncryptionKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        StringBuilder hexString = new StringBuilder();
        for (byte b : key) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}

