package com.huanjing.geo.module.content.authoritymedia;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.content.distribution.TargetContext;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.entity.AuthorityMediaOrder;
import com.huanjing.geo.module.content.entity.AuthorityMediaResource;
import com.huanjing.geo.module.content.entity.DistributionTask;
import com.huanjing.geo.module.content.mapper.AuthorityMediaOrderMapper;
import com.huanjing.geo.module.content.mapper.AuthorityMediaResourceMapper;
import com.huanjing.geo.module.content.service.adapter.SubmitResult;
import com.huanjing.geo.module.project.entity.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorityMediaDistributionAdapterTest {

    @Mock
    private MeititejiaClient client;
    @Mock
    private AuthorityMediaResourceSyncService resourceSyncService;
    @Mock
    private AuthorityMediaResourceMapper resourceMapper;
    @Mock
    private AuthorityMediaOrderMapper orderMapper;
    @Mock
    private AuthorityMediaPreviewTokenService previewTokenService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MeititejiaProperties properties;
    private AuthorityMediaDistributionAdapter adapter;

    @BeforeEach
    void setUp() {
        properties = new MeititejiaProperties();
        properties.setBalanceSafetyFactor(new BigDecimal("1.1"));
        adapter = new AuthorityMediaDistributionAdapter(client, resourceSyncService, resourceMapper, orderMapper,
                previewTokenService, properties, objectMapper);
    }

    @Test
    void submitNewsMedia_createsStableOrderNoAndSanitizedRequestPayload() throws Exception {
        AuthorityMediaResource resource = resource();
        when(resourceMapper.selectById(11L)).thenReturn(resource);
        when(resourceSyncService.refreshNewsMediaResourceIfStale(11L))
                .thenReturn(new AuthorityMediaResourceSyncService.RefreshResult(11L, false, false, "fresh"));
        when(client.userInfo()).thenReturn(objectMapper.readTree("{\"code\":200,\"data\":{\"money\":\"1000\"}}"));
        doAnswer(invocation -> {
            AuthorityMediaOrder order = invocation.getArgument(0);
            order.setId(501L);
            return 1;
        }).when(orderMapper).insert(any(AuthorityMediaOrder.class));
        when(previewTokenService.issuePreviewUrl(any(), any(), eq("https://preview.example/a")))
                .thenReturn("https://preview.example/a/api/public/authority-media/previews/token");
        when(client.buildAuditPayload(any())).thenReturn(auditPayload());
        when(client.createNewsMediaOrder(any())).thenReturn(objectMapper.readTree("{\"code\":200,\"msg\":\"success\",\"data\":{}}"));

        SubmitResult result = adapter.submitNewsMedia(
                article(),
                project(),
                task(),
                99L,
                new TargetContext.AuthorityMediaTarget(11L, new BigDecimal("120"), "https://preview.example/a", null, "remark"),
                "article body"
        );

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getPlatformArticleId()).isNull();
        verify(orderMapper).assignExternalNoIfAbsent(501L, "AM-501");
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(orderMapper).updateSubmissionResult(eq(501L), eq("submitted"), any(), eq(0), eq("未处理"),
                payloadCaptor.capture(), any());
        assertThat(payloadCaptor.getValue()).contains("\"title\"");
        assertThat(payloadCaptor.getValue()).doesNotContain("secret_id");
        assertThat(payloadCaptor.getValue()).doesNotContain("signature");
        assertThat(payloadCaptor.getValue()).doesNotContain("timestamp");
    }

    @Test
    void submitNewsMedia_rejectsWhenBalanceIsBelowSafetyThreshold() throws Exception {
        AuthorityMediaResource resource = resource();
        when(resourceMapper.selectById(11L)).thenReturn(resource);
        when(resourceSyncService.refreshNewsMediaResourceIfStale(11L))
                .thenReturn(new AuthorityMediaResourceSyncService.RefreshResult(11L, false, false, "fresh"));
        when(client.userInfo()).thenReturn(objectMapper.readTree("{\"code\":200,\"data\":{\"money\":\"100\"}}"));

        SubmitResult result = adapter.submitNewsMedia(
                article(),
                project(),
                task(),
                99L,
                new TargetContext.AuthorityMediaTarget(11L, new BigDecimal("120"), "https://preview.example/a", null, null),
                "article body"
        );

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("balance");
        verify(orderMapper, never()).insert(any());
        verify(client, never()).createNewsMediaOrder(any());
    }

    @Test
    void validateBeforeCreatingTask_rejectsWhenSameArticleAndResourceHasUnfinishedOrder() {
        AuthorityMediaResource resource = resource();
        when(resourceMapper.selectById(11L)).thenReturn(resource);
        when(resourceSyncService.refreshNewsMediaResourceIfStale(11L))
                .thenReturn(new AuthorityMediaResourceSyncService.RefreshResult(11L, false, false, "fresh"));
        when(orderMapper.selectUnfinishedByArticleAndResource(21L, 11L)).thenReturn(java.util.List.of(new AuthorityMediaOrder()));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> adapter.validateBeforeCreatingTask(
                        article(),
                        new TargetContext.AuthorityMediaTarget(11L, new BigDecimal("120"), "https://preview.example/a", null, null)
                ))
                .isInstanceOf(com.huanjing.geo.common.exception.BizException.class)
                .hasMessageContaining("已有进行中");
    }

    @Test
    void submitNewsMedia_vendorBusinessErrorMarksSubmitFailedAndClassifiesByMessage() throws Exception {
        AuthorityMediaResource resource = resource();
        when(resourceMapper.selectById(11L)).thenReturn(resource);
        when(resourceSyncService.refreshNewsMediaResourceIfStale(11L))
                .thenReturn(new AuthorityMediaResourceSyncService.RefreshResult(11L, false, false, "fresh"));
        when(client.userInfo()).thenReturn(objectMapper.readTree("{\"code\":200,\"data\":{\"money\":\"1000\"}}"));
        doAnswer(invocation -> {
            AuthorityMediaOrder order = invocation.getArgument(0);
            order.setId(502L);
            return 1;
        }).when(orderMapper).insert(any(AuthorityMediaOrder.class));
        when(previewTokenService.issuePreviewUrl(any(), any(), eq("https://preview.example/a")))
                .thenReturn("https://preview.example/a/api/public/authority-media/previews/token");
        when(client.buildAuditPayload(any())).thenReturn(auditPayload());
        when(client.createNewsMediaOrder(any())).thenThrow(new MeititejiaApiException(
                200, 201, "余额不足", "create_media_order", "{\"code\":201,\"msg\":\"余额不足\"}", false, null
        ));

        SubmitResult result = adapter.submitNewsMedia(
                article(),
                project(),
                task(),
                99L,
                new TargetContext.AuthorityMediaTarget(11L, new BigDecimal("120"), "https://preview.example/a", null, null),
                "article body"
        );

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getFailureKind()).isEqualTo("VALIDATION");
        verify(orderMapper).updateSubmissionResult(eq(502L), eq("submit_failed"), isNull(), isNull(), isNull(), any(), any());
    }

    private AuthorityMediaResource resource() {
        AuthorityMediaResource resource = new AuthorityMediaResource();
        resource.setId(11L);
        resource.setResourceType(MeititejiaResourceType.NEWS_MEDIA.name());
        resource.setExternalResourceId("135");
        resource.setPrice(new BigDecimal("100"));
        return resource;
    }

    private ArticleDraft article() {
        ArticleDraft article = new ArticleDraft();
        article.setId(21L);
        article.setProjectId(31L);
        article.setTitle("title");
        return article;
    }

    private Project project() {
        Project project = new Project();
        project.setId(31L);
        return project;
    }

    private DistributionTask task() {
        DistributionTask task = new DistributionTask();
        task.setId(41L);
        return task;
    }

    private Map<String, String> auditPayload() {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("title", "title");
        return payload;
    }
}
