package com.huanjing.geo.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseTextTest {

    @Test
    void truncatesByUnicodeCharacterWithoutSplittingSurrogatePairs() {
        assertThat(DatabaseText.truncate("公众号😀发布异常", 4)).isEqualTo("公众号😀");
    }
}
