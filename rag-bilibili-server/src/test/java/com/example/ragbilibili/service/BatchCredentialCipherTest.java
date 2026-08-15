package com.example.ragbilibili.service;

import com.example.ragbilibili.config.BatchImportProperties;
import com.example.ragbilibili.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BatchCredentialCipherTest {

    @Test
    void encryptsAndDecryptsCredentialsWithRandomIv() {
        BatchCredentialCipher cipher = cipherWithKey("0123456789abcdef0123456789abcdef");
        BatchImportCredentials credentials = new BatchImportCredentials("sess,data", "csrf", "buvid3");

        String first = cipher.encrypt(credentials);
        String second = cipher.encrypt(credentials);

        assertThat(first).isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).isEqualTo(credentials);
        assertThat(cipher.decrypt(second)).isEqualTo(credentials);
        assertThat(first).doesNotContain("sess,data");
    }

    @Test
    void rejectsMissingOrInvalidKey() {
        BatchImportProperties missing = new BatchImportProperties();
        BatchCredentialCipher missingCipher = new BatchCredentialCipher(missing);

        BatchImportProperties shortKey = new BatchImportProperties();
        shortKey.setCredentialKey(Base64.getEncoder().encodeToString("short".getBytes(StandardCharsets.UTF_8)));
        BatchCredentialCipher shortKeyCipher = new BatchCredentialCipher(shortKey);

        assertThatThrownBy(() -> missingCipher.encrypt(new BatchImportCredentials("a", "b", "c")))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(2008);
        assertThatThrownBy(() -> shortKeyCipher.encrypt(new BatchImportCredentials("a", "b", "c")))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(2008);
    }

    @Test
    void rejectsCiphertextCreatedWithAnotherKey() {
        String encrypted = cipherWithKey("0123456789abcdef0123456789abcdef")
                .encrypt(new BatchImportCredentials("a", "b", "c"));

        assertThatThrownBy(() -> cipherWithKey("abcdef0123456789abcdef0123456789").decrypt(encrypted))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(2008);
    }

    private BatchCredentialCipher cipherWithKey(String rawKey) {
        BatchImportProperties properties = new BatchImportProperties();
        properties.setCredentialKey(Base64.getEncoder().encodeToString(rawKey.getBytes(StandardCharsets.UTF_8)));
        return new BatchCredentialCipher(properties);
    }
}
