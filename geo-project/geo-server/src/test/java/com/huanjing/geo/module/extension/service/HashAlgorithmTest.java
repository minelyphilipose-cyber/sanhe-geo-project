package com.huanjing.geo.module.extension.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HashAlgorithmTest {

    @Test
    void mapsDbValueToJavaAlgorithm() {
        assertEquals("SHA-256", HashAlgorithm.fromDbValue("SHA-256").javaName());
    }

    @Test
    void rejectsUnknownDbValue() {
        assertThrows(IllegalStateException.class, () -> HashAlgorithm.fromDbValue("UNKNOWN"));
    }
}
