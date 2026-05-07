package com.huanjing.geo.module.extension.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SemverComparatorTest {

    @Test
    void parsesSemanticVersion() {
        assertEquals(10203, SemverComparator.parseToInt("1.2.3"));
    }

    @Test
    void ignoresBuildMetadata() {
        assertEquals(SemverComparator.parseToInt("1.2.3"), SemverComparator.parseToInt("1.2.3+build.456"));
    }

    @Test
    void rejectsPrerelease() {
        assertThrows(IllegalArgumentException.class, () -> SemverComparator.parseToInt("1.2.3-beta.1"));
    }

    @Test
    void rejectsInvalidVersion() {
        assertThrows(IllegalArgumentException.class, () -> SemverComparator.parseToInt("1.2"));
        assertThrows(IllegalArgumentException.class, () -> SemverComparator.parseToInt("abc"));
    }
}
