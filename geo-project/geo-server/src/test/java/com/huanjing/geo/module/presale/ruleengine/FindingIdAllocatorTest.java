package com.huanjing.geo.module.presale.ruleengine;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FindingIdAllocatorTest {

    @Test
    void allocate_startsAtF001_andIncrements() {
        FindingIdAllocator a = new FindingIdAllocator();
        assertThat(a.next()).isEqualTo("F001");
        assertThat(a.next()).isEqualTo("F002");
        assertThat(a.next()).isEqualTo("F003");
    }

    @Test
    void allocate_formatsTo3DigitsAtLeast() {
        FindingIdAllocator a = new FindingIdAllocator();
        for (int i = 0; i < 9; i++) a.next();   // -> F009
        assertThat(a.next()).isEqualTo("F010");
    }

    @Test
    void eachAllocatorInstance_hasIndependentCounter() {
        FindingIdAllocator a1 = new FindingIdAllocator();
        FindingIdAllocator a2 = new FindingIdAllocator();
        a1.next(); a1.next();
        assertThat(a1.next()).isEqualTo("F003");
        assertThat(a2.next()).isEqualTo("F001");   // 独立计数,互不干扰
    }
}
