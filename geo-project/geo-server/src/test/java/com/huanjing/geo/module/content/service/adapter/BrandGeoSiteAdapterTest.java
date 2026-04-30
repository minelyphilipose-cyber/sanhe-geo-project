package com.huanjing.geo.module.content.service.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.util.HttpClientUtil;
import com.huanjing.geo.module.content.config.BrandGeoSiteProperties;
import com.huanjing.geo.module.content.distribution.TargetContext;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BrandGeoSiteAdapterTest {

    private ObjectMapper objectMapper;
    private TestAdapter adapter;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        BrandGeoSiteProperties props = new BrandGeoSiteProperties();
        props.setEndpoint("https://owned.example/api/v1/content");
        adapter = new TestAdapter(objectMapper, props);
    }

    @Test
    void submitToTarget_success_returnsSubmitResultWithUrl() {
        adapter.nextResponse = response(200, "{\"code\":200,\"message\":\"ok\",\"data\":{\"id\":12345}}");

        SubmitResult result = adapter.submitToTarget(article("Title", "industry_article"), "markdown", target("ok"));

        assertTrue(result.isSuccess());
        assertEquals("12345", result.getPlatformArticleId());
        assertEquals("https://www.ok.com/knowledge/detail/12345", result.getPublishedUrl());
    }

    @Test
    void submitToTarget_articleTypeFAQ_mapsToQuestion() throws Exception {
        adapter.nextResponse = response(200, "{\"code\":200,\"data\":{\"id\":1}}");

        adapter.submitToTarget(article("Title", "FAQ"), "markdown", target("ok"));

        JsonNode payload = objectMapper.readTree(adapter.lastBody);
        assertEquals("question", payload.get("articleType").asText());
    }

    @Test
    void submitToTarget_articleTypeOther_mapsToKnowledge() throws Exception {
        adapter.nextResponse = response(200, "{\"code\":200,\"data\":{\"id\":1}}");

        adapter.submitToTarget(article("Title", "stage_advice"), "markdown", target("ok"));

        JsonNode payload = objectMapper.readTree(adapter.lastBody);
        assertEquals("knowledge", payload.get("articleType").asText());
    }

    @Test
    void submitToTarget_specialCharsInTitle_serializesValidJson() throws Exception {
        adapter.nextResponse = response(200, "{\"code\":200,\"data\":{\"id\":1}}");

        adapter.submitToTarget(article("A \"quoted\" title", "faq"), "line1\\line2\nbody", target("ok"));

        JsonNode payload = objectMapper.readTree(adapter.lastBody);
        assertEquals("A \"quoted\" title", payload.get("title").asText());
        assertEquals("line1\\line2\nbody", payload.get("content").asText());
    }

    @Test
    void submitToTarget_http400_returnsClientErrorNotRetryable() {
        adapter.nextResponse = response(400, "{\"code\":400}");

        SubmitResult result = adapter.submitToTarget(article("Title", "faq"), "body", target("bad"));

        assertFalse(result.isSuccess());
        assertEquals(FailureKind.CLIENT_ERROR, result.getFailureKind());
        assertFalse(result.isRetryable());
    }

    @Test
    void submitToTarget_http429_returnsServerErrorRetryable() {
        adapter.nextResponse = response(429, "{\"code\":429}");

        SubmitResult result = adapter.submitToTarget(article("Title", "faq"), "body", target("ok"));

        assertFalse(result.isSuccess());
        assertEquals(FailureKind.SERVER_ERROR, result.getFailureKind());
        assertTrue(result.isRetryable());
    }

    @Test
    void submitToTarget_http500_returnsServerErrorRetryable() {
        adapter.nextResponse = response(500, "{\"code\":500}");

        SubmitResult result = adapter.submitToTarget(article("Title", "faq"), "body", target("fail"));

        assertEquals(FailureKind.SERVER_ERROR, result.getFailureKind());
        assertTrue(result.isRetryable());
    }

    @Test
    void submitToTarget_networkException_returnsServerErrorRetryable() {
        adapter.nextException = new IOException("connection refused");

        SubmitResult result = adapter.submitToTarget(article("Title", "faq"), "body", target("ok"));

        assertEquals(FailureKind.SERVER_ERROR, result.getFailureKind());
        assertTrue(result.isRetryable());
        assertNull(result.getResponseBody());
    }

    @Test
    void submitToTarget_bodyCodeNon200_returnsClientErrorNotRetryable() {
        adapter.nextResponse = response(200, "{\"code\":1001,\"message\":\"siteCode not found\"}");

        SubmitResult result = adapter.submitToTarget(article("Title", "faq"), "body", target("biz-fail"));

        assertEquals(FailureKind.CLIENT_ERROR, result.getFailureKind());
        assertFalse(result.isRetryable());
    }

    @Test
    void submitToTarget_missingDataId_returnsServerErrorRetryable() {
        adapter.nextResponse = response(200, "{\"code\":200,\"data\":{}}");

        SubmitResult result = adapter.submitToTarget(article("Title", "faq"), "body", target("no-id"));

        assertEquals(FailureKind.SERVER_ERROR, result.getFailureKind());
        assertEquals("response data.id missing or invalid", result.getErrorMessage());
        assertTrue(result.isRetryable());
    }

    @Test
    void submitToTarget_invalidResponseBody_returnsUnknown() {
        adapter.nextResponse = response(200, "{not-json");

        SubmitResult result = adapter.submitToTarget(article("Title", "faq"), "body", target("ok"));

        assertEquals(FailureKind.UNKNOWN, result.getFailureKind());
        assertFalse(result.isRetryable());
    }

    private ArticleDraft article(String title, String articleType) {
        ArticleDraft article = new ArticleDraft();
        article.setTitle(title);
        article.setArticleType(articleType);
        return article;
    }

    private TargetContext.BrandGeoSiteTarget target(String siteCode) {
        return new TargetContext.BrandGeoSiteTarget(30L, siteCode);
    }

    private HttpClientUtil.HttpResult response(int status, String body) {
        return new HttpClientUtil.HttpResult(status, body, Map.of());
    }

    private static class TestAdapter extends BrandGeoSiteAdapter {
        private HttpClientUtil.HttpResult nextResponse;
        private Exception nextException;
        private String lastBody;

        TestAdapter(ObjectMapper objectMapper, BrandGeoSiteProperties props) {
            super(objectMapper, props);
        }

        @Override
        protected HttpClientUtil.HttpResult postJson(String url,
                                                     Map<String, String> headers,
                                                     String body,
                                                     int connectTimeoutMs,
                                                     int requestTimeoutMs) throws Exception {
            lastBody = body;
            if (nextException != null) {
                throw nextException;
            }
            return nextResponse;
        }
    }
}
