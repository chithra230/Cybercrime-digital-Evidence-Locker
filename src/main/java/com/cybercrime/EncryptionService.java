package com.cybercrime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class EncryptionService {

    private static final String AES = "AES";
    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;

    private final byte[] secretKey;

    public EncryptionService(
            @Value("${cybercrime.encryption.key-base64}") String base64Key) {

        try {
            this.secretKey = Base64.getDecoder().decode(base64Key);

            if (this.secretKey.length != 32) {
                throw new IllegalArgumentException(
                        "AES-256 key must be exactly 32 bytes."
                );
            }

        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid AES-256 Base64 encryption key in application.properties.",
                    e
            );
        }
    }

    /**
     * Encrypts a byte array using AES-256-GCM.
     * The returned data contains:
     * IV + encrypted data + authentication tag
     */
    public byte[] encrypt(byte[] plainData) throws Exception {

        byte[] iv = new byte[IV_LENGTH];

        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(iv);

        SecretKeySpec keySpec =
                new SecretKeySpec(secretKey, AES);

        GCMParameterSpec gcmSpec =
                new GCMParameterSpec(GCM_TAG_LENGTH * 1, iv);

        Cipher cipher =
                Cipher.getInstance(AES_GCM);

        cipher.init(
                Cipher.ENCRYPT_MODE,
                keySpec,
                gcmSpec
        );

        byte[] encryptedData =
                cipher.doFinal(plainData);

        byte[] result =
                new byte[iv.length + encryptedData.length];

        System.arraycopy(
                iv,
                0,
                result,
                0,
                iv.length
        );

        System.arraycopy(
                encryptedData,
                0,
                result,
                iv.length,
                encryptedData.length
        );

        return result;
    }

    /**
     * Decrypts data encrypted by the encrypt() method.
     */
    public byte[] decrypt(byte[] encryptedData) throws Exception {

        if (encryptedData == null ||
                encryptedData.length <= IV_LENGTH) {

            throw new IllegalArgumentException(
                    "Invalid encrypted data."
            );
        }

        byte[] iv =
                new byte[IV_LENGTH];

        System.arraycopy(
                encryptedData,
                0,
                iv,
                0,
                IV_LENGTH
        );

        byte[] actualEncryptedData =
                new byte[encryptedData.length - IV_LENGTH];

        System.arraycopy(
                encryptedData,
                IV_LENGTH,
                actualEncryptedData,
                0,
                actualEncryptedData.length
        );

        SecretKeySpec keySpec =
                new SecretKeySpec(secretKey, AES);

        GCMParameterSpec gcmSpec =
                new GCMParameterSpec(
                        GCM_TAG_LENGTH,
                        iv
                );

        Cipher cipher =
                Cipher.getInstance(AES_GCM);

        cipher.init(
                Cipher.DECRYPT_MODE,
                keySpec,
                gcmSpec
        );

        return cipher.doFinal(actualEncryptedData);
    }

    /**
     * Encrypts a file and saves it to the encrypted path.
     */
    public void encryptFile(
            Path inputFile,
            Path encryptedFile) throws Exception {

        byte[] plainData =
                Files.readAllBytes(inputFile);

        byte[] encryptedData =
                encrypt(plainData);

        Files.createDirectories(
                encryptedFile.getParent()
        );

        Files.write(
                encryptedFile,
                encryptedData
        );
    }

    /**
     * Decrypts an encrypted file and returns its bytes.
     */
    public byte[] decryptFile(
            Path encryptedFile) throws Exception {

        if (!Files.exists(encryptedFile)) {
            throw new IOException(
                    "Encrypted evidence file not found."
            );
        }

        byte[] encryptedData =
                Files.readAllBytes(encryptedFile);

        return decrypt(encryptedData);
    }

    /**
     * Encrypts text.
     */
    public String encryptText(String text)
            throws Exception {

        byte[] encrypted =
                encrypt(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        return Base64.getEncoder()
                .encodeToString(encrypted);
    }

    /**
     * Decrypts text.
     */
    public String decryptText(String encryptedText)
            throws Exception {

        byte[] encrypted =
                Base64.getDecoder()
                        .decode(encryptedText);

        byte[] decrypted =
                decrypt(encrypted);

        return new String(
                decrypted,
                java.nio.charset.StandardCharsets.UTF_8
        );
    }
}