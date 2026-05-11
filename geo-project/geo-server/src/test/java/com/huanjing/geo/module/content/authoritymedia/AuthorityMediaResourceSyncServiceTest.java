package com.huanjing.geo.module.content.authoritymedia;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.content.entity.AuthorityMediaResource;
import com.huanjing.geo.module.content.mapper.AuthorityMediaResourceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorityMediaResourceSyncServiceTest {

    private static final MeititejiaResourceType NEWS_MEDIA = MeititejiaResourceType.NEWS_MEDIA;

    @Mock
    private MeititejiaClient client;
    @Mock
    private AuthorityMediaResourceMapper resourceMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MeititejiaProperties properties;
    private AuthorityMediaResourceSyncService service;

    @BeforeEach
    void setUp() {
        properties = new MeititejiaProperties();
        properties.setSyncPageLimit(2);
        properties.setResourceStalenessThresholdMinutes(60);
        service = new AuthorityMediaResourceSyncService(client, resourceMapper, properties, objectMapper);
    }

    @Test
    void syncNewsMediaFull_paginatesUntilPageSizeBelowLimitAndUpsertsRawPayload() throws Exception {
        when(client.listResources(NEWS_MEDIA, 1, 2, null, null)).thenReturn(json("""
                {"code":200,"data":[
                  {"id":101,"media_name":"Portal A","price":"120.00","status":1,"pc_weight":5,"uptime":1710000001},
                  {"id":102,"media_name":"Portal B","price":80,"status":1,"province":"上海","uptime":1710000002}
                ]}
                """));
        when(client.listResources(NEWS_MEDIA, 2, 2, null, null)).thenReturn(json("""
                {"code":200,"data":[
                  {"id":103,"media_name":"Portal C","price":"60.50","status":1,"channel_type":"科技","uptime":1710000003}
                ]}
                """));
        when(resourceMapper.upsert(any())).thenReturn(1);

        AuthorityMediaResourceSyncService.SyncResult result = service.syncNewsMediaFull();

        assertThat(result.pages()).isEqualTo(2);
        assertThat(result.fetched()).isEqualTo(3);
        assertThat(result.processed()).isEqualTo(3);
        ArgumentCaptor<AuthorityMediaResource> captor = ArgumentCaptor.forClass(AuthorityMediaResource.class);
        verify(resourceMapper, org.mockito.Mockito.times(3)).upsert(captor.capture());
        AuthorityMediaResource first = captor.getAllValues().get(0);
        assertThat(first.getResourceType()).isEqualTo("NEWS_MEDIA");
        assertThat(first.getExternalResourceId()).isEqualTo("101");
        assertThat(first.getName()).isEqualTo("Portal A");
        assertThat(first.getPrice()).isEqualByComparingTo("120.00");
        assertThat(first.getPcWeight()).isEqualTo(5);
        assertThat(first.getRawPayload()).contains("\"media_name\":\"Portal A\"");
    }

    @Test
    void syncNewsMediaFull_acceptsVendorDateTimeStringForUptime() throws Exception {
        when(client.listResources(NEWS_MEDIA, 1, 2, null, null)).thenReturn(json("""
                {"code":200,"data":[
                  {"id":50452,"media_name":"Portal Date","price":"120.00","status":1,"uptime":"2025-10-27 16:53:17"}
                ]}
                """));
        when(resourceMapper.upsert(any())).thenReturn(1);

        AuthorityMediaResourceSyncService.SyncResult result = service.syncNewsMediaFull();

        assertThat(result.processed()).isEqualTo(1);
        ArgumentCaptor<AuthorityMediaResource> captor = ArgumentCaptor.forClass(AuthorityMediaResource.class);
        verify(resourceMapper).upsert(captor.capture());
        assertThat(captor.getValue().getUptime()).isEqualTo(
                LocalDateTime.of(2025, 10, 27, 16, 53, 17)
                        .atZone(ZoneId.of("Asia/Shanghai"))
                        .toEpochSecond()
        );
    }

    @Test
    void syncNewsMediaIncremental_usesLatestUptimeAsIncrementalCursor() throws Exception {
        when(resourceMapper.selectMaxUptime("NEWS_MEDIA")).thenReturn(1710000000L);
        when(client.listResources(NEWS_MEDIA, 1, 2, null, 1709999940L))
                .thenReturn(json("{\"code\":200,\"data\":[]}"));

        AuthorityMediaResourceSyncService.SyncResult result = service.syncNewsMediaIncremental();

        assertThat(result.uptime()).isEqualTo(1709999940L);
        assertThat(result.fetched()).isZero();
        verify(client).listResources(NEWS_MEDIA, 1, 2, null, 1709999940L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void reconcileNewsMediaIds_marksLocallyMissingResourcesDeleted() throws Exception {
        when(client.getIds(NEWS_MEDIA)).thenReturn(json("""
                {"code":200,"data":[101,"102",{"id":103},null,{"unknown":104},[]]}
                """));
        when(resourceMapper.markDeletedExcept(eq("NEWS_MEDIA"), any(Collection.class), any(LocalDateTime.class)))
                .thenReturn(4);

        AuthorityMediaResourceSyncService.ReconcileResult result = service.reconcileNewsMediaIds();

        assertThat(result.activeIds()).isEqualTo(3);
        assertThat(result.markedDeleted()).isEqualTo(4);
        ArgumentCaptor<Collection<String>> idsCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(resourceMapper).markDeletedExcept(eq("NEWS_MEDIA"), idsCaptor.capture(), any(LocalDateTime.class));
        assertThat(idsCaptor.getValue()).containsExactly("101", "102", "103");
    }

    @Test
    void syncNewsMediaFull_failsWhenListShapeIsUnexpected() throws Exception {
        when(client.listResources(NEWS_MEDIA, 1, 2, null, null)).thenReturn(json("""
                {"code":200,"data":{"error":"unexpected"}}
                """));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.syncNewsMediaFull())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unexpected Meititejia NEWS_MEDIA list response shape");
    }

    @Test
    void reconcileNewsMediaIds_skipsSoftDeleteWhenRemoteIdListIsEmpty() throws Exception {
        when(client.getIds(NEWS_MEDIA)).thenReturn(json("{\"code\":200,\"data\":[]}"));

        AuthorityMediaResourceSyncService.ReconcileResult result = service.reconcileNewsMediaIds();

        assertThat(result.activeIds()).isZero();
        assertThat(result.markedDeleted()).isZero();
        verify(resourceMapper, never()).markDeletedExcept(any(), any(), any());
    }

    @Test
    void refreshNewsMediaResourceIfStale_skipsFreshLocalResource() {
        AuthorityMediaResource resource = resource(7L, "701", LocalDateTime.now().minusMinutes(5));
        when(resourceMapper.selectById(7L)).thenReturn(resource);

        AuthorityMediaResourceSyncService.RefreshResult result = service.refreshNewsMediaResourceIfStale(7L);

        assertThat(result.refreshed()).isFalse();
        assertThat(result.reason()).isEqualTo("fresh");
        verifyNoInteractions(client);
    }

    @Test
    void refreshNewsMediaResourceIfStale_refreshesSingleRemoteResource() throws Exception {
        AuthorityMediaResource resource = resource(8L, "801", LocalDateTime.now().minusHours(2));
        when(resourceMapper.selectById(8L)).thenReturn(resource);
        when(client.listResources(NEWS_MEDIA, 1, 1, 801L, null)).thenReturn(json("""
                {"code":200,"data":[{"id":801,"media_name":"Portal 801","price":"99","status":1}]}
                """));
        when(resourceMapper.upsert(any())).thenReturn(1);

        AuthorityMediaResourceSyncService.RefreshResult result = service.refreshNewsMediaResourceIfStale(8L);

        assertThat(result.refreshed()).isTrue();
        assertThat(result.deleted()).isFalse();
        verify(client).listResources(NEWS_MEDIA, 1, 1, 801L, null);
        ArgumentCaptor<AuthorityMediaResource> captor = ArgumentCaptor.forClass(AuthorityMediaResource.class);
        verify(resourceMapper).upsert(captor.capture());
        assertThat(captor.getValue().getExternalResourceId()).isEqualTo("801");
        assertThat(captor.getValue().getName()).isEqualTo("Portal 801");
    }

    @Test
    void refreshNewsMediaResourceIfStale_marksDeletedWhenRemoteResourceIsMissing() throws Exception {
        AuthorityMediaResource resource = resource(9L, "901", LocalDateTime.now().minusHours(2));
        when(resourceMapper.selectById(9L)).thenReturn(resource);
        when(client.listResources(NEWS_MEDIA, 1, 1, 901L, null))
                .thenReturn(json("{\"code\":200,\"data\":[]}"));
        when(resourceMapper.markDeletedById(eq(9L), any(LocalDateTime.class))).thenReturn(1);

        AuthorityMediaResourceSyncService.RefreshResult result = service.refreshNewsMediaResourceIfStale(9L);

        assertThat(result.refreshed()).isFalse();
        assertThat(result.deleted()).isTrue();
        assertThat(result.reason()).isEqualTo("remote missing");
    }

    private AuthorityMediaResource resource(Long id, String externalId, LocalDateTime updatedAt) {
        AuthorityMediaResource resource = new AuthorityMediaResource();
        resource.setId(id);
        resource.setResourceType("NEWS_MEDIA");
        resource.setExternalResourceId(externalId);
        resource.setUpdatedAt(updatedAt);
        return resource;
    }

    private JsonNode json(String json) throws Exception {
        return objectMapper.readTree(json);
    }
}
