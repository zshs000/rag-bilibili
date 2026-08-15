package com.example.ragbilibili.service;

import com.example.ragbilibili.config.BatchImportProperties;
import com.example.ragbilibili.exception.BusinessException;
import com.example.ragbilibili.exception.ErrorCode;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Service
public class BatchCredentialCipher {
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final BatchImportProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public BatchCredentialCipher(BatchImportProperties properties) {
        this.properties = properties;
    }

    public String encrypt(BatchImportCredentials credentials) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(serialize(credentials).getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw configError();
        }
    }

    public BatchImportCredentials decrypt(String ciphertext) {
        try {
            byte[] payload = Base64.getDecoder().decode(ciphertext);
            if (payload.length <= IV_LENGTH) {
                throw new GeneralSecurityException("invalid payload");
            }
            byte[] iv = Arrays.copyOfRange(payload, 0, IV_LENGTH);
            byte[] encrypted = Arrays.copyOfRange(payload, IV_LENGTH, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return deserialize(new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw configError();
        }
    }

    private SecretKeySpec key() {
        String encodedKey = properties.getCredentialKey();
        if (encodedKey == null || encodedKey.isBlank()) {
            throw configError();
        }
        byte[] rawKey;
        try {
            rawKey = Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException e) {
            throw configError();
        }
        if (rawKey.length != 32) {
            throw configError();
        }
        return new SecretKeySpec(rawKey, "AES");
    }

    private String serialize(BatchImportCredentials credentials) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encode(encoder, credentials.sessdata()) + "."
                + encode(encoder, credentials.biliJct()) + "."
                + encode(encoder, credentials.buvid3());
    }

    private BatchImportCredentials deserialize(String serialized) {
        String[] fields = serialized.split("\\.", -1);
        if (fields.length != 3) {
            throw configError();
        }
        Base64.Decoder decoder = Base64.getUrlDecoder();
        return new BatchImportCredentials(decode(decoder, fields[0]), decode(decoder, fields[1]), decode(decoder, fields[2]));
    }

    private String encode(Base64.Encoder encoder, String value) {
        return encoder.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decode(Base64.Decoder decoder, String value) {
        return new String(decoder.decode(value), StandardCharsets.UTF_8);
    }

    private BusinessException configError() {
        return new BusinessException(ErrorCode.VIDEO_IMPORT_CREDENTIAL_CONFIG_ERROR);
    }
}
