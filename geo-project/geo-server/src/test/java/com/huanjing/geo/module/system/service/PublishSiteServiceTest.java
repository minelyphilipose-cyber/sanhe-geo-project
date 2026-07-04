package com.huanjing.geo.module.system.service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.huanjing.geo.module.system.entity.PublishSite;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.PublishSiteMapper;
import com.huanjing.geo.module.system.mapper.SysDictItemMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublishSiteServiceTest {

    private final PublishSiteMapper publishSiteMapper = mock(PublishSiteMapper.class);
    private final SysDictItemMapper sysDictItemMapper = mock(SysDictItemMapper.class);
    private final CurrentUserService currentUserService = mock(CurrentUserService.class);
    private final PlatformCredentialService platformCredentialService = mock(PlatformCredentialService.class);

    @Test
    void brandGeoSiteConnectivityUsesSiteDomain() {
        TestPublishSiteService service = new TestPublishSiteService();
        service.nextPingResult = new PublishSiteService.PingResult(true, "ok");
        givenManagerUser();
        when(publishSiteMapper.selectById(7L)).thenReturn(agentSite());

        Map<String, Object> result = service.testConnectivity(7L);

        assertTrue((Boolean) result.get("success"));
        assertTrue((Boolean) result.get("reachable"));
        assertEquals("ping", result.get("testType"));
        assertEquals("agent-site.local", result.get("host"));
        assertEquals("agent-site.local", service.pingHost);
    }

    @Test
    void connectivityFailureReturnsBusinessMessage() {
        TestPublishSiteService service = new TestPublishSiteService();
        service.nextPingResult = new PublishSiteService.PingResult(false, "Ping �����Ҳ������� api.example.test");
        givenManagerUser();
        when(publishSiteMapper.selectById(8L)).thenReturn(restApiSite());

        Map<String, Object> result = service.testConnectivity(8L);

        assertFalse((Boolean) result.get("success"));
        assertFalse((Boolean) result.get("reachable"));
        assertEquals("ping", result.get("testType"));
        assertEquals("连通测试失败，请确认域名 DNS 解析已生效，且目标服务器允许 Ping。", result.get("message"));
    }

    @Test
    void forumCredentialAccountsContainerDoesNotDefaultRootExpiry() {
        TestPublishSiteService service = new TestPublishSiteService();
        LocalDateTime accountExpiry = LocalDateTime.now().plusDays(60).withNano(0);
        String expiresAt = accountExpiry.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String rawCredential = "{\"accounts\":[{\"username\":\"forum-user\",\"password\":\"secret\",\"expiresAt\":\""
                + expiresAt + "\"}]}";

        String normalized = ReflectionTestUtils.invokeMethod(
                service,
                "withDefaultForumCookieExpiry",
                "discuz_http",
                rawCredential
        );
        JSONObject root = JSONUtil.parseObj(normalized);
        JSONObject account = root.getJSONArray("accounts").getJSONObject(0);
        LocalDateTime nearest = ReflectionTestUtils.invokeMethod(service, "nearestForumCookieExpiresAt", normalized);

        assertNull(root.getStr("expiresAt"));
        assertEquals(expiresAt, account.getStr("expiresAt"));
        assertEquals("manual", account.getStr("expirySource"));
        assertEquals(accountExpiry, nearest);
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

    private PublishSite restApiSite() {
        PublishSite site = new PublishSite();
        site.setId(8L);
        site.setSiteName("智装");
        site.setDomain("api.example.test");
        site.setIntegrationMethod("rest_api");
        return site;
    }

    private class TestPublishSiteService extends PublishSiteService {
        private PingResult nextPingResult = new PingResult(true, "ok");
        private String pingHost;

        private TestPublishSiteService() {
            super(publishSiteMapper, sysDictItemMapper, currentUserService, platformCredentialService);
        }

        @Override
        protected PingResult runPing(String host) {
            pingHost = host;
            return nextPingResult;
        }
    }
}
