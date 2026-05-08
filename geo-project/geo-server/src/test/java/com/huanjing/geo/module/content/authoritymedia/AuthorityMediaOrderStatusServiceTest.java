package com.huanjing.geo.module.content.authoritymedia;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.content.entity.AuthorityMediaOrder;
import com.huanjing.geo.module.content.mapper.AuthorityMediaOrderMapper;
import com.huanjing.geo.module.content.mapper.DistributionTaskMapper;
import com.huanjing.geo.module.content.service.CompanyChannelQuotaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorityMediaOrderStatusServiceTest {

    @Mock
    private AuthorityMediaOrderMapper orderMapper;
    @Mock
    private DistributionTaskMapper distributionTaskMapper;
    @Mock
    private CompanyChannelQuotaService quotaService;
    @Mock
    private MeititejiaClient client;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AuthorityMediaOrderStatusService service;

    @BeforeEach
    void setUp() {
        service = new AuthorityMediaOrderStatusService(orderMapper, distributionTaskMapper, quotaService, client, objectMapper);
    }

    @Test
    void checkDueNewsMediaOrders_marksPublishedWhenRemoteStatusCompleted() throws Exception {
        AuthorityMediaOrder order = order(1L, 41L, "AM-1", 3);
        when(orderMapper.selectDueForStatusCheck(any(), eq(50))).thenReturn(List.of(order));
        when(client.queryOrders(MeititejiaResourceType.NEWS_MEDIA, List.of("AM-1")))
                .thenReturn(objectMapper.readTree("""
                        {"code":200,"data":[{"no3":"AM-1","status":2,"url":"https://news.example/a"}]}
                        """));
        when(orderMapper.updateRemoteStatus(eq(1L), eq(3), eq(2), eq("已完成"), eq("https://news.example/a"),
                isNull(), any(), any(), isNull(), any(), isNull())).thenReturn(1);
        when(distributionTaskMapper.markAuthorityMediaPublished(eq(41L), eq("https://news.example/a"), any())).thenReturn(1);

        AuthorityMediaOrderStatusService.StatusCheckResult result = service.checkDueNewsMediaOrders(50);

        assertThat(result.selected()).isEqualTo(1);
        assertThat(result.terminal()).isEqualTo(1);
        verify(distributionTaskMapper).markAuthorityMediaPublished(eq(41L), eq("https://news.example/a"), any());
        verify(quotaService, never()).refundConfirmedDistribution(any());
    }

    @Test
    void checkDueNewsMediaOrders_marksRejectedAndRefundsConfirmedQuota() throws Exception {
        AuthorityMediaOrder order = order(2L, 42L, "AM-2", 4);
        when(orderMapper.selectDueForStatusCheck(any(), eq(50))).thenReturn(List.of(order));
        when(client.queryOrders(MeititejiaResourceType.NEWS_MEDIA, List.of("AM-2")))
                .thenReturn(objectMapper.readTree("""
                        {"code":200,"data":[{"no3":"AM-2","status":-1,"reason":"内容违规"}]}
                        """));
        when(orderMapper.updateRemoteStatus(eq(2L), eq(4), eq(-1), eq("已拒稿"), isNull(),
                eq("内容违规"), isNull(), any(), isNull(), any(), isNull())).thenReturn(1);
        when(distributionTaskMapper.markAuthorityMediaFailed(eq(42L), eq("VALIDATION"), eq("内容违规"), any())).thenReturn(1);

        AuthorityMediaOrderStatusService.StatusCheckResult result = service.checkDueNewsMediaOrders(50);

        assertThat(result.terminal()).isEqualTo(1);
        verify(quotaService).refundConfirmedDistribution(42L);
    }

    @Test
    void checkDueNewsMediaOrders_marksDeletedAsPlatformFailureAndRefundsConfirmedQuota() throws Exception {
        AuthorityMediaOrder order = order(5L, 45L, "AM-5", 7);
        when(orderMapper.selectDueForStatusCheck(any(), eq(50))).thenReturn(List.of(order));
        when(client.queryOrders(MeititejiaResourceType.NEWS_MEDIA, List.of("AM-5")))
                .thenReturn(objectMapper.readTree("""
                        {"code":200,"data":[{"no3":"AM-5","status":-2,"reason":"已删除"}]}
                        """));
        when(orderMapper.updateRemoteStatus(eq(5L), eq(7), eq(-2), eq("已删除"), isNull(),
                eq("已删除"), isNull(), any(), isNull(), any(), isNull())).thenReturn(1);
        when(distributionTaskMapper.markAuthorityMediaFailed(eq(45L), eq("PLATFORM"), eq("已删除"), any())).thenReturn(1);

        AuthorityMediaOrderStatusService.StatusCheckResult result = service.checkDueNewsMediaOrders(50);

        assertThat(result.terminal()).isEqualTo(1);
        verify(distributionTaskMapper).markAuthorityMediaFailed(eq(45L), eq("PLATFORM"), eq("已删除"), any());
        verify(quotaService).refundConfirmedDistribution(45L);
    }

    @Test
    void checkDueNewsMediaOrders_schedulesNextCheckForPublishingStatus() throws Exception {
        AuthorityMediaOrder order = order(3L, 43L, "AM-3", 5);
        when(orderMapper.selectDueForStatusCheck(any(), eq(50))).thenReturn(List.of(order));
        when(client.queryOrders(MeititejiaResourceType.NEWS_MEDIA, List.of("AM-3")))
                .thenReturn(objectMapper.readTree("""
                        {"code":200,"data":[{"no3":"AM-3","status":1}]}
                        """));
        when(orderMapper.updateRemoteStatus(eq(3L), eq(5), eq(1), eq("发布中"), isNull(),
                isNull(), isNull(), any(), any(), any(), isNull())).thenReturn(1);

        AuthorityMediaOrderStatusService.StatusCheckResult result = service.checkDueNewsMediaOrders(50);

        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.terminal()).isZero();
        verify(distributionTaskMapper, never()).markAuthorityMediaPublished(any(), any(), any());
        verify(distributionTaskMapper, never()).markAuthorityMediaFailed(any(), any(), any(), any());
    }

    @Test
    void checkDueNewsMediaOrders_updatesNextCheckWhenRemoteDoesNotReturnOrder() throws Exception {
        AuthorityMediaOrder order = order(4L, 44L, "AM-4", 6);
        when(orderMapper.selectDueForStatusCheck(any(), eq(50))).thenReturn(List.of(order));
        when(client.queryOrders(MeititejiaResourceType.NEWS_MEDIA, List.of("AM-4")))
                .thenReturn(objectMapper.readTree("{\"code\":200,\"data\":[]}"));
        when(orderMapper.updateRemoteStatus(eq(4L), eq(6), isNull(), isNull(), isNull(),
                isNull(), isNull(), any(), any(), eq("remote order not returned"), isNull())).thenReturn(1);

        AuthorityMediaOrderStatusService.StatusCheckResult result = service.checkDueNewsMediaOrders(50);

        assertThat(result.missing()).isEqualTo(1);
    }

    @Test
    void checkDueNewsMediaOrders_skipsTaskAndQuotaWhenOrderVersionChanged() throws Exception {
        AuthorityMediaOrder order = order(6L, 46L, "AM-6", 8);
        when(orderMapper.selectDueForStatusCheck(any(), eq(50))).thenReturn(List.of(order));
        when(client.queryOrders(MeititejiaResourceType.NEWS_MEDIA, List.of("AM-6")))
                .thenReturn(objectMapper.readTree("""
                        {"code":200,"data":[{"no3":"AM-6","status":-1,"reason":"内容违规"}]}
                        """));
        when(orderMapper.updateRemoteStatus(eq(6L), eq(8), eq(-1), eq("已拒稿"), isNull(),
                eq("内容违规"), isNull(), any(), isNull(), any(), isNull())).thenReturn(0);

        AuthorityMediaOrderStatusService.StatusCheckResult result = service.checkDueNewsMediaOrders(50);

        assertThat(result.updated()).isZero();
        assertThat(result.terminal()).isZero();
        verify(distributionTaskMapper, never()).markAuthorityMediaFailed(any(), any(), any(), any());
        verify(quotaService, never()).refundConfirmedDistribution(any());
    }

    @Test
    void checkDueNewsMediaOrders_failsWhenOrderListShapeIsUnexpected() throws Exception {
        AuthorityMediaOrder order = order(7L, 47L, "AM-7", 9);
        when(orderMapper.selectDueForStatusCheck(any(), eq(50))).thenReturn(List.of(order));
        when(client.queryOrders(MeititejiaResourceType.NEWS_MEDIA, List.of("AM-7")))
                .thenReturn(objectMapper.readTree("""
                        {"code":200,"data":{"error":"unexpected"}}
                        """));

        assertThatThrownBy(() -> service.checkDueNewsMediaOrders(50))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("query_media_order");
        verify(orderMapper, never()).updateRemoteStatus(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    private AuthorityMediaOrder order(Long id, Long taskId, String externalNo, int lockVersion) {
        AuthorityMediaOrder order = new AuthorityMediaOrder();
        order.setId(id);
        order.setDistributionTaskId(taskId);
        order.setExternalNo(externalNo);
        order.setLockVersion(lockVersion);
        order.setSubmittedAt(LocalDateTime.now().minusMinutes(10));
        order.setCreatedAt(LocalDateTime.now().minusMinutes(10));
        return order;
    }
}
