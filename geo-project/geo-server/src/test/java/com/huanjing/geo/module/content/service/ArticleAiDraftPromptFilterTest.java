package com.huanjing.geo.module.content.service;

import com.huanjing.geo.module.system.mapper.SysDictItemMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ArticleAiDraftPromptFilterTest {

    @Test
    void bankCardRedactionRequiresLuhnMatch() {
        SysDictItemMapper mapper = mock(SysDictItemMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of());
        ArticleAiDraftPromptFilter filter = new ArticleAiDraftPromptFilter(mapper);

        String filtered = filter.filterOutboundPrompt(
                "order 4111111111111112 card 4111111111111111 sku 9876543210123",
                null,
                null
        );

        assertTrue(filtered.contains("4111111111111112"));
        assertTrue(filtered.contains("9876543210123"));
        assertFalse(filtered.contains("4111111111111111"));
        assertTrue(filtered.contains("[NUMBER_REDACTED]"));
    }
}
