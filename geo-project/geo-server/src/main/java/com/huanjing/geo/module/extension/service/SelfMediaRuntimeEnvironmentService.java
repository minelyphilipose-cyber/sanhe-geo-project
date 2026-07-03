package com.huanjing.geo.module.extension.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.module.extension.config.SelfMediaRuntimeProperties;
import com.huanjing.geo.module.extension.dto.RuntimeReadinessQuery;
import com.huanjing.geo.module.extension.dto.RuntimeReadinessResult;
import com.huanjing.geo.module.extension.dto.SelfMediaRuntimeEnvironmentBaseRow;
import com.huanjing.geo.module.extension.dto.SelfMediaRuntimeEnvironmentVO;
import com.huanjing.geo.module.extension.entity.ExtensionRuntimeStatus;
import com.huanjing.geo.module.extension.entity.LocalAgentRuntimeStatus;
import com.huanjing.geo.module.extension.mapper.ExtensionRuntimeStatusMapper;
import com.huanjing.geo.module.extension.mapper.LocalAgentRuntimeStatusMapper;
import com.huanjing.geo.module.extension.mapper.SelfMediaRuntimeEnvironmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SelfMediaRuntimeEnvironmentService {

    private final SelfMediaRuntimeEnvironmentMapper runtimeEnvironmentMapper;
    private final ExtensionRuntimeStatusMapper extensionRuntimeStatusMapper;
    private final LocalAgentRuntimeStatusMapper localAgentRuntimeStatusMapper;
    private final SelfMediaRuntimeReadinessService readinessService;
    private final SelfMediaRuntimeProperties runtimeProperties;

    public Page<SelfMediaRuntimeEnvironmentVO> pageRuntimeEnvironments(Long brandId,
                                                                       String platform,
                                                                       Boolean ready,
                                                                       String blockedReason,
                                                                       String keyword,
                                                                       long page,
                                                                       long size) {
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), 100);
        String normalizedPlatform = normalize(platform);
        String normalizedKeyword = trimToNull(keyword);
        String normalizedBlockedReason = trimToNull(blockedReason);

        List<SelfMediaRuntimeEnvironmentBaseRow> baseRows =
                runtimeEnvironmentMapper.selectRuntimeEnvironmentRows(brandId, normalizedPlatform, normalizedKeyword);
        Map<Long, LocalAgentRuntimeStatus> helperByBrand = new HashMap<>();
        Map<String, ExtensionRuntimeStatus> extensionByEnvironmentPlatform = new HashMap<>();
        List<SelfMediaRuntimeEnvironmentVO> matched = new ArrayList<>();
        for (SelfMediaRuntimeEnvironmentBaseRow row : baseRows) {
            LocalAgentRuntimeStatus helper = helperByBrand.computeIfAbsent(
                    row.getBrandId(),
                    id -> localAgentRuntimeStatusMapper.selectLatestByBrandId(id)
            );
            String extensionKey = row.getBrowserEnvironmentId() + ":" + normalize(row.getPlatform());
            ExtensionRuntimeStatus extension = extensionByEnvironmentPlatform.computeIfAbsent(
                    extensionKey,
                    ignored -> latestExtension(row.getBrowserEnvironmentId(), row.getPlatform())
            );
            RuntimeReadinessResult readiness = readinessService.evaluate(new RuntimeReadinessQuery(
                    row.getBrandId(),
                    helper == null ? null : helper.getOperatorId(),
                    helper == null ? null : helper.getSessionId(),
                    row.getBrowserEnvironmentId(),
                    row.getPlatform(),
                    "claim",
                    "fill"
            ));
            String gateMode = runtimeProperties.getGate().modeFor(row.getBrandId(), row.getPlatform());
            SelfMediaRuntimeEnvironmentVO vo = toVO(row, extension, helper, readiness, gateMode);
            if (ready != null && readiness.ready() != ready) {
                continue;
            }
            if (StringUtils.hasText(normalizedBlockedReason)
                    && readiness.blockedReasons().stream().noneMatch(reason -> equalsIgnoreCase(reason, normalizedBlockedReason))) {
                continue;
            }
            matched.add(vo);
        }

        long total = matched.size();
        int from = (int) Math.min((safePage - 1) * safeSize, total);
        int to = (int) Math.min(from + safeSize, total);
        Page<SelfMediaRuntimeEnvironmentVO> result = new Page<>(safePage, safeSize, total);
        result.setRecords(matched.subList(from, to));
        return result;
    }

    private ExtensionRuntimeStatus latestExtension(Long browserEnvironmentId, String platform) {
        if (browserEnvironmentId == null) {
            return null;
        }
        List<ExtensionRuntimeStatus> rows = extensionRuntimeStatusMapper.selectLatestByEnvironmentAndPlatform(
                browserEnvironmentId,
                normalize(platform)
        );
        return rows == null || rows.isEmpty() ? null : rows.get(0);
    }

    private SelfMediaRuntimeEnvironmentVO toVO(SelfMediaRuntimeEnvironmentBaseRow row,
                                               ExtensionRuntimeStatus extension,
                                               LocalAgentRuntimeStatus helper,
                                               RuntimeReadinessResult readiness,
                                               String gateMode) {
        return new SelfMediaRuntimeEnvironmentVO(
                row.getBrandId(),
                row.getBrandName(),
                row.getPlatform(),
                row.getSelfMediaAccountId(),
                row.getAccountName(),
                row.getPlatformAccountId(),
                row.getBrowserEnvironmentId(),
                row.getEnvironmentName(),
                row.getEnvironmentKey(),
                row.getProviderProfileId(),
                row.getBrowserEnvironmentAccountId(),
                row.getLoginStatus(),
                row.getExpectedAccountName(),
                row.getExpectedPlatformAccountId(),
                extensionStatus(extension),
                helperStatus(helper),
                new SelfMediaRuntimeEnvironmentVO.ReadinessStatus(
                        readiness.ready(),
                        readiness.blockedReasons(),
                        readiness.retryAfterSeconds(),
                        gateMode
                )
        );
    }

    private SelfMediaRuntimeEnvironmentVO.ExtensionStatus extensionStatus(ExtensionRuntimeStatus extension) {
        if (extension == null) {
            return null;
        }
        return new SelfMediaRuntimeEnvironmentVO.ExtensionStatus(
                extension.getInstallId(),
                extension.getExtensionVersion(),
                extension.getProtocolVersion(),
                extension.getLastSeenAt(),
                extension.getRuntimeStage(),
                extension.getRuntimeStageMessage(),
                extension.getLastErrorCode(),
                extension.getLastErrorMessage()
        );
    }

    private SelfMediaRuntimeEnvironmentVO.HelperStatus helperStatus(LocalAgentRuntimeStatus helper) {
        if (helper == null) {
            return null;
        }
        return new SelfMediaRuntimeEnvironmentVO.HelperStatus(
                helper.getSessionId(),
                helper.getMachineId(),
                helper.getActiveProfile(),
                helper.getHelperVersion(),
                helper.getProtocolVersion(),
                helper.getAdspowerApiOk(),
                helper.getRunningTaskCount(),
                helper.getCapacity(),
                helper.getLastSeenAt(),
                helper.getLastErrorCode(),
                helper.getLastErrorMessage()
        );
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : null;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }
}
