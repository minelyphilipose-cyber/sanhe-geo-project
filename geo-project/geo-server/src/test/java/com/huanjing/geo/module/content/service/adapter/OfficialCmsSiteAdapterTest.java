package com.huanjing.geo.module.content.service.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.util.HttpClientUtil;
import com.huanjing.geo.module.content.distribution.TargetContext;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.entity.BrandOfficialSite;
import com.huanjing.geo.module.content.entity.MpAccount;
import com.huanjing.geo.module.content.service.render.MarkdownToHtmlRenderer;
import com.huanjing.geo.module.system.entity.PublishSite;
import com.huanjing.geo.module.system.mapper.PublishSiteMapper;
import com.huanjing.geo.module.system.service.MpCredentialCipherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OfficialCmsSiteAdapterTest {

    private final PublishSiteMapper publishSiteMapper = mock(PublishSiteMapper.class);
    private final MpCredentialCipherService cipherService = mock(MpCredentialCipherService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private TestAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TestAdapter(publishSiteMapper, new MarkdownToHtmlRenderer(), cipherService, objectMapper);
        when(cipherService.decrypt("ENC:token")).thenReturn("plain-token");
    }

    @Test
    void supportsPlatform_officialCmsOnly() {
        assertTrue(adapter.supportsPlatform("official_cms"));
        assertFalse(adapter.supportsPlatform("rest_api"));
    }

    @Test
    void submitToTarget_success_returnsSuccess() {
        givenFramework();
        adapter.nextPost = new HttpClientUtil.HttpResult(200, "{\"id\":\"cms-1\",\"url\":\"https://site/a\",\"status\":\"ok\"}", Map.of());
        SubmitResult result = adapter.submitToTarget(article("Title", "qa"), "# body", target());
        assertTrue(result.isSuccess());
        assertEquals("https://site/a", result.getPublishedUrl());
        assertEquals("cms-1", result.getPlatformArticleId());
    }

    @Test
    void submitToTarget_401_authExpired() {
        givenFramework();
        adapter.nextPost = new HttpClientUtil.HttpResult(401, "no", Map.of());
        SubmitResult result = adapter.submitToTarget(article("Title", "qa"), "body", target());
        assertFalse(result.isSuccess());
        assertEquals(FailureKind.AUTH_EXPIRED, result.getFailureKind());
        assertFalse(result.isRetryable());
    }

    @Test
    void submitToTarget_5xx_serverErrorRetryable() {
        givenFramework();
        adapter.nextPost = new HttpClientUtil.HttpResult(503, "down", Map.of());
        SubmitResult result = adapter.submitToTarget(article("Title", "qa"), "body", target());
        assertEquals(FailureKind.SERVER_ERROR, result.getFailureKind());
        assertTrue(result.isRetryable());
    }

    @Test
    void submitToTarget_4xx_clientErrorNotRetryable() {
        givenFramework();
        adapter.nextPost = new HttpClientUtil.HttpResult(422, "bad", Map.of());
        SubmitResult result = adapter.submitToTarget(article("Title", "qa"), "body", target());
        assertEquals(FailureKind.CLIENT_ERROR, result.getFailureKind());
        assertFalse(result.isRetryable());
    }

    @Test
    void submitToTarget_wrongTarget_throwsIae() {
        assertThrows(IllegalArgumentException.class,
                () -> adapter.submitToTarget(article("Title", "qa"), "body",
                        new TargetContext.SelfMediaTarget(new MpAccount(), null, null, null, null, null,
                                "test-request", null)));
    }

    @Test
    void submitToTarget_templateVariables_serializesSpecialCharactersAsValidJson() throws Exception {
        givenFramework();
        adapter.nextPost = new HttpClientUtil.HttpResult(201, "{\"id\":\"cms-2\",\"url\":\"https://site/b\"}", Map.of());
        SubmitResult result = adapter.submitToTarget(article("A \"quoted\" title", "case"), "line1\\line2\n<script>x</script>", target());
        assertTrue(result.isSuccess());
        JsonNode payload = objectMapper.readTree(adapter.lastBody);
        assertEquals("tenant-a", payload.get("site_id").asText());
        assertEquals("A \"quoted\" title", payload.get("title").asText());
        assertTrue(payload.get("content").asText().contains("line1\\line2"));
        assertFalse(adapter.lastBody.contains("<script>"));
    }

    @Test
    void checkAuth_200_success() {
        adapter.nextGet = new HttpClientUtil.HttpResult(200, "ok", Map.of());
        assertTrue(adapter.checkAuth(target()).isSuccess());
    }

    @Test
    void checkAuth_401_failure() {
        adapter.nextGet = new HttpClientUtil.HttpResult(401, "no", Map.of());
        AuthCheckResult result = adapter.checkAuth(target());
        assertFalse(result.isSuccess());
        assertEquals(FailureKind.AUTH_EXPIRED, result.getFailureKind());
        assertEquals("auth_failed", result.getMessage());
    }

    @Test
    void checkAuth_500_failureUnreachable() {
        adapter.nextGet = new HttpClientUtil.HttpResult(500, "error", Map.of());

        AuthCheckResult result = adapter.checkAuth(target());

        assertFalse(result.isSuccess());
        assertEquals(FailureKind.SERVER_ERROR, result.getFailureKind());
        assertTrue(result.getMessage().contains("unreachable"));
        assertFalse(result.getMessage().contains("plain-token"));
    }

    @Test
    void checkAuth_networkException_failureNetworkError() {
        adapter.nextGetException = new IOException("connection refused");

        AuthCheckResult result = adapter.checkAuth(target());

        assertFalse(result.isSuccess());
        assertEquals(FailureKind.NETWORK_ERROR, result.getFailureKind());
        assertEquals("network_error", result.getMessage());
    }

    private void givenFramework() {
        when(publishSiteMapper.selectOne(any())).thenReturn(framework());
    }

    private ArticleDraft article(String title, String articleType) {
        ArticleDraft article = new ArticleDraft();
        article.setTitle(title);
        article.setArticleType(articleType);
        return article;
    }

    private TargetContext.BrandOfficialSiteTarget target() {
        BrandOfficialSite site = new BrandOfficialSite();
        site.setCmsFrameworkCode(OfficialCmsSiteAdapter.FRAMEWORK_CODE_DEFAULT);
        site.setTenantKey("tenant-a");
        site.setApiEndpoint("https://cms.example/api");
        site.setCredentialsCipher("ENC:token");
        return new TargetContext.BrandOfficialSiteTarget(site);
    }

    private PublishSite framework() {
        PublishSite site = new PublishSite();
        site.setSiteName("Official CMS Framework v1");
        site.setIntegrationMethod("official_cms");
        site.setRequestBodyTemplate("{\"site_id\":\"{{tenantKey}}\",\"title\":\"{{title}}\",\"type\":\"{{articleType}}\",\"content\":\"{{content}}\"}");
        return site;
    }

    private static class TestAdapter extends OfficialCmsSiteAdapter {
        private HttpClientUtil.HttpResult nextPost;
        private HttpClientUtil.HttpResult nextGet;
        private IOException nextGetException;
        private String lastBody;

        TestAdapter(PublishSiteMapper publishSiteMapper,
                    MarkdownToHtmlRenderer markdownToHtmlRenderer,
                    MpCredentialCipherService mpCredentialCipherService,
                    ObjectMapper objectMapper) {
            super(publishSiteMapper, markdownToHtmlRenderer, mpCredentialCipherService, objectMapper);
        }

        @Override
        protected HttpClientUtil.HttpResult postJson(String url, Map<String, String> headers, String body, int connectTimeoutMs, int requestTimeoutMs) {
            this.lastBody = body;
            return nextPost;
        }

        @Override
        protected HttpClientUtil.HttpResult get(String url, Map<String, String> headers, int connectTimeoutMs, int requestTimeoutMs) throws IOException {
            if (nextGetException != null) {
                throw nextGetException;
            }
            return nextGet;
        }
    }
}
