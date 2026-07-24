package com.huanjing.geo.module.mobiledashboard.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.security.TrustedProxyClientIp;
import com.huanjing.geo.module.mobiledashboard.dto.MobileDashboardWechatClientErrorRequest;
import com.huanjing.geo.module.mobiledashboard.dto.MobileDashboardWechatConfigRequest;
import com.huanjing.geo.module.mobiledashboard.dto.MobileDashboardWechatConfigVO;
import com.huanjing.geo.module.mobiledashboard.dto.MobileDashboardWechatSharePreviewVO;
import com.huanjing.geo.module.mobiledashboard.entity.MobileDashboardShare;
import com.huanjing.geo.module.mobiledashboard.mapper.MobileDashboardShareMapper;
import com.huanjing.geo.module.mobiledashboard.wechat.MobileDashboardWechatJsSdkProperties;
import com.huanjing.geo.module.mobiledashboard.wechat.MobileDashboardWechatShareRateLimiter;
import com.huanjing.geo.module.mobiledashboard.wechat.WechatJsapiTicketService;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MobileDashboardWechatShareService {
    private static final int MAX_SIGNATURE_URL_LENGTH = 2048;

    private final MobileDashboardShareService shareService;
    private final MobileDashboardShareMapper shareMapper;
    private final ProjectMapper projectMapper;
    private final MobileDashboardWechatJsSdkProperties properties;
    private final WechatJsapiTicketService ticketService;
    private final MobileDashboardWechatShareRateLimiter rateLimiter;

    @Value("${geo.mobile-dashboard.web-base-url}")
    private String webBaseUrl;

    public MobileDashboardWechatConfigVO createConfig(String authorization,
                                                       MobileDashboardWechatConfigRequest request,
                                                       HttpServletRequest servletRequest) {
        MobileDashboardSessionTokenService.SessionClaims claims = null;
        try {
            claims = shareService.requireValidSession(authorization);
            rateLimiter.enforceConfig(claims.shareId(), resolveClientIp(servletRequest));
            if (!properties.isEnabledForProject(claims.projectId())) {
                log.info("Mobile dashboard WeChat JS-SDK config skipped by rollout shareId={} projectId={}",
                        claims.shareId(), claims.projectId());
                return MobileDashboardWechatConfigVO.disabled();
            }

            MobileDashboardShare share = requireMatchingShare(claims);
            Project project = requireProject(claims.projectId());
            String signatureUrl = validateAndNormalizeSignatureUrl(request.url(), share.getShareCode());
            long timestamp = Instant.now().getEpochSecond();
            String nonce = UUID.randomUUID().toString().replace("-", "");
            String signature = sign(ticketService.getTicket(), nonce, timestamp, signatureUrl);
            MobileDashboardWechatConfigVO.ShareContent content = new MobileDashboardWechatConfigVO.ShareContent(
                    shareService.resolveShareCardTitle(project),
                    properties.getShareDescription(),
                    canonicalShareUrl(share.getShareCode()),
                    properties.getShareImageUrl()
            );
            log.info("Mobile dashboard WeChat JS-SDK config succeeded shareId={} projectId={}",
                    claims.shareId(), claims.projectId());
            return new MobileDashboardWechatConfigVO(
                    true,
                    properties.getAppId(),
                    timestamp,
                    nonce,
                    signature,
                    content
            );
        } catch (RuntimeException ex) {
            Long shareId = claims == null ? null : claims.shareId();
            Long projectId = claims == null ? null : claims.projectId();
            if (ex instanceof BizException bizException) {
                log.warn("Mobile dashboard WeChat JS-SDK config failed shareId={} projectId={} type={} code={}",
                        shareId, projectId, ex.getClass().getSimpleName(), bizException.getCode());
            } else {
                log.error("Mobile dashboard WeChat JS-SDK config failed shareId={} projectId={} type={}",
                        shareId, projectId, ex.getClass().getSimpleName(), ex);
            }
            throw ex;
        }
    }

    public MobileDashboardWechatSharePreviewVO preview(Long projectId) {
        shareService.ensureProjectReadable(projectId);
        Project project = requireProject(projectId);
        return new MobileDashboardWechatSharePreviewVO(
                shareService.resolveShareCardTitle(project),
                properties.getShareDescription(),
                properties.getShareImageUrl(),
                properties.isEnabledForProject(projectId),
                properties.getRolloutMode()
        );
    }

    public MobileDashboardWechatConfigVO.ShareContent shareCardContent(String shareCode) {
        return new MobileDashboardWechatConfigVO.ShareContent(
                shareService.resolveShareCardTitle(shareCode),
                properties.getShareDescription(),
                canonicalShareUrl(shareCode),
                properties.getShareImageUrl()
        );
    }

    public void reportClientError(String authorization,
                                  MobileDashboardWechatClientErrorRequest request,
                                  HttpServletRequest servletRequest) {
        MobileDashboardSessionTokenService.SessionClaims claims = shareService.requireValidSession(authorization);
        rateLimiter.enforceClientError(claims.shareId(), resolveClientIp(servletRequest));
        log.warn("Mobile dashboard WeChat JS-SDK client error shareId={} projectId={} stage={} code={}",
                claims.shareId(), claims.projectId(), request.stage(), request.code());
        shareService.logPublicApiAccess(
                authorization,
                "wechat_js_sdk_" + request.stage(),
                false,
                request.code(),
                servletRequest
        );
    }

    String validateAndNormalizeSignatureUrl(String value, String shareCode) {
        if (!StringUtils.hasText(value) || value.length() > MAX_SIGNATURE_URL_LENGTH) {
            throw new BizException(400, "invalid WeChat JS-SDK signature URL");
        }
        String withoutFragment = value.trim();
        int fragmentIndex = withoutFragment.indexOf('#');
        if (fragmentIndex >= 0) {
            withoutFragment = withoutFragment.substring(0, fragmentIndex);
        }
        try {
            URI uri = URI.create(withoutFragment);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || !properties.getAllowedHosts().contains(host)
                    || uri.getPort() != -1
                    || StringUtils.hasText(uri.getUserInfo())) {
                throw new BizException(400, "WeChat JS-SDK signature URL is not allowed");
            }
            String expectedRoot = "/m/" + shareCode;
            String path = uri.getPath();
            if (path == null
                    || !path.equals(uri.normalize().getPath())
                    || !(path.equalsIgnoreCase(expectedRoot)
                    || path.toLowerCase(Locale.ROOT).startsWith(expectedRoot.toLowerCase(Locale.ROOT) + "/"))) {
                throw new BizException(400, "WeChat JS-SDK signature URL does not match the current share");
            }
            return withoutFragment;
        } catch (BizException ex) {
            throw ex;
        } catch (IllegalArgumentException ex) {
            throw new BizException(400, "invalid WeChat JS-SDK signature URL");
        }
    }

    String sign(String ticket, String nonce, long timestamp, String url) {
        String source = "jsapi_ticket=" + ticket
                + "&noncestr=" + nonce
                + "&timestamp=" + timestamp
                + "&url=" + url;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new BizException(500, "unable to generate WeChat JS-SDK signature");
        }
    }

    private MobileDashboardShare requireMatchingShare(MobileDashboardSessionTokenService.SessionClaims claims) {
        MobileDashboardShare share = shareMapper.selectById(claims.shareId());
        if (share == null || !claims.projectId().equals(share.getProjectId()) || !StringUtils.hasText(share.getShareCode())) {
            throw new BizException(401, "mobile dashboard share context mismatch");
        }
        return share;
    }

    private Project requireProject(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null || project.getDeletedAt() != null) {
            throw new BizException(404, "Project not found");
        }
        return project;
    }

    private String canonicalShareUrl(String shareCode) {
        String baseUrl = StringUtils.hasText(webBaseUrl) ? webBaseUrl.trim() : "";
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        URI uri;
        try {
            uri = URI.create(baseUrl);
        } catch (IllegalArgumentException ex) {
            throw new BizException(503, "mobile dashboard public base URL is invalid");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !StringUtils.hasText(uri.getHost())
                || uri.getPort() != -1
                || StringUtils.hasText(uri.getUserInfo())
                || (StringUtils.hasText(uri.getPath()) && !"/".equals(uri.getPath()))
                || StringUtils.hasText(uri.getQuery())
                || StringUtils.hasText(uri.getFragment())) {
            throw new BizException(503, "mobile dashboard public base URL must be an absolute HTTPS URL");
        }
        return baseUrl + "/m/" + shareCode;
    }

    private String resolveClientIp(HttpServletRequest request) {
        return TrustedProxyClientIp.resolve(request);
    }
}
