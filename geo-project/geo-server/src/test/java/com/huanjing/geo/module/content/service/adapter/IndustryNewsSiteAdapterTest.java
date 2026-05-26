package com.huanjing.geo.module.content.service.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.util.HttpClientUtil;
import com.huanjing.geo.module.content.distribution.TargetContext;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.system.entity.PublishSite;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IndustryNewsSiteAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void submitsUnifiedIndustrySitePayloadAndHeaders() throws Exception {
        CapturingAdapter adapter = new CapturingAdapter(objectMapper,
                new HttpClientUtil.HttpResult(200, "{\"success\":true,\"data\":{\"id\":42,\"url\":\"https://site.test/a/42\"},\"message\":null}", Map.of()));
        ArticleDraft article = article();
        Project project = project();
        PublishSite site = site();

        SubmitResult result = adapter.submitToTarget(article, "# 标题\n\n阜阳全屋智能正文内容。", new TargetContext.IndustrySiteTarget(site, project));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getPublishedUrl()).isEqualTo("https://site.test/a/42");
        assertThat(result.getPlatformArticleId()).isEqualTo("42");
        assertThat(adapter.capturedHeaders).containsEntry("X-Admin-Token", "token-123");
        JsonNode payload = objectMapper.readTree(adapter.capturedBody);
        assertThat(payload.path("categorySlug").asText()).isEqualTo("region");
        assertThat(payload.path("title").asText()).isEqualTo("阜阳全屋智能市场观察 2026");
        assertThat(payload.path("keywords").get(0).asText()).isEqualTo("阜阳全屋智能");
        assertThat(payload.path("province").asText()).isEqualTo("安徽省");
        assertThat(payload.path("city").asText()).isEqualTo("阜阳市");
        assertThat(payload.path("markdown").asText()).contains("正文内容");
        assertThat(payload.path("meta").path("industry").asText()).isEqualTo("全屋智能");
    }

    @Test
    void treatsTokenFailureAsAuthExpired() {
        CapturingAdapter adapter = new CapturingAdapter(objectMapper,
                new HttpClientUtil.HttpResult(200, "{\"success\":false,\"data\":{},\"message\":\"token invalid\"}", Map.of()));

        SubmitResult result = adapter.submitToTarget(article(), "# 标题\n\n正文", new TargetContext.IndustrySiteTarget(site(), project()));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("token invalid");
        assertThat(result.getFailureKind()).isEqualTo(FailureKind.AUTH_EXPIRED);
    }

    @Test
    void treatsSuccessFalseWithoutAuthSignalAsBusinessFailure() {
        CapturingAdapter adapter = new CapturingAdapter(objectMapper,
                new HttpClientUtil.HttpResult(200, "{\"success\":false,\"data\":{},\"message\":\"category missing\"}", Map.of()));

        SubmitResult result = adapter.submitToTarget(article(), "# 标题\n\n正文", new TargetContext.IndustrySiteTarget(site(), project()));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("category missing");
        assertThat(result.getFailureKind()).isEqualTo(FailureKind.CLIENT_ERROR);
    }

    private ArticleDraft article() {
        ArticleDraft article = new ArticleDraft();
        article.setId(100L);
        article.setProjectId(200L);
        article.setArticleType("industry_article");
        article.setCategory("region");
        article.setTitle("阜阳全屋智能市场观察 2026");
        article.setTagsJson("[\"阜阳全屋智能\",\"阜阳智能家居\"]");
        return article;
    }

    private Project project() {
        Project project = new Project();
        project.setId(200L);
        project.setBrandName("测试品牌");
        project.setProvinceName("安徽省");
        project.setCityName("阜阳市");
        return project;
    }

    private PublishSite site() {
        PublishSite site = new PublishSite();
        site.setId(300L);
        site.setApiEndpoint("https://api.site.test/api/admin/articles");
        site.setRequestHeaderTemplate("{\"X-Admin-Token\":\"token-123\"}");
        site.setIndustryTags("[\"全屋智能\"]");
        return site;
    }

    private static class CapturingAdapter extends IndustryNewsSiteAdapter {
        private final HttpClientUtil.HttpResult response;
        private Map<String, String> capturedHeaders;
        private String capturedBody;

        private CapturingAdapter(ObjectMapper objectMapper, HttpClientUtil.HttpResult response) {
            super(objectMapper);
            this.response = response;
        }

        @Override
        protected HttpClientUtil.HttpResult postJson(String url,
                                                     Map<String, String> headers,
                                                     String body,
                                                     int connectTimeoutMs,
                                                     int requestTimeoutMs) {
            this.capturedHeaders = headers;
            this.capturedBody = body;
            return response;
        }
    }
}
