package com.huanjing.geo.module.extension.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

final class HashSupport {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private HashSupport() {
    }

    static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        SECURE_RANDOM.nextBytes(bytes);
        return bytes;
    }

    static String sha256Hex(String value) {
        return sha256Hex(value.getBytes(StandardCharsets.UTF_8));
    }

    static String sha256Hex(byte[] value) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HashAlgorithm.SHA_256.javaName());
            return HexFormat.of().formatHex(digest.digest(value));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    static String saltedSha256Hex(String saltHex, String plaintext) {
        byte[] salt = HexFormat.of().parseHex(saltHex);
        byte[] token = plaintext.getBytes(StandardCharsets.UTF_8);
        byte[] combined = new byte[salt.length + token.length];
        System.arraycopy(salt, 0, combined, 0, salt.length);
        System.arraycopy(token, 0, combined, salt.length, token.length);
        return sha256Hex(combined);
    }

    static boolean constantTimeEqualsHex(String expectedHex, String actualHex) {
        if (expectedHex == null || actualHex == null) {
            return false;
        }
        byte[] expected = expectedHex.getBytes(StandardCharsets.UTF_8);
        byte[] actual = actualHex.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }
}
