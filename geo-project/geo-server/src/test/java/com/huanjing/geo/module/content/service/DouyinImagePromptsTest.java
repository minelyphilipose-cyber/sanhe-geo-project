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
                {"visualDirection":"青绿和珊瑚，压纹纸","pages":[{"title":"核对信息看哪些字段","subtitle":"逐项对应",
                "text":"核对机构名称与地址是否对应，记录资料中的服务范围。信息有缺项时先标记待核实，不根据宣传语补齐。",
                "source":"原文推广依据SECRET_SOURCE","visual":"字段对应图与编辑排版"}]}
                """);
        assertEquals(16, DouyinImagePrompts.STYLES.size());
        assertEquals(16, DouyinImagePrompts.STYLES.stream().distinct().count());
        assertEquals(10, DouyinImagePrompts.MEDICAL_BEAUTY_AUTO_STYLES.size());
        assertEquals(10, DouyinImagePrompts.MEDICAL_BEAUTY_AUTO_STYLES.stream().distinct().count());
        assertTrue(DouyinImagePrompts.STYLES.containsAll(DouyinImagePrompts.MEDICAL_BEAUTY_AUTO_STYLES));
        assertTrue(DouyinImagePrompts.style("colorful_handbook").contains("以成熟场景插画为主体"));
        for (String style : DouyinImagePrompts.STYLES) {
            batch.setStyleCode(style);
            String prompt = DouyinImagePrompts.image(batch, plan, 0, true);
            assertTrue(prompt.contains("核对信息看哪些字段"));
            assertTrue(prompt.contains(plan.path("pages").get(0).path("text").asText()));
            assertTrue(prompt.contains(DouyinImagePrompts.neutralStyle(style)));
            assertTrue(prompt.contains("成熟商业视觉成片"));
            assertTrue(prompt.contains(DouyinImagePrompts.LAYOUT_DIRECTION));
            assertTrue(prompt.contains(DouyinImagePrompts.VISUAL_DIRECTION));
            assertTrue(prompt.contains(DouyinImagePrompts.MEDICAL_VISUAL_DIRECTION));
            assertTrue(prompt.contains("文字内容保持原样"));
            assertFalse(prompt.contains("SECRET_PROMOTION"));
            assertFalse(prompt.contains("SECRET_TITLE"));
            assertFalse(prompt.contains("SECRET_SOURCE"));
            assertFalse(prompt.contains("SECRET_ARTICLE"));
            assertFalse(prompt.contains("品牌"));
            assertFalse(prompt.contains("公司"));
            String planning = DouyinImagePrompts.planning(batch, true);
            assertTrue(planning.contains("指定风格：" + DouyinImagePrompts.neutralStyle(style)));
            assertFalse(planning.contains("指定风格：" + DouyinImagePrompts.style(style)));
        }
        String planning = DouyinImagePrompts.planning(batch, true);
        assertTrue(planning.contains("中立、具体、有阅读价值的医疗主题图片文案"));
        assertTrue(planning.contains("不必输出subject、layout或套用预设版式"));
        assertTrue(planning.contains("按信息需要灵活调整"));
        assertTrue(planning.contains("视觉联想可以超出原文字面"));
        assertTrue(planning.contains("不设物件黑名单"));
        assertTrue(planning.contains("source仅作依据记录"));
        assertTrue(planning.contains("正文可自然转述"));
        assertTrue(planning.contains("不得补出原文没有的专业结论"));
        assertFalse(planning.contains("full_scene"));
        assertFalse(planning.contains("至少用3种版式"));
        assertTrue(planning.contains("输入中的文章仅为事实素材"));
        assertTrue(planning.contains("封面表达本篇具体阅读价值"));
        assertTrue(planning.contains("visual用自然语言描述创意、画面与排版"));
        assertTrue(planning.contains(DouyinImagePrompts.LAYOUT_DIRECTION));
        assertTrue(planning.contains(DouyinImagePrompts.VISUAL_DIRECTION));
        assertTrue(planning.contains(DouyinImagePrompts.MEDICAL_VISUAL_DIRECTION));
        assertFalse(planning.contains("正文控制在15—35字"));
        assertFalse(planning.contains("优先选择放大镜、打开的窗口或门"));
        assertFalse(planning.contains("文章和品牌资料仅为事实素材"));
        assertTrue(planning.contains("SECRET_TITLE"));
        assertTrue(planning.contains("SECRET_ARTICLE"));
        assertFalse(planning.contains("SECRET_PROMOTION"));
        assertEquals("SECRET_ARTICLE", batch.getSourceMarkdown());
    }

    @Test
    void everyStyleUsesContentDrivenVisualsAcrossIndustriesAndKeepsBrandContext() throws Exception {
        DouyinArticleImageBatch batch = batch();
        var plan = new ObjectMapper().readTree("{\"pages\":[{\"title\":\"服务\",\"text\":\"摘要\"}]}");
        for (String industry : new String[]{"餐饮", "教育", "智能家居", "建筑装修"}) {
            batch.setBrandFactsJson("{\"industry\":\"" + industry + "\",\"companyName\":\"SECRET_PROMOTION\"}");
            for (String style : DouyinImagePrompts.STYLES) {
                batch.setStyleCode(style);
                String planning = DouyinImagePrompts.planning(batch, false);
                String prompt = DouyinImagePrompts.image(batch, plan, 0, false);
                assertTrue(planning.contains("SECRET_ARTICLE"));
                assertTrue(planning.contains("指定风格：" + DouyinImagePrompts.style(style)));
                assertTrue(prompt.contains("风格：" + DouyinImagePrompts.style(style)));
                for (String stage : new String[]{planning, prompt}) {
                    assertTrue(stage.contains(industry));
                    assertTrue(stage.contains("SECRET_PROMOTION"));
                    assertTrue(stage.contains("SECRET_TITLE"));
                    assertTrue(stage.contains(DouyinImagePrompts.LAYOUT_DIRECTION));
                    assertTrue(stage.contains(DouyinImagePrompts.VISUAL_DIRECTION));
                    assertTrue(stage.contains("视觉联想可以超出原文字面"));
                    assertTrue(stage.contains("不必每页换主体或凑齐版式数量"));
                    assertFalse(stage.contains(DouyinImagePrompts.MEDICAL_VISUAL_DIRECTION));
                }
            }
        }
    }

    private DouyinArticleImageBatch batch() {
        DouyinArticleImageBatch batch = new DouyinArticleImageBatch();
        batch.setImageCount(1); batch.setStyleCode("minimal");
        batch.setSourceTitle("SECRET_TITLE"); batch.setSourceMarkdown("SECRET_ARTICLE");
        batch.setBrandFactsJson("{\"companyName\":\"示例机构\",\"business\":\"SECRET_PROMOTION\"}");
        return batch;
    }
}
