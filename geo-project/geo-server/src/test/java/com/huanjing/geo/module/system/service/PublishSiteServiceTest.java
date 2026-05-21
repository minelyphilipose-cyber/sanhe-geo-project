package com.huanjing.geo.module.system.service;

import com.huanjing.geo.module.content.config.BrandGeoSiteProperties;
import com.huanjing.geo.module.system.entity.PublishSite;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.PublishSiteMapper;
import com.huanjing.geo.module.system.mapper.SysDictItemMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublishSiteServiceTest {

    private final PublishSiteMapper publishSiteMapper = mock(PublishSiteMapper.class);
    private final SysDictItemMapper sysDictItemMapper = mock(SysDictItemMapper.class);
    private final CurrentUserService currentUserService = mock(CurrentUserService.class);
    private final PlatformCredentialService platformCredentialService = mock(PlatformCredentialService.class);
    private final BrandGeoSiteProperties brandGeoSiteProperties = new BrandGeoSiteProperties();

    @Test
    void brandGeoSiteConnectivityUsesConfiguredEndpoint() {
        brandGeoSiteProperties.setEndpoint("http://agent.example.test/api/publish");
        TestPublishSiteService service = new TestPublishSiteService();
        service.nextPingResult = new PublishSiteService.PingResult(true, "ok");
        givenManagerUser();
        when(publishSiteMapper.selectById(7L)).thenReturn(agentSite());

        Map<String, Object> result = service.testConnectivity(7L);

        assertTrue((Boolean) result.get("success"));
        assertTrue((Boolean) result.get("reachable"));
        assertEquals("endpoint_ping", result.get("testType"));
        assertEquals("http://agent.example.test/api/publish", result.get("endpoint"));
        assertEquals("agent.example.test", result.get("host"));
        assertEquals("agent.example.test", service.pingHost);
    }

    @Test
    void brandGeoSiteConnectivityReportsMissingEndpoint() {
        brandGeoSiteProperties.setEndpoint("");
        TestPublishSiteService service = new TestPublishSiteService();
        givenManagerUser();
        when(publishSiteMapper.selectById(7L)).thenReturn(agentSite());

        Map<String, Object> result = service.testConnectivity(7L);

        assertFalse((Boolean) result.get("success"));
        assertFalse((Boolean) result.get("reachable"));
        assertEquals("endpoint_ping", result.get("testType"));
        assertEquals("BRAND_GEO_SITE_ENDPOINT is not configured", result.get("message"));
    }

    private void givenManagerUser() {
        SysUser user = new SysUser();
        user.setRole("manager");
        when(currentUserService.requireCurrentUser()).thenReturn(user);
    }

    private PublishSite agentSite() {
        PublishSite site = new PublishSite();
        site.setId(7L);
        site.setSiteName("Agent 官网");
        site.setDomain("agent-site.local");
        site.setIntegrationMethod("brand_geo_site");
        return site;
    }

    private class TestPublishSiteService extends PublishSiteService {
        private PingResult nextPingResult = new PingResult(true, "ok");
        private String pingHost;

        private TestPublishSiteService() {
            super(publishSiteMapper, sysDictItemMapper, currentUserService, platformCredentialService, brandGeoSiteProperties);
        }

        @Override
        protected PingResult runPing(String host) {
            pingHost = host;
            return nextPingResult;
        }
    }
}
