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
        assertEquals(16, DouyinImagePrompts.STYLES.size());
        assertEquals(16, DouyinImagePrompts.STYLES.stream().distinct().count());
        assertTrue(DouyinImagePrompts.style("colorful_handbook").contains("大面积实色色块"));
        for (String style : DouyinImagePrompts.STYLES) {
            batch.setStyleCode(style);
            String prompt = DouyinImagePrompts.image(batch, plan, 0, true);
            assertTrue(prompt.contains("了解服务流程"));
            assertTrue(prompt.contains(DouyinImagePrompts.neutralStyle(style)));
            assertTrue(prompt.contains("成熟商业视觉成片"));
            assertTrue(prompt.contains("内容中性不等于视觉空白"));
            assertTrue(prompt.contains("占画布约35%—60%的明确主视觉"));
            assertTrue(prompt.contains("不要默认生成米白、浅灰、低对比的空背景"));
            assertTrue(prompt.contains("将其升级为可识别的概念主体"));
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
        assertTrue(planning.contains("与本页信息点对应的清晰视觉主体"));
        assertTrue(planning.contains("不局限于文档和文具"));
        assertTrue(planning.contains("不得只放标题、小图标、细线和大面积空白"));
        assertTrue(planning.contains("普通笔记本、单个平板屏幕或无含义的色块不能作为唯一主体"));
        assertTrue(planning.contains("一种明确主色和一种对比强调色"));
        assertTrue(planning.contains("不要把客观克制理解为低饱和"));
        assertFalse(planning.contains("visual字段只描述纸张、文档、信息图、文具"));
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
