package com.huanjing.geo.module.system.service;

import cn.hutool.crypto.symmetric.AES;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class PlatformCredentialService {

    private static final String ENC_PREFIX = "ENC:";
    private static final Pattern DATABASE_REF = Pattern.compile("^db://ai-platform-config/(\\d+)$");

    private final AiPlatformConfigMapper platformConfigMapper;

    public PlatformCredentialService(AiPlatformConfigMapper platformConfigMapper) {
        this.platformConfigMapper = platformConfigMapper;
    }

    @Value("${geo.dispatch.api-key-aes-secret:geo-dispatch-key}")
    private String aesSecret;

    private AES aes;

    @PostConstruct
    void init() {
        byte[] raw = aesSecret.getBytes(StandardCharsets.UTF_8);
        byte[] key = new byte[16];
        for (int i = 0; i < key.length; i++) {
            key[i] = i < raw.length ? raw[i] : (byte) ('0' + i);
        }
        this.aes = new AES(key);
    }

    public String encryptForStorage(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return null;
        }
        String value = apiKey.trim();
        if (value.startsWith(ENC_PREFIX)) {
            return value;
        }
        return ENC_PREFIX + aes.encryptHex(value);
    }

    public String decryptIfNeeded(String storedValue) {
        if (!StringUtils.hasText(storedValue)) {
            return null;
        }
        String value = storedValue.trim();
        if (!value.startsWith(ENC_PREFIX)) {
            return value;
        }
        try {
            return aes.decryptStr(value.substring(ENC_PREFIX.length()));
        } catch (Exception ex) {
            log.warn("Failed to decrypt api key value, fallback to raw text");
            return value;
        }
    }

    public String resolveApiKey(String platformCode, String primaryKeyRef, String encryptedApiKey) {
        String byRef = resolveByRef(primaryKeyRef);
        if (StringUtils.hasText(byRef)) {
            return byRef;
        }
        return decryptIfNeeded(encryptedApiKey);
    }

    public String resolveCredential(String credentialRef, String encryptedValue) {
        String byRef = resolveByRef(credentialRef);
        if (StringUtils.hasText(byRef)) {
            return byRef;
        }
        return decryptIfNeeded(encryptedValue);
    }

    /**
     * Resolves only the configured primary source. A configured reference never falls back to the
     * database value, and an encrypted database value must decrypt successfully.
     */
    public String resolvePrimaryCredentialStrict(String primaryKeyRef, String encryptedValue) {
        if (StringUtils.hasText(primaryKeyRef)) {
            return resolveByRef(primaryKeyRef);
        }
        if (!StringUtils.hasText(encryptedValue)) {
            return null;
        }
        return decryptStoredStrict(encryptedValue);
    }

    String resolveByRef(String keyRef) {
        if (!StringUtils.hasText(keyRef)) {
            return null;
        }
        String normalized = keyRef.trim();
        Matcher databaseMatcher = DATABASE_REF.matcher(normalized);
        if (databaseMatcher.matches()) {
            return resolveDatabaseCredential(Long.parseLong(databaseMatcher.group(1)));
        }
        String envKey;
        if (normalized.regionMatches(true, 0, "env://", 0, "env://".length())) {
            envKey = normalized.substring("env://".length());
            if (!envKey.matches("^[A-Za-z_][A-Za-z0-9_]*$")) {
                return null;
            }
        } else {
            envKey = "AI_KEY_REF_" + normalized.toUpperCase(Locale.ROOT).replace('-', '_');
        }
        String value = System.getenv(envKey);
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public static String databaseCredentialRef(Long platformConfigId) {
        if (platformConfigId == null || platformConfigId < 1) {
            throw new IllegalArgumentException("platformConfigId must be positive");
        }
        return "db://ai-platform-config/" + platformConfigId;
    }

    private String resolveDatabaseCredential(Long platformConfigId) {
        if (platformConfigMapper == null) {
            return null;
        }
        AiPlatformConfig config = platformConfigMapper.selectById(platformConfigId);
        return config == null ? null : decryptStoredStrict(config.getApiKey());
    }

    private String decryptStoredStrict(String storedValue) {
        if (!StringUtils.hasText(storedValue)) {
            return null;
        }
        String value = storedValue.trim();
        if (!value.startsWith(ENC_PREFIX)) {
            return value;
        }
        try {
            return aes.decryptStr(value.substring(ENC_PREFIX.length()));
        } catch (Exception ex) {
            log.warn("Failed to decrypt strict primary credential");
            return null;
        }
    }
}
