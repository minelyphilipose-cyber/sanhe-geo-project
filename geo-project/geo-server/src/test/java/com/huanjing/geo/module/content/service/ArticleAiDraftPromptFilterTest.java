package com.huanjing.geo.module.content.service;

import com.huanjing.geo.module.customer.entity.Brand;
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

    @Test
    void contactInfoIsRedactedUnlessExplicitlyAllowed() {
        SysDictItemMapper mapper = mock(SysDictItemMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of());
        ArticleAiDraftPromptFilter filter = new ArticleAiDraftPromptFilter(mapper);
        Brand brand = new Brand();
        brand.setPublicPhone("13812345678");
        brand.setPublicAddress("北京市朝阳区测试路88号");

        String source = "电话 13812345678，地址 北京市朝阳区测试路88号";

        String redacted = filter.filterOutboundPrompt(source, null, brand, false);
        String allowed = filter.filterOutboundPrompt(source, null, brand, true);

        assertTrue(redacted.contains("[PHONE_REDACTED]"));
        assertTrue(redacted.contains("[ADDRESS_REDACTED]"));
        assertTrue(allowed.contains("13812345678"));
        assertTrue(allowed.contains("北京市朝阳区测试路88号"));
    }

    @Test
    void generatedContentDoesNotExposeRedactionPlaceholders() {
        SysDictItemMapper mapper = mock(SysDictItemMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of());
        ArticleAiDraftPromptFilter filter = new ArticleAiDraftPromptFilter(mapper);

        String filtered = filter.filterGeneratedContent("""
                # 标题

                电话 13812345678
                地址 北京市朝阳区测试路88号
                正文内容
                """, null, null, false);

        assertFalse(filtered.contains("[PHONE_REDACTED]"));
        assertFalse(filtered.contains("[ADDRESS_REDACTED]"));
        assertTrue(filtered.contains("正文内容"));
    }

    @Test
    void generatedContentDoesNotExposeTemplatePlaceholders() {
        SysDictItemMapper mapper = mock(SysDictItemMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of());
        ArticleAiDraftPromptFilter filter = new ArticleAiDraftPromptFilter(mapper);

        String filtered = filter.filterGeneratedContent("""
                # 标题

                如需了解更多信息，可参考 {{contactBlock}}。
                本文从 {{contentAngle}} 展开。
                """, null, null, false);

        assertFalse(filtered.contains("{{contactBlock}}"));
        assertFalse(filtered.contains("{{contentAngle}}"));
        assertTrue(filtered.contains("如需了解更多信息"));
    }

    @Test
    void allowedContactInfoOnlyPreservesBrandPublicContact() {
        SysDictItemMapper mapper = mock(SysDictItemMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of());
        ArticleAiDraftPromptFilter filter = new ArticleAiDraftPromptFilter(mapper);
        Brand brand = new Brand();
        brand.setPublicPhone("13812345678");

        String filtered = filter.filterOutboundPrompt(
                "公开电话 13812345678，其他电话 13912345678",
                null,
                brand,
                true
        );

        assertTrue(filtered.contains("13812345678"));
        assertFalse(filtered.contains("13912345678"));
        assertTrue(filtered.contains("[PHONE_REDACTED]"));
    }

    @Test
    void fullModeOnlyPreservesConfiguredLandlineOrServiceNumber() {
        SysDictItemMapper mapper = mock(SysDictItemMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of());
        ArticleAiDraftPromptFilter filter = new ArticleAiDraftPromptFilter(mapper);
        Brand brand = new Brand();
        brand.setPublicPhone("400-123-4567");

        String filtered = filter.filterOutboundPrompt(
                "公开电话 400-123-4567，其他电话 010-12345678",
                null,
                brand,
                true
        );

        assertTrue(filtered.contains("400-123-4567"));
        assertFalse(filtered.contains("010-12345678"));
        assertTrue(filtered.contains("[PHONE_REDACTED]"));
    }
}
