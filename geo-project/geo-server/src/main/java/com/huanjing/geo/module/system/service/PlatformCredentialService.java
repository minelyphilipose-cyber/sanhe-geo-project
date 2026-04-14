package com.huanjing.geo.module.system.service;

import cn.hutool.crypto.symmetric.AES;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Slf4j
@Service
public class PlatformCredentialService {

    private static final String ENC_PREFIX = "ENC:";

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

    private String resolveByRef(String keyRef) {
        if (!StringUtils.hasText(keyRef)) {
            return null;
        }
        String envKey = "AI_KEY_REF_" + keyRef.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        String value = System.getenv(envKey);
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
