package com.huanjing.geo.module.mobiledashboard.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.mobiledashboard.dto.MobileDashboardWechatConfigRequest;
import com.huanjing.geo.module.mobiledashboard.dto.MobileDashboardWechatConfigVO;
import com.huanjing.geo.module.mobiledashboard.entity.MobileDashboardShare;
import com.huanjing.geo.module.mobiledashboard.mapper.MobileDashboardShareMapper;
import com.huanjing.geo.module.mobiledashboard.wechat.MobileDashboardWechatJsSdkProperties;
import com.huanjing.geo.module.mobiledashboard.wechat.MobileDashboardWechatShareRateLimiter;
import com.huanjing.geo.module.mobiledashboard.wechat.WechatJsapiTicketService;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MobileDashboardWechatShareServiceTest {

    private MobileDashboardShareService shareService;
    private MobileDashboardShareMapper shareMapper;
    private ProjectMapper projectMapper;
    private WechatJsapiTicketService ticketService;
    private MobileDashboardWechatShareRateLimiter rateLimiter;
    private MobileDashboardWechatJsSdkProperties properties;
    private MobileDashboardWechatShareService service;

    @BeforeEach
    void setUp() {
        shareService = mock(MobileDashboardShareService.class);
        shareMapper = mock(MobileDashboardShareMapper.class);
        projectMapper = mock(ProjectMapper.class);
        ticketService = mock(WechatJsapiTicketService.class);
        rateLimiter = mock(MobileDashboardWechatShareRateLimiter.class);
        properties = new MobileDashboardWechatJsSdkProperties();
        properties.setEnabled(true);
        properties.setAppId("wx_unit_test");
        properties.setAppSecret("not-used-by-this-unit-test");
        properties.setAllowedHosts(List.of("www.huanjingaigeo.com"));
        properties.setRolloutMode("allowlist");
        properties.setRolloutProjectIds(List.of(11L));

        service = new MobileDashboardWechatShareService(
                shareService,
                shareMapper,
                projectMapper,
                properties,
                ticketService,
                rateLimiter
        );
        ReflectionTestUtils.setField(service, "webBaseUrl", "https://www.huanjingaigeo.com");
    }

    @Test
    void createsSignedConfigWithCanonicalRootShareUrl() {
        MobileDashboardSessionTokenService.SessionClaims claims =
                new MobileDashboardSessionTokenService.SessionClaims(5L, 11L);
        MobileDashboardShare share = new MobileDashboardShare();
        share.setId(5L);
        share.setProjectId(11L);
        share.setShareCode("MAHEKSKZ");
        Project project = new Project();
        project.setId(11L);

        when(shareService.requireValidSession("Bearer session")).thenReturn(claims);
        when(shareMapper.selectById(5L)).thenReturn(share);
        when(projectMapper.selectById(11L)).thenReturn(project);
        when(shareService.resolveShareCardTitle(project)).thenReturn("华为鸿蒙智家");
        when(ticketService.getTicket()).thenReturn("ticket");
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");

        MobileDashboardWechatConfigVO result = service.createConfig(
                "Bearer session",
                new MobileDashboardWechatConfigRequest(
                        "https://www.huanjingaigeo.com/m/MAHEKSKZ/monitor?source=wechat#section"
                ),
                servletRequest
        );

        assertThat(result.enabled()).isTrue();
        assertThat(result.appId()).isEqualTo("wx_unit_test");
        assertThat(result.signature()).matches("[0-9a-f]{40}");
        assertThat(result.share().title()).isEqualTo("华为鸿蒙智家");
        assertThat(result.share().link()).isEqualTo("https://www.huanjingaigeo.com/m/MAHEKSKZ");
        verify(rateLimiter).enforceConfig(5L, "127.0.0.1");
    }

    @Test
    void rejectsSignatureUrlFromAnotherHost() {
        BizException ex = assertThrows(
                BizException.class,
                () -> service.validateAndNormalizeSignatureUrl(
                        "https://attacker.example/m/MAHEKSKZ",
                        "MAHEKSKZ"
                )
        );

        assertThat(ex.getMessage()).contains("not allowed");
    }

    @Test
    void rejectsSignatureUrlForAnotherShare() {
        BizException ex = assertThrows(
                BizException.class,
                () -> service.validateAndNormalizeSignatureUrl(
                        "https://www.huanjingaigeo.com/m/ABCDEFGH",
                        "MAHEKSKZ"
                )
        );

        assertThat(ex.getMessage()).contains("does not match");
    }

    @Test
    void rejectsSignatureUrlContainingPathTraversal() {
        BizException ex = assertThrows(
                BizException.class,
                () -> service.validateAndNormalizeSignatureUrl(
                        "https://www.huanjingaigeo.com/m/MAHEKSKZ/../ABCDEFGH",
                        "MAHEKSKZ"
                )
        );

        assertThat(ex.getMessage()).contains("does not match");
    }

    @Test
    void disabledRolloutReturnsWithoutFetchingTicket() {
        properties.setRolloutProjectIds(List.of(99L));
        MobileDashboardSessionTokenService.SessionClaims claims =
                new MobileDashboardSessionTokenService.SessionClaims(5L, 11L);
        when(shareService.requireValidSession("session")).thenReturn(claims);
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");

        MobileDashboardWechatConfigVO result = service.createConfig(
                "session",
                new MobileDashboardWechatConfigRequest("https://www.huanjingaigeo.com/m/MAHEKSKZ"),
                servletRequest
        );

        assertThat(result.enabled()).isFalse();
        verify(ticketService, never()).getTicket();
    }

    @Test
    void signatureUsesOfficialFieldOrder() {
        String signature = service.sign(
                "jsapi_ticket",
                "nonce",
                1_700_000_000L,
                "https://www.huanjingaigeo.com/m/MAHEKSKZ"
        );

        assertThat(signature).isEqualTo("be9e275b77c3138a1830935e04dffa40996ed0e7");
    }
}
