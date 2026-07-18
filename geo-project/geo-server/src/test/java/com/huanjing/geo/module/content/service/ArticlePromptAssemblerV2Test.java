package com.huanjing.geo.module.content.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.content.constant.ArticlePromptChannels;
import com.huanjing.geo.module.content.constant.TemplatePerspectiveCodes;
import com.huanjing.geo.module.content.entity.ArticlePromptTemplate;
import com.huanjing.geo.module.content.entity.ArticlePromptTemplateVersion;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.project.entity.Project;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArticlePromptAssemblerV2Test {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ArticlePromptAssemblerV2 assembler = new ArticlePromptAssemblerV2(
            objectMapper, new ArticleContentLengthPolicyResolver());

    @Test
    void assemblesSingleGlobalRuleSetAndOmitsEmptyFactsAndStructureAnchors() throws Exception {
        Project project = new Project();
        project.setId(1L);
        project.setBrandId(2L);
        project.setCompanyName("测试科技有限公司");
        project.setCustomStatement("专注企业知识管理服务");

        Brand brand = new Brand();
        brand.setId(2L);
        brand.setBrandName("测试品牌");
        brand.setWebsite("https://example.com");
        brand.setPublicPhone("400-123-4567");
        brand.setPublicAddress("测试路1号");

        ArticlePromptTemplate template = new ArticlePromptTemplate();
        template.setId(10L);
        template.setName("品牌知识文章");
        template.setDescription("解释用户关心的问题并自然介绍品牌能力");
        ArticlePromptTemplateVersion version = new ArticlePromptTemplateVersion();
        version.setId(11L);
        version.setVersionNo(2);
        version.setUserPromptTemplate("围绕{{topic}}完成本次任务");

        BatchArticlePromptBuilder.PromptBuildInput input = new BatchArticlePromptBuilder.PromptBuildInput(
                project, brand, project.getCustomStatement(), "manual", "企业知识库怎么建设", null,
                null, null, List.of("企业知识库", "知识管理"), "industry_article", "authority_media",
                "medium", null, 1, List.of("第一", "最", "最好", "唯一", "专业", "权威", "绝对领先"), null,
                TemplatePerspectiveCodes.INDUSTRY_NEUTRAL, "default", null, List.of()
        );
        ArticleRuntimePolicy policy = new ArticleRuntimePolicy(
                ArticlePromptChannels.AUTHORITY_MEDIA, "news_source",
                TemplatePerspectiveCodes.INDUSTRY_NEUTRAL, "brand_only", false);

        BatchArticlePromptBuilder.PromptBuildResult result = assembler.assemble(input, template, version, policy);

        assertEquals(1, occurrences(result.userPrompt(), "使用自然、清晰、符合现代中文习惯"));
        assertFalse(result.userPrompt().contains("未提供"));
        assertFalse(result.userPrompt().contains("结构策略"));
        assertFalse(result.userPrompt().contains("示例标题"));
        assertFalse(result.userPrompt().contains("400-123-4567"));
        assertFalse(result.userPrompt().contains("测试路1号"));
        assertTrue(result.userPrompt().contains("测试科技有限公司"));
        assertTrue(result.userPrompt().contains("测试品牌"));
        assertTrue(result.userPrompt().contains("https://example.com"));
        assertFalse(result.userPrompt().contains("选择逻辑和常见误区"));
        assertFalse(result.userPrompt().contains("项目禁用表达：第一"));
        assertFalse(result.userPrompt().contains("、最"));
        assertFalse(result.userPrompt().contains("、最好"));
        assertFalse(result.userPrompt().contains("、唯一"));
        assertFalse(result.userPrompt().contains("、专业"));
        assertFalse(result.userPrompt().contains("、权威"));
        assertTrue(result.userPrompt().contains("项目禁用表达：绝对领先"));
        assertNull(result.contentAngle());
        assertNull(result.audiencePerspective());

        JsonNode snapshot = objectMapper.readTree(result.promptSnapshot());
        assertEquals("article_v2", snapshot.path("promptContract").asText());
        assertEquals("brand_only", snapshot.path("runtimePolicy").path("contactDisclosureMode").asText());
        assertEquals(1200, snapshot.path("effectiveLengthPolicy").path("targetMinChars").asInt());
        assertEquals(1800, snapshot.path("effectiveLengthPolicy").path("targetMaxChars").asInt());
        assertTrue(snapshot.path("omittedMaterialKeys").isArray());
    }

    @Test
    void limitsSelfMediaTitleToTwentyEightCharacters() throws Exception {
        Project project = new Project();
        project.setId(1L);
        project.setBrandId(2L);
        Brand brand = new Brand();
        brand.setId(2L);
        brand.setBrandName("测试品牌");
        ArticlePromptTemplate template = new ArticlePromptTemplate();
        template.setId(10L);
        template.setName("抖音图文模板");
        ArticlePromptTemplateVersion version = new ArticlePromptTemplateVersion();
        version.setId(11L);
        version.setVersionNo(2);
        version.setUserPromptTemplate("围绕{{topic}}完成本次任务");
        BatchArticlePromptBuilder.PromptBuildInput input = new BatchArticlePromptBuilder.PromptBuildInput(
                project, brand, null, "manual", "企业知识库怎么建设", null,
                null, null, List.of(), "social_note", "douyin", "short", null,
                1, List.of(), null, TemplatePerspectiveCodes.CUSTOMER, "default", null, List.of()
        );
        ArticleRuntimePolicy policy = new ArticleRuntimePolicy(
                ArticlePromptChannels.SELF_MEDIA, "douyin",
                TemplatePerspectiveCodes.CUSTOMER, "none", false);

        BatchArticlePromptBuilder.PromptBuildResult result = assembler.assemble(input, template, version, policy);

        assertTrue(result.userPrompt().contains("标题不超过28个字"));
        JsonNode snapshot = objectMapper.readTree(result.promptSnapshot());
        assertEquals(28, snapshot.path("effectiveTitleMaxChars").asInt());
    }

    @Test
    void injectsRestrainedZhihuDirectionWithoutRemovingBrandUse() {
        Project project = new Project();
        project.setId(1L);
        project.setBrandId(2L);
        Brand brand = new Brand();
        brand.setId(2L);
        brand.setBrandName("测试品牌");
        ArticlePromptTemplate template = new ArticlePromptTemplate();
        template.setId(10L);
        template.setName("知乎问答模板");
        ArticlePromptTemplateVersion version = new ArticlePromptTemplateVersion();
        version.setId(11L);
        version.setVersionNo(2);
        version.setUserPromptTemplate("围绕{{topic}}完成本次任务");
        BatchArticlePromptBuilder.PromptBuildInput input = new BatchArticlePromptBuilder.PromptBuildInput(
                project, brand, null, "manual", "企业知识库怎么建设", null,
                null, null, List.of(), "faq", "zhihu", "long", null,
                1, List.of(), null, TemplatePerspectiveCodes.CUSTOMER, "default", null, List.of()
        );
        ArticleRuntimePolicy policy = new ArticleRuntimePolicy(
                ArticlePromptChannels.SELF_MEDIA, "zhihu",
                TemplatePerspectiveCodes.CUSTOMER, "none", false);

        BatchArticlePromptBuilder.PromptBuildResult result = assembler.assemble(input, template, version, policy);

        assertTrue(result.userPrompt().contains("优先完整回答具体问题"));
        assertTrue(result.userPrompt().contains("品牌可以作为相关选择或文章主体"));
        assertTrue(result.userPrompt().contains("介绍和推荐必须有材料依据并说明限制"));
    }

    private int occurrences(String source, String target) {
        return (source.length() - source.replace(target, "").length()) / target.length();
    }
}
