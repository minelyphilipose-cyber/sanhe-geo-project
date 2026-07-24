package com.huanjing.geo.module.mobiledashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.customer.access.InternalScopeService;
import com.huanjing.geo.module.mobiledashboard.dto.MobileDashboardBootstrapVO;
import com.huanjing.geo.module.mobiledashboard.dto.MobileDashboardSessionVO;
import com.huanjing.geo.module.mobiledashboard.dto.MobileDashboardShareCreateRequest;
import com.huanjing.geo.module.mobiledashboard.dto.MobileDashboardShareVO;
import com.huanjing.geo.module.mobiledashboard.entity.MobileDashboardAccessLog;
import com.huanjing.geo.module.mobiledashboard.entity.MobileDashboardShare;
import com.huanjing.geo.module.mobiledashboard.mapper.MobileDashboardAccessLogMapper;
import com.huanjing.geo.module.mobiledashboard.mapper.MobileDashboardShareMapper;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.ActivityLogService;
import com.huanjing.geo.module.system.service.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class MobileDashboardShareService {

    private static final String ACTIVE = "active";
    private static final String DISABLED = "disabled";
    private static final String DEFAULT_SHARE_CARD_TITLE = "移动数据看板";
    private static final char[] SHARE_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern SHARE_CODE_PATTERN = Pattern.compile("[A-HJ-NP-Z2-9]{8}");

    private final MobileDashboardShareMapper shareMapper;
    private final MobileDashboardAccessLogMapper accessLogMapper;
    private final ProjectMapper projectMapper;
    private final CurrentUserService currentUserService;
    private final ActivityLogService activityLogService;
    private final InternalScopeService internalScopeService;
    private final MobileDashboardSessionTokenService sessionTokenService;

    @Value("${geo.mobile-dashboard.share-token-salt:${geo.jwt.secret}}")
    private String tokenSalt;

    @Value("${geo.mobile-dashboard.default-share-ttl-days:90}")
    private long defaultShareTtlDays;

    @Value("${geo.mobile-dashboard.web-base-url:http://localhost:5173}")
    private String webBaseUrl;

    public List<MobileDashboardShareVO> listShares(Long projectId) {
        Project project = requireReadableProject(projectId);
        return shareMapper.selectList(
                new LambdaQueryWrapper<MobileDashboardShare>()
                        .eq(MobileDashboardShare::getProjectId, project.getId())
                        .orderByDesc(MobileDashboardShare::getCreatedAt, MobileDashboardShare::getId)
        ).stream().map(this::toVO).toList();
    }

    @Transactional
    public MobileDashboardShareVO createShare(Long projectId, MobileDashboardShareCreateRequest request) {
        Project project = requireWritableProject(projectId);
        int disabledActiveCount = disableActiveShares(project.getId());

        String token = generateLongToken();
        String shareCode = generateUniqueShareCode();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = request != null && request.getExpiresAt() != null
                ? request.getExpiresAt()
                : now.plusDays(defaultShareTtlDays);
        if (!expiresAt.isAfter(now)) {
            throw new BizException(400, "Share expiry must be in the future");
        }

        MobileDashboardShare share = new MobileDashboardShare();
        share.setProjectId(project.getId());
        share.setShareCode(shareCode);
        share.setTokenHash(hashToken(token));
        share.setTokenPrefix(token.substring(0, Math.min(12, token.length())));
        share.setStatus(ACTIVE);
        share.setExpiresAt(expiresAt);
        share.setCreatedBy(currentUserService.requireCurrentUser().getId());
        share.setAccessCount(0L);
        shareMapper.insert(share);
        Map<String, Object> shareAfter = new LinkedHashMap<>();
        shareAfter.put("shareId", share.getId());
        shareAfter.put("shareCode", share.getShareCode());
        shareAfter.put("tokenPrefix", share.getTokenPrefix());
        shareAfter.put("expiresAt", share.getExpiresAt());
        activityLogService.logAction(share.getCreatedBy(), "mobile_dashboard_share.create", "project", project.getId(),
                null, shareAfter, Map.of("disabledActiveCount", disabledActiveCount));
        return toVO(share);
    }

    @Transactional
    public void disableShare(Long id) {
        MobileDashboardShare share = requireShare(id);
        requireWritableProject(share.getProjectId());
        if (!ACTIVE.equalsIgnoreCase(share.getStatus())) {
            return;
        }
        String beforeStatus = share.getStatus();
        share.setStatus(DISABLED);
        share.setDisabledAt(LocalDateTime.now());
        shareMapper.updateById(share);
        activityLogService.logAction(currentUserService.requireCurrentUser().getId(), "mobile_dashboard_share.disable", "project", share.getProjectId(),
                Map.of("shareId", share.getId(), "status", beforeStatus),
                Map.of("shareId", share.getId(), "status", share.getStatus(), "disabledAt", share.getDisabledAt()),
                Map.of("tokenPrefix", share.getTokenPrefix()));
    }

    @Transactional
    public void deleteShare(Long id) {
        MobileDashboardShare share = requireShare(id);
        requireWritableProject(share.getProjectId());
        if (ACTIVE.equalsIgnoreCase(share.getStatus())) {
            throw new BizException(400, "Active mobile dashboard share must be disabled before deletion");
        }
        shareMapper.deleteById(id);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("shareId", share.getId());
        metadata.put("status", share.getStatus());
        metadata.put("tokenPrefix", share.getTokenPrefix());
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("tokenPrefix", share.getTokenPrefix());
        activityLogService.logAction(currentUserService.requireCurrentUser().getId(), "mobile_dashboard_share.delete", "project", share.getProjectId(),
                metadata,
                null,
                before);
    }

    @Transactional
    public MobileDashboardSessionVO exchangeSession(String shareCode, HttpServletRequest request) {
        if (!StringUtils.hasText(shareCode)) {
            logAccess(null, null, "exchange_session", false, "missing_share_code", request);
            throw new BizException(401, "Mobile dashboard share code is required");
        }
        String normalized = normalizeShareCode(shareCode);
        MobileDashboardShare share = shareMapper.selectOne(
                new LambdaQueryWrapper<MobileDashboardShare>()
                        .eq(MobileDashboardShare::getShareCode, normalized)
                        .last("LIMIT 1")
        );
        if (share == null) {
            logAccess(null, null, "exchange_session", false, "invalid_share_code", request);
            throw new BizException(401, "Mobile dashboard link is invalid");
        }
        if (!ACTIVE.equalsIgnoreCase(share.getStatus())) {
            logAccess(share, "exchange_session", false, "disabled", request);
            throw new BizException(403, "Mobile dashboard link has been disabled");
        }
        if (share.getExpiresAt() == null || !share.getExpiresAt().isAfter(LocalDateTime.now())) {
            logAccess(share, "exchange_session", false, "expired", request);
            throw new BizException(403, "Mobile dashboard link has expired");
        }
        if (StringUtils.hasText(share.getAccessPasswordHash())) {
            logAccess(share, "exchange_session", false, "password_required", request);
            throw new BizException(403, "Access password is required");
        }

        Project project = requireProject(share.getProjectId());
        MobileDashboardSessionTokenService.IssuedSession issued = sessionTokenService.issue(share.getId(), project.getId());
        share.setLastAccessAt(LocalDateTime.now());
        share.setAccessCount(share.getAccessCount() == null ? 1L : share.getAccessCount() + 1L);
        shareMapper.updateById(share);
        logAccess(share, "exchange_session", true, null, request);

        MobileDashboardSessionVO vo = new MobileDashboardSessionVO();
        vo.setSessionToken(issued.token());
        vo.setSessionExpiresAt(issued.expiresAt());
        vo.setSessionTtlSeconds(sessionTokenService.getTtlSeconds());
        vo.setShareId(share.getId());
        vo.setProjectId(project.getId());
        vo.setProjectName(project.getProjectName());
        vo.setBrandName(project.getBrandName());
        vo.setContentPlatforms(MobileDashboardContentChannelCatalog.platformOptions());
        return vo;
    }

    public MobileDashboardBootstrapVO getBootstrap(String sessionToken) {
        MobileDashboardSessionTokenService.SessionClaims claims = requireValidSession(sessionToken);
        Project project = requireProject(claims.projectId());
        MobileDashboardBootstrapVO vo = new MobileDashboardBootstrapVO();
        vo.setProjectId(project.getId());
        vo.setProjectName(project.getProjectName());
        vo.setBrandName(project.getBrandName());
        vo.setAvailablePages(Map.of(
                "home", true,
                "monitor", true,
                "content", true,
                "report", false
        ));
        vo.setContentPlatforms(MobileDashboardContentChannelCatalog.platformOptions());
        vo.setMessage("Dashboard aggregation APIs are available. Judge-derived metrics remain unavailable until judge pipeline launch.");
        return vo;
    }

    public String resolveShareCardTitle(String shareCode) {
        if (!StringUtils.hasText(shareCode)) {
            return DEFAULT_SHARE_CARD_TITLE;
        }
        String normalized = normalizeShareCode(shareCode);
        if (!SHARE_CODE_PATTERN.matcher(normalized).matches()) {
            return DEFAULT_SHARE_CARD_TITLE;
        }
        MobileDashboardShare share = shareMapper.selectOne(
                new LambdaQueryWrapper<MobileDashboardShare>()
                        .eq(MobileDashboardShare::getShareCode, normalized)
                        .last("LIMIT 1")
        );
        if (share == null
                || !ACTIVE.equalsIgnoreCase(share.getStatus())
                || share.getExpiresAt() == null
                || !share.getExpiresAt().isAfter(LocalDateTime.now())) {
            return DEFAULT_SHARE_CARD_TITLE;
        }
        Project project = projectMapper.selectById(share.getProjectId());
        if (project == null || project.getDeletedAt() != null) {
            return DEFAULT_SHARE_CARD_TITLE;
        }
        return resolveShareCardTitle(project);
    }

    public String resolveShareCardTitle(Project project) {
        if (project == null || project.getDeletedAt() != null) {
            return DEFAULT_SHARE_CARD_TITLE;
        }
        if (StringUtils.hasText(project.getBrandName())) {
            return formatShareCardTitle(project.getBrandName());
        }
        if (StringUtils.hasText(project.getCompanyName())) {
            return formatShareCardTitle(project.getCompanyName());
        }
        if (StringUtils.hasText(project.getProjectName())) {
            return formatShareCardTitle(project.getProjectName());
        }
        return DEFAULT_SHARE_CARD_TITLE;
    }

    private String formatShareCardTitle(String displayName) {
        return truncate(DEFAULT_SHARE_CARD_TITLE + " | " + displayName.trim(), 80);
    }

    public void ensureProjectReadable(Long projectId) {
        requireReadableProject(projectId);
    }

    public MobileDashboardSessionTokenService.SessionClaims requireValidSession(String sessionToken) {
        MobileDashboardSessionTokenService.SessionClaims claims = sessionTokenService.parse(extractBearerToken(sessionToken));
        MobileDashboardShare share = requireShare(claims.shareId());
        if (!claims.projectId().equals(share.getProjectId())) {
            throw new BizException(401, "Mobile dashboard session project mismatch");
        }
        if (!ACTIVE.equalsIgnoreCase(share.getStatus()) || share.getExpiresAt() == null || !share.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new BizException(401, "Mobile dashboard session is no longer valid");
        }
        return claims;
    }

    private int disableActiveShares(Long projectId) {
        LocalDateTime now = LocalDateTime.now();
        List<MobileDashboardShare> shares = shareMapper.selectList(
                new LambdaQueryWrapper<MobileDashboardShare>()
                        .eq(MobileDashboardShare::getProjectId, projectId)
                        .eq(MobileDashboardShare::getStatus, ACTIVE)
        );
        for (MobileDashboardShare share : shares) {
            share.setStatus(DISABLED);
            share.setDisabledAt(now);
            shareMapper.updateById(share);
        }
        return shares.size();
    }

    private MobileDashboardShareVO toVO(MobileDashboardShare share) {
        MobileDashboardShareVO vo = new MobileDashboardShareVO();
        vo.setId(share.getId());
        vo.setProjectId(share.getProjectId());
        vo.setShareCode(share.getShareCode());
        vo.setTokenPrefix(share.getTokenPrefix());
        vo.setStatus(share.getStatus());
        vo.setExpiresAt(share.getExpiresAt());
        vo.setCreatedBy(share.getCreatedBy());
        vo.setCreatedAt(share.getCreatedAt());
        vo.setDisabledAt(share.getDisabledAt());
        vo.setLastAccessAt(share.getLastAccessAt());
        vo.setAccessCount(share.getAccessCount());
        if (StringUtils.hasText(share.getShareCode())) {
            vo.setShareUrl(trimTrailingSlash(webBaseUrl) + "/m/" + share.getShareCode());
        }
        return vo;
    }

    private MobileDashboardShare requireShare(Long id) {
        MobileDashboardShare share = shareMapper.selectById(id);
        if (share == null) {
            throw new BizException(404, "Mobile dashboard share not found");
        }
        return share;
    }

    private Project requireReadableProject(Long projectId) {
        currentUserService.ensurePermission("project.read");
        Project project = requireProject(projectId);
        SysUser user = currentUserService.requireCurrentUser();
        currentUserService.ensurePartnerResourceAccess(user, project.getPartnerId(), "project");
        internalScopeService.ensureProjectAccess(user, project, "project");
        return project;
    }

    private Project requireWritableProject(Long projectId) {
        currentUserService.ensurePermission("project.report.export");
        Project project = requireProject(projectId);
        SysUser user = currentUserService.requireCurrentUser();
        currentUserService.ensurePartnerResourceAccess(user, project.getPartnerId(), "project");
        internalScopeService.ensureProjectAccess(user, project, "project");
        return project;
    }

    private Project requireProject(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null || project.getDeletedAt() != null) {
            throw new BizException(404, "Project not found");
        }
        return project;
    }

    private String generateLongToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return "mdb_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String generateUniqueShareCode() {
        for (int i = 0; i < 12; i++) {
            String code = generateShareCode();
            Long count = shareMapper.selectCount(
                    new LambdaQueryWrapper<MobileDashboardShare>()
                            .eq(MobileDashboardShare::getShareCode, code)
            );
            if (count == null || count == 0) {
                return code;
            }
        }
        throw new BizException(500, "Unable to generate mobile dashboard share code");
    }

    private String generateShareCode() {
        char[] code = new char[8];
        for (int i = 0; i < code.length; i++) {
            code[i] = SHARE_CODE_ALPHABET[RANDOM.nextInt(SHARE_CODE_ALPHABET.length)];
        }
        return new String(code);
    }

    private String normalizeShareCode(String shareCode) {
        return shareCode.trim().toUpperCase(Locale.ROOT);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((token + tokenSalt).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            throw new BizException(500, "Unable to hash mobile dashboard token", e);
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        if (!StringUtils.hasText(expected) || !StringUtils.hasText(actual)) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }

    private void logAccess(MobileDashboardShare share, String eventType, boolean success, String failReason, HttpServletRequest request) {
        logAccess(share == null ? null : share.getId(), share == null ? null : share.getProjectId(), eventType, success, failReason, request);
    }

    public void logPublicApiAccess(String authorization, String eventType, boolean success, String failReason, HttpServletRequest request) {
        Long shareId = null;
        Long projectId = null;
        try {
            MobileDashboardSessionTokenService.SessionClaims claims = sessionTokenService.parse(extractBearerToken(authorization));
            shareId = claims.shareId();
            projectId = claims.projectId();
        } catch (Exception ignored) {
            // Invalid sessions are still logged without token material.
        }
        logAccess(shareId, projectId, eventType, success, failReason, request);
    }

    private void logAccess(Long shareId, Long projectId, String eventType, boolean success, String failReason, HttpServletRequest request) {
        MobileDashboardAccessLog log = new MobileDashboardAccessLog();
        log.setShareId(shareId);
        log.setProjectId(projectId);
        log.setEventType(eventType);
        log.setSuccess(success);
        log.setFailReason(failReason);
        String ip = resolveClientIp(request);
        log.setClientIpMasked(maskIp(ip));
        log.setClientIpHash(hashClientIp(ip));
        log.setUserAgent(truncate(request == null ? null : request.getHeader("User-Agent"), 512));
        accessLogMapper.insert(log);
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String[] headerNames = {"X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP"};
        for (String header : headerNames) {
            String value = request.getHeader(header);
            if (!StringUtils.hasText(value) || "unknown".equalsIgnoreCase(value)) {
                continue;
            }
            int comma = value.indexOf(',');
            return comma > 0 ? value.substring(0, comma).trim() : value.trim();
        }
        return request.getRemoteAddr();
    }

    private String maskIp(String ip) {
        if (!StringUtils.hasText(ip)) {
            return "";
        }
        if (ip.contains(".")) {
            String[] parts = ip.split("\\.");
            if (parts.length == 4) {
                return parts[0] + "." + parts[1] + "." + parts[2] + ".*";
            }
        }
        int colon = ip.indexOf(':');
        return colon > 0 ? ip.substring(0, colon) + ":*" : "***";
    }

    private String hashClientIp(String ip) {
        if (!StringUtils.hasText(ip)) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest((ip + tokenSalt).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return null;
        }
    }

    private String extractBearerToken(String authorization) {
        if (!StringUtils.hasText(authorization)) {
            throw new BizException(401, "Mobile dashboard session is required");
        }
        String value = authorization.trim();
        if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return value.substring(7).trim();
        }
        return value;
    }

    private String trimTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value) || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
