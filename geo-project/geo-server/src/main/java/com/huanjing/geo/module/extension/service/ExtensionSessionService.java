package com.huanjing.geo.module.extension.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.audit.AuditMode;
import com.huanjing.geo.module.audit.AuditResult;
import com.huanjing.geo.module.extension.config.ExtensionProperties;
import com.huanjing.geo.module.extension.dto.ExtensionBindResponse;
import com.huanjing.geo.module.extension.dto.ExtensionTokenRefreshResponse;
import com.huanjing.geo.module.extension.entity.ExtensionSession;
import com.huanjing.geo.module.extension.mapper.ExtensionSessionMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static com.huanjing.geo.module.extension.ExtensionErrorCodes.EXTENSION_BAD_REQUEST;
import static com.huanjing.geo.module.extension.ExtensionErrorCodes.EXTENSION_DENIED;
import static com.huanjing.geo.module.extension.ExtensionErrorCodes.EXTENSION_NOT_FOUND;
import static com.huanjing.geo.module.extension.ExtensionErrorCodes.EXTENSION_UNAUTHORIZED;

@Service
@RequiredArgsConstructor
public class ExtensionSessionService {

    private static final String TOKEN_PREFIX = "ext.";
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final ExtensionSessionMapper sessionMapper;
    private final ExtensionProperties properties;
    private final ExtensionVersionService versionService;
    private final ExtensionAuditSupport auditSupport;
    private final SysUserMapper sysUserMapper;
    private final Clock clock = Clock.systemUTC();
    private static final Set<String> GLOBAL_REVOKE_ROLES = Set.of("super_admin", "manager", "delivery_manager");

    /**
     * Creates a new extension session after the caller has verified brand MANAGE access.
     *
     * <p>SECURITY CONTRACT: this method does not resolve brand ownership by itself. The current
     * binding path must call {@code ExtensionBindCodeService.consume} first; that method consumes
     * a brand-bound code and enforces {@code BrandAccessAction.MANAGE}. Token renewal uses the
     * private helper directly and is not a new bind event.</p>
     */
    @Transactional
    public ExtensionBindResponse createBoundSession(
            Long brandId,
            Long operatorId,
            String installId,
            String deviceFingerprint,
            String extensionVersion,
            String userAgent
    ) {
        ExtensionBindResponse response = doCreateBoundSession(
                operatorId,
                installId,
                deviceFingerprint,
                extensionVersion,
                userAgent
        );
        auditSupport.record(
                "EXTENSION_BIND",
                AuditResult.SUCCESS,
                AuditMode.SYNC,
                true,
                operatorId,
                brandId,
                null,
                null,
                response.sessionId(),
                "EXTENSION_SESSION",
                String.valueOf(response.sessionId()),
                null,
                null,
                detail("installId", installId, "extensionVersion", extensionVersion, "expiresAt", response.expiresAt())
        );
        return response;
    }

    private ExtensionBindResponse doCreateBoundSession(
            Long operatorId,
            String installId,
            String deviceFingerprint,
            String extensionVersion,
            String userAgent
    ) {
        if (operatorId == null || !StringUtils.hasText(installId)) {
            throw new BizException(EXTENSION_BAD_REQUEST, "operatorId and installId are required");
        }
        TokenMaterial token = newToken();
        LocalDateTime now = now();
        LocalDateTime expiresAt = now.plusDays(properties.getLongToken().getTtlDays());
        ExtensionSession session = new ExtensionSession();
        session.setOperatorId(operatorId);
        session.setTokenLookupHash(HashSupport.sha256Hex(token.plaintext()));
        session.setTokenHash(HashSupport.saltedSha256Hex(token.saltHex(), token.plaintext()));
        session.setTokenHashAlg(HashAlgorithm.SHA_256.dbValue());
        session.setTokenSalt(token.saltHex());
        session.setInstallId(installId);
        session.setDeviceFingerprintHash(fingerprintHash(deviceFingerprint));
        session.setDeviceFingerprintHashAlg(HashAlgorithm.SHA_256.dbValue());
        session.setExtensionVersion(extensionVersion);
        session.setUserAgent(userAgent);
        session.setStatus("active");
        session.setBoundAt(now);
        session.setLastSeenAt(now);
        session.setExpiresAt(expiresAt);
        sessionMapper.insert(session);
        return new ExtensionBindResponse(token.plaintext(), expiresAt, session.getId());
    }

    @Transactional
    public ExtensionTokenRefreshResponse validateAndMaybeRenew(
            String plaintextToken,
            String extensionVersion,
            String userAgent
    ) {
        ExtensionSession session = requireActiveSession(plaintextToken);
        String effectiveVersion = StringUtils.hasText(extensionVersion)
                ? extensionVersion
                : session.getExtensionVersion();
        versionService.requireSupported("chrome", effectiveVersion);
        LocalDateTime now = now();
        if (!session.getExpiresAt().isAfter(now)) {
            throw new BizException(EXTENSION_UNAUTHORIZED, "extension token expired");
        }
        sessionMapper.touchActive(session.getId(), now, extensionVersion, userAgent);

        LocalDateTime renewThreshold = now.plusDays(properties.getLongToken().getSlideRenewThresholdDays());
        if (session.getExpiresAt().isAfter(renewThreshold)) {
            auditTokenRefresh(session, false, session.getExpiresAt(), null);
            return new ExtensionTokenRefreshResponse(null, false, session.getExpiresAt(), session.getId());
        }

        sessionMapper.revokeActive(session.getId(), now, session.getOperatorId());
        ExtensionBindResponse renewed = doCreateBoundSession(
                session.getOperatorId(),
                session.getInstallId(),
                null,
                StringUtils.hasText(extensionVersion) ? extensionVersion : session.getExtensionVersion(),
                userAgent
        );
        auditTokenRefresh(session, true, renewed.expiresAt(), renewed.sessionId());
        return new ExtensionTokenRefreshResponse(renewed.token(), true, renewed.expiresAt(), renewed.sessionId());
    }

    public void revoke(Long sessionId, Long operatorId) {
        if (sessionId == null) {
            throw new BizException(EXTENSION_BAD_REQUEST, "sessionId is required");
        }
        ExtensionSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BizException(EXTENSION_NOT_FOUND, "extension session not found");
        }
        if (!session.getOperatorId().equals(operatorId) && !isGlobalRevoker(operatorId)) {
            auditSupport.record(
                    "EXTENSION_TOKEN_REVOKE",
                    AuditResult.DENIED,
                    AuditMode.SYNC,
                    true,
                    operatorId,
                    null,
                    null,
                    null,
                    sessionId,
                    "EXTENSION_SESSION",
                    String.valueOf(sessionId),
                    String.valueOf(EXTENSION_DENIED),
                    "REVOKE_PERMISSION_DENIED",
                    Map.of("sessionOperatorId", session.getOperatorId())
            );
            throw new BizException(EXTENSION_DENIED, "no permission to revoke extension session");
        }
        sessionMapper.revokeActive(sessionId, now(), operatorId);
        auditSupport.record(
                "EXTENSION_TOKEN_REVOKE",
                AuditResult.SUCCESS,
                AuditMode.SYNC,
                true,
                operatorId,
                null,
                null,
                null,
                sessionId,
                "EXTENSION_SESSION",
                String.valueOf(sessionId),
                null,
                null,
                Map.of("sessionOperatorId", session.getOperatorId())
        );
    }

    public ExtensionSession requireActiveSession(String plaintextToken) {
        if (!StringUtils.hasText(plaintextToken) || !plaintextToken.startsWith(TOKEN_PREFIX)) {
            throw new BizException(EXTENSION_UNAUTHORIZED, "extension token invalid");
        }
        ExtensionSession session = sessionMapper.selectActiveByLookupHash(HashSupport.sha256Hex(plaintextToken));
        if (session == null) {
            throw new BizException(EXTENSION_UNAUTHORIZED, "extension token invalid");
        }
        HashAlgorithm algorithm = HashAlgorithm.fromDbValue(session.getTokenHashAlg());
        if (algorithm != HashAlgorithm.SHA_256) {
            throw new IllegalStateException("unsupported token hash algorithm: " + session.getTokenHashAlg());
        }
        String computed = HashSupport.saltedSha256Hex(session.getTokenSalt(), plaintextToken);
        if (!HashSupport.constantTimeEqualsHex(session.getTokenHash(), computed)) {
            throw new BizException(EXTENSION_UNAUTHORIZED, "extension token invalid");
        }
        return session;
    }

    private TokenMaterial newToken() {
        byte[] random = HashSupport.randomBytes(32);
        String plaintext = TOKEN_PREFIX + BASE64_URL_ENCODER.encodeToString(random);
        String saltHex = HexFormat.of().formatHex(HashSupport.randomBytes(16));
        return new TokenMaterial(plaintext, saltHex);
    }

    private String fingerprintHash(String fingerprint) {
        return StringUtils.hasText(fingerprint) ? HashSupport.sha256Hex(fingerprint) : null;
    }

    /**
     * Checks the legacy {@code sys_user.role} column only. Multi-role RBAC via
     * {@code sys_user_role} is intentionally left to a later shared role resolver; revoke is
     * currently limited to self-revoke plus a small set of legacy global roles.
     */
    private boolean isGlobalRevoker(Long operatorId) {
        if (operatorId == null) {
            return false;
        }
        SysUser operator = sysUserMapper.selectById(operatorId);
        if (operator == null || Boolean.FALSE.equals(operator.getIsActive())) {
            return false;
        }
        String role = operator.getRole();
        return role != null && GLOBAL_REVOKE_ROLES.contains(role.trim().toLowerCase());
    }

    private void auditTokenRefresh(ExtensionSession session, boolean renewed, LocalDateTime expiresAt, Long renewedSessionId) {
        auditSupport.record(
                "EXTENSION_TOKEN_REFRESH",
                AuditResult.SUCCESS,
                AuditMode.SYNC,
                true,
                session.getOperatorId(),
                null,
                null,
                null,
                session.getId(),
                "EXTENSION_SESSION",
                String.valueOf(session.getId()),
                null,
                null,
                detail("renewed", renewed, "expiresAt", expiresAt, "renewedSessionId", renewedSessionId)
        );
    }

    private Map<String, Object> detail(Object... values) {
        Map<String, Object> detail = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            detail.put(String.valueOf(values[i]), values[i + 1]);
        }
        return detail;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private record TokenMaterial(String plaintext, String saltHex) {
    }
}
