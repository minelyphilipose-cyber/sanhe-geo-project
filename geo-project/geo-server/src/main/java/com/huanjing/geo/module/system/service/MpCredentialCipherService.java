package com.huanjing.geo.module.system.service;

import cn.hutool.crypto.symmetric.AES;
import com.huanjing.geo.common.exception.BizException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

/**
 * User-level / multichannel credentials: strict decrypt contract (no silent fallback).
 * Does not modify {@link PlatformCredentialService}.
 */
@Slf4j
@Service
public class MpCredentialCipherService {

    private static final String ENC_PREFIX = "ENC:";

    @Value("${geo.mp.credential.aes-secret:geo-mp-cred-v1}")
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

    /**
     * Store-only helper; produces {@value #ENC_PREFIX}-prefixed ciphertext.
     */
    public String encryptForStorage(String plaintext) {
        if (!StringUtils.hasText(plaintext)) {
            return null;
        }
        String value = plaintext.trim();
        if (value.startsWith(ENC_PREFIX)) {
            return value;
        }
        return ENC_PREFIX + aes.encryptHex(value);
    }

    /**
     * @throws IllegalArgumentException if null or blank
     * @throws IllegalStateException if value is not prefixed with ENC:
     * @throws BizException with code 500 and message exactly {@code credential decrypt failed} if AES decrypt fails
     */
    public String decrypt(String storedValue) {
        if (!StringUtils.hasText(storedValue)) {
            throw new IllegalArgumentException("credential is null or blank");
        }
        String value = storedValue.trim();
        if (!value.startsWith(ENC_PREFIX)) {
            throw new IllegalStateException("credential not encrypted");
        }
        try {
            return aes.decryptStr(value.substring(ENC_PREFIX.length()));
        } catch (Exception ex) {
            log.warn("Mp credential decrypt failed (message omits cipher material)");
            throw new BizException(500, "credential decrypt failed");
        }
    }
}
