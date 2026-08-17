package com.huanjing.geo.module.content.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.module.content.dto.SelfMediaRuntimeVersionCoverageVO;
import com.huanjing.geo.module.extension.entity.ExtensionRuntimeStatus;
import com.huanjing.geo.module.extension.entity.LocalAgentRuntimeStatus;
import com.huanjing.geo.module.extension.mapper.ExtensionRuntimeStatusMapper;
import com.huanjing.geo.module.extension.mapper.LocalAgentRuntimeStatusMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SelfMediaRuntimeVersionCoverageService {
    private final ExtensionRuntimeStatusMapper extensionRuntimeStatusMapper;
    private final LocalAgentRuntimeStatusMapper localAgentRuntimeStatusMapper;
    private final CurrentUserService currentUserService;

    public SelfMediaRuntimeVersionCoverageVO coverage(int recentDays) {
        if (!currentUserService.hasPermission("self-media.auth-health.read")
                && !currentUserService.hasPermission("content.read")) {
            throw new com.huanjing.geo.common.exception.BizException(403, "No permission: self-media.auth-health.read");
        }
        LocalDateTime since = LocalDateTime.now().minusDays(Math.max(recentDays, 1));
        List<ExtensionRuntimeStatus> extensions = extensionRuntimeStatusMapper.selectList(
                new LambdaQueryWrapper<ExtensionRuntimeStatus>().ge(ExtensionRuntimeStatus::getLastSeenAt, since));
        List<LocalAgentRuntimeStatus> helpers = localAgentRuntimeStatusMapper.selectList(
                new LambdaQueryWrapper<LocalAgentRuntimeStatus>().ge(LocalAgentRuntimeStatus::getLastSeenAt, since));
        List<ExtensionRuntimeStatus> latestExtensions = latestByIdentity(extensions,
                row -> text(row.getInstallId(), "row:" + row.getId()), ExtensionRuntimeStatus::getLastSeenAt);
        List<LocalAgentRuntimeStatus> latestHelpers = latestByIdentity(helpers,
                row -> text(row.getMachineId(), "row:" + row.getId()), LocalAgentRuntimeStatus::getLastSeenAt);
        return new SelfMediaRuntimeVersionCoverageVO(LocalDateTime.now(), versionCounts(
                latestExtensions.stream().collect(Collectors.groupingBy(row -> versionLabel(
                        row.getExtensionVersion(), row.getCapabilitiesJson()))),
                rows -> rows.stream().map(ExtensionRuntimeStatus::getLastSeenAt).max(LocalDateTime::compareTo).orElse(null)),
                versionCounts(latestHelpers.stream().collect(Collectors.groupingBy(row -> versionLabel(
                                row.getHelperVersion(), row.getCapabilitiesJson()))),
                        rows -> rows.stream().map(LocalAgentRuntimeStatus::getLastSeenAt).max(LocalDateTime::compareTo).orElse(null)),
                latestExtensions.size(), latestHelpers.size());
    }

    private <T> List<T> latestByIdentity(List<T> rows,
                                         java.util.function.Function<T, String> identity,
                                         java.util.function.Function<T, LocalDateTime> seenAt) {
        return rows.stream().collect(Collectors.toMap(identity, row -> row, (left, right) -> {
            LocalDateTime leftAt = seenAt.apply(left);
            LocalDateTime rightAt = seenAt.apply(right);
            if (leftAt == null) return right;
            if (rightAt == null) return left;
            return rightAt.isAfter(leftAt) ? right : left;
        })).values().stream().toList();
    }

    private String versionLabel(String version, String capabilitiesJson) {
        String normalized = text(version, "unknown");
        if (!org.springframework.util.StringUtils.hasText(capabilitiesJson)) return normalized;
        try {
            String revision = JSONUtil.parseObj(capabilitiesJson).getStr("buildRevision");
            return org.springframework.util.StringUtils.hasText(revision)
                    ? normalized + "/" + revision.trim() : normalized;
        } catch (Exception ignored) {
            return normalized;
        }
    }

    private String text(String value, String fallback) {
        return org.springframework.util.StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private <T> List<SelfMediaRuntimeVersionCoverageVO.VersionCount> versionCounts(
            Map<String, List<T>> grouped,
            java.util.function.Function<List<T>, LocalDateTime> latestResolver) {
        return grouped.entrySet().stream()
                .map(entry -> new SelfMediaRuntimeVersionCoverageVO.VersionCount(
                        entry.getKey(), entry.getValue().size(), latestResolver.apply(entry.getValue())))
                .sorted(Comparator.comparingInt(SelfMediaRuntimeVersionCoverageVO.VersionCount::count).reversed())
                .toList();
    }
}
