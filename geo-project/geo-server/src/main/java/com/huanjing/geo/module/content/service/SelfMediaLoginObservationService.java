package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.module.content.dto.BrowserEnvironmentLoginStatusRequest;
import com.huanjing.geo.module.content.dto.SelfMediaLoginObservationVO;
import com.huanjing.geo.module.customer.access.BrandAccessAction;
import com.huanjing.geo.module.customer.access.BrandAccessService;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import com.huanjing.geo.module.content.entity.BrowserEnvironment;
import com.huanjing.geo.module.content.entity.BrowserEnvironmentAccount;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.entity.SelfMediaLoginObservation;
import com.huanjing.geo.module.content.mapper.SelfMediaLoginObservationMapper;
import com.huanjing.geo.module.system.service.SystemAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SelfMediaLoginObservationService {
    private static final Set<String> PARSER_FAILURE_RESULTS = Set.of("technical_failure", "identity_mismatch");

    private final SelfMediaLoginObservationMapper observationMapper;
    private final SystemAlertService systemAlertService;
    private final BrandAccessService brandAccessService;
    private final CurrentUserService currentUserService;

    public List<SelfMediaLoginObservationVO> listForAccount(Long brandId, Long accountId, int limit) {
        SysUser operator = currentUserService.requireCurrentUser();
        if (!currentUserService.hasPermission("self-media.auth-health.read")
                && !currentUserService.hasPermission("content.read")) {
            throw new com.huanjing.geo.common.exception.BizException(403, "No permission: self-media.auth-health.read");
        }
        brandAccessService.requireBrandAccess(brandId, operator.getId(), BrandAccessAction.READ);
        return observationMapper.selectList(new LambdaQueryWrapper<SelfMediaLoginObservation>()
                        .eq(SelfMediaLoginObservation::getBrandId, brandId)
                        .eq(SelfMediaLoginObservation::getSelfMediaAccountId, accountId)
                        .orderByDesc(SelfMediaLoginObservation::getCreatedAt, SelfMediaLoginObservation::getId)
                        .last("LIMIT " + Math.max(1, Math.min(limit, 100))))
                .stream().map(SelfMediaLoginObservationVO::from).toList();
    }

    public void record(BrowserEnvironmentAccount binding,
                       BrowserEnvironment environment,
                       SelfMediaAccount account,
                       BrowserEnvironmentLoginStatusRequest request,
                       String observationResult,
                       boolean authoritative,
                       Long operatorId) {
        SelfMediaLoginObservation row = new SelfMediaLoginObservation();
        row.setBrandId(binding == null ? null : binding.getBrandId());
        row.setSelfMediaAccountId(account == null ? null : account.getId());
        row.setBrowserEnvironmentId(environment == null ? null : environment.getId());
        row.setBrowserEnvironmentAccountId(binding == null ? null : binding.getId());
        row.setLoginVerificationId(request.loginVerificationId());
        row.setPlatform(normalize(request.platform()));
        row.setReportSource(normalizeSource(request));
        row.setObservationResult(limit(observationResult, 32));
        row.setResultCode(limit(request.errorCode(), 64));
        row.setResultMessage(limit(request.errorMessage(), 512));
        row.setActualAccountName(limit(request.actualAccountName(), 128));
        row.setActualPlatformAccountId(limit(request.actualPlatformAccountId(), 128));
        row.setCurrentUrl(limit(request.currentUrl(), 1024));
        row.setExtensionVersion(limit(request.extensionVersion(), 32));
        row.setProtocolVersion(limit(request.protocolVersion(), 32));
        row.setAuthoritative(authoritative);
        row.setOperatorId(operatorId);
        row.setCreatedAt(LocalDateTime.now());
        observationMapper.insert(row);
    }

    public PlatformCircuitStatus platformCircuitStatus(String platform,
                                                       int windowMinutes,
                                                       int minimumSamples,
                                                       double failureRatioThreshold,
                                                       int minimumAffectedBrands) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(Math.max(windowMinutes, 1));
        List<SelfMediaLoginObservation> rows = observationMapper.selectList(
                new LambdaQueryWrapper<SelfMediaLoginObservation>()
                        .eq(SelfMediaLoginObservation::getPlatform, normalize(platform))
                        .ge(SelfMediaLoginObservation::getCreatedAt, since)
                        .orderByDesc(SelfMediaLoginObservation::getCreatedAt));
        int samples = rows.size();
        int failures = (int) rows.stream()
                .filter(row -> PARSER_FAILURE_RESULTS.contains(row.getObservationResult()))
                .count();
        long affectedBrands = rows.stream()
                .filter(row -> PARSER_FAILURE_RESULTS.contains(row.getObservationResult()))
                .map(SelfMediaLoginObservation::getBrandId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .count();
        double ratio = samples == 0 ? 0D : (double) failures / samples;
        boolean open = samples >= Math.max(minimumSamples, 1)
                && ratio >= Math.max(0D, Math.min(failureRatioThreshold, 1D))
                && affectedBrands >= Math.max(minimumAffectedBrands, 1);
        String normalizedPlatform = normalize(platform);
        reconcilePlatformIncident(normalizedPlatform, open, samples, failures, affectedBrands, ratio);
        return new PlatformCircuitStatus(normalizedPlatform, open, samples, failures,
                affectedBrands, ratio, since, LocalDateTime.now());
    }

    private void reconcilePlatformIncident(String platform,
                                           boolean open,
                                           int samples,
                                           int failures,
                                           long affectedBrands,
                                           double ratio) {
        String dedupeKey = "self_media_identity_parser:" + platform;
        if (!open) {
            systemAlertService.resolveOpenByDedupeKey(dedupeKey, null);
            return;
        }
        systemAlertService.createOrRefreshRecipientAlert(
                "SELF_MEDIA_IDENTITY_PARSER_HEALTH",
                "high",
                "self_media_identity_parser",
                platform + "身份识别异常率升高，已暂停据此形成账号负向结论",
                java.util.Map.of(
                        "platform", platform,
                        "sampleCount", samples,
                        "failureCount", failures,
                        "affectedBrandCount", affectedBrands,
                        "failureRatio", ratio,
                        "customerActionRequired", false,
                        "route", "/admin/content/account-auth-health"),
                null,
                "delivery_manager",
                dedupeKey);
    }

    private String normalizeSource(BrowserEnvironmentLoginStatusRequest request) {
        if (StringUtils.hasText(request.reportSource())) return normalize(request.reportSource());
        if (request.loginVerificationId() != null) return "verification_task";
        return "passive_page_observation";
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "unknown";
    }

    private String limit(String value, int max) {
        if (!StringUtils.hasText(value)) return null;
        String text = value.trim();
        return text.length() > max ? text.substring(0, max) : text;
    }

    public record PlatformCircuitStatus(
            String platform,
            boolean open,
            int sampleCount,
            int failureCount,
            long affectedBrandCount,
            double failureRatio,
            LocalDateTime windowStartedAt,
            LocalDateTime evaluatedAt
    ) {
    }
}
