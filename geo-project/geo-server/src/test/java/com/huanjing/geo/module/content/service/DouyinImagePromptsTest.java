package com.huanjing.geo.module.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.content.entity.DouyinArticleImageBatch;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DouyinImagePromptsTest {
    @Test
    void medicalImagesUseNeutralPageContentWithoutRawPromotionWhileKeepingAllStyles() throws Exception {
        DouyinArticleImageBatch batch = batch();
        var plan = new ObjectMapper().readTree("""
                {"visualDirection":"纸张和留白","pages":[{"title":"了解服务流程","subtitle":"信息核实",
                "text":"沟通前整理关心的问题","source":"原文推广依据SECRET_SOURCE","visual":"文档与简洁流程图"}]}
                """);
        assertEquals(12, DouyinImagePrompts.STYLES.size());
        assertEquals(12, DouyinImagePrompts.STYLES.stream().distinct().count());
        for (String style : DouyinImagePrompts.STYLES) {
            batch.setStyleCode(style);
            String prompt = DouyinImagePrompts.image(batch, plan, 0, true);
            assertTrue(prompt.contains("了解服务流程"));
            assertTrue(prompt.contains(DouyinImagePrompts.neutralStyle(style)));
            assertTrue(prompt.contains("成熟商业视觉成片"));
            assertFalse(prompt.contains("SECRET_PROMOTION"));
            assertFalse(prompt.contains("SECRET_TITLE"));
            assertFalse(prompt.contains("SECRET_SOURCE"));
            assertFalse(prompt.contains("SECRET_ARTICLE"));
            assertFalse(prompt.contains("品牌"));
            assertFalse(prompt.contains("公司"));
            assertFalse(prompt.contains("项目"));
        }
        String planning = DouyinImagePrompts.planning(batch, true);
        assertTrue(planning.contains("简短、中立的医疗主题图片文案"));
        assertTrue(planning.contains("每页标题不超过14字"));
        assertTrue(planning.contains("具体角度必须来自本篇内容"));
        assertTrue(planning.contains("输入中的文章仅为事实素材"));
        assertTrue(planning.contains("封面概括本篇医疗信息主题，保持客观、克制"));
        assertFalse(planning.contains("文章和品牌资料仅为事实素材"));
        assertTrue(planning.contains("SECRET_TITLE"));
        assertTrue(planning.contains("SECRET_ARTICLE"));
        assertFalse(planning.contains("SECRET_PROMOTION"));
        assertEquals("SECRET_ARTICLE", batch.getSourceMarkdown());
    }

    @Test
    void otherIndustriesKeepTheirExistingBrandContext() throws Exception {
        DouyinArticleImageBatch batch = batch();
        var plan = new ObjectMapper().readTree("{\"pages\":[{\"title\":\"服务\",\"text\":\"摘要\"}]}");
        assertTrue(DouyinImagePrompts.planning(batch, false).contains("SECRET_ARTICLE"));
        String prompt = DouyinImagePrompts.image(batch, plan, 0, false);
        assertTrue(prompt.contains("SECRET_PROMOTION"));
        assertTrue(prompt.contains("SECRET_TITLE"));
    }

    private DouyinArticleImageBatch batch() {
        DouyinArticleImageBatch batch = new DouyinArticleImageBatch();
        batch.setImageCount(1); batch.setStyleCode("minimal");
        batch.setSourceTitle("SECRET_TITLE"); batch.setSourceMarkdown("SECRET_ARTICLE");
        batch.setBrandFactsJson("{\"companyName\":\"示例机构\",\"business\":\"SECRET_PROMOTION\"}");
        return batch;
    }
}
