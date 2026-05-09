package com.huanjing.geo.module.content.service;

import com.huanjing.geo.module.system.entity.SysDictItem;
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

    @Test
    void sensitiveRedactedDictionaryKeyIsRestoredToValue() {
        SysDictItemMapper mapper = mock(SysDictItemMapper.class);
        SysDictItem item = new SysDictItem();
        item.setDictKey("SENSITIVE_REDACTED");
        item.setDictValue("真实项目名");
        item.setEnabled(true);
        when(mapper.selectList(any())).thenReturn(List.of(item));
        ArticleAiDraftPromptFilter filter = new ArticleAiDraftPromptFilter(mapper);

        String filtered = filter.filterOutboundPrompt(
                "请围绕 [SENSITIVE_REDACTED] 输出文章，不要保留 SENSITIVE_REDACTED 占位符",
                null,
                null
        );

        assertEquals("请围绕 真实项目名 输出文章，不要保留 真实项目名 占位符", filtered);
    }
}
