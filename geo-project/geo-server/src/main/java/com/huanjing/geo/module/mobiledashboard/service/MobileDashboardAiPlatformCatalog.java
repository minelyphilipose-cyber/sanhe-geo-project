package com.huanjing.geo.module.mobiledashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class MobileDashboardAiPlatformCatalog {

    private static final List<String> CANONICAL_ORDER =
            List.of("doubao", "deepseek", "tongyi", "wenxin", "yuanbao");
    private static final Map<String, List<String>> ALIASES = Map.of(
            "doubao", List.of("doubao", "doubao_web"),
            "deepseek", List.of("deepseek", "deepseek_ark_web"),
            "tongyi", List.of("tongyi", "qwen", "qwen_web"),
            "wenxin", List.of("wenxin", "ernie", "wenxin_web"),
            "yuanbao", List.of("yuanbao", "hunyuan", "tencent_search_web")
    );
    private static final Scope LEGACY_SCOPE =
            scopeForCanonicalCodes(List.of("doubao", "deepseek", "tongyi"));

    private final AiPlatformConfigMapper aiPlatformConfigMapper;
    private volatile Scope currentScope = LEGACY_SCOPE;

    @PostConstruct
    public void initialize() {
        refresh();
    }

    @Scheduled(fixedDelayString = "${geo.mobile-dashboard.ai-platform-refresh-ms:30000}")
    public void scheduledRefresh() {
        refresh();
    }

    public void refresh() {
        try {
            List<AiPlatformConfig> rows = aiPlatformConfigMapper.selectList(
                    new LambdaQueryWrapper<AiPlatformConfig>()
                            .eq(AiPlatformConfig::getUsageScene, "QUESTION_POLL_WEB")
                            .eq(AiPlatformConfig::getEnabledForMobileDashboard, true)
            );
            LinkedHashSet<String> visible = new LinkedHashSet<>();
            for (AiPlatformConfig row : rows) {
                String source = StringUtils.hasText(row.getChannelCode())
                        ? row.getChannelCode() : row.getPlatformCode();
                String canonical = canonicalCode(source);
                if (CANONICAL_ORDER.contains(canonical)) {
                    visible.add(canonical);
                }
            }
            currentScope = scopeForCanonicalCodes(visible);
        } catch (RuntimeException ex) {
            log.warn("Failed to refresh mobile dashboard AI platform visibility; retaining previous scope: {}",
                    ex.getMessage());
        }
    }

    public Scope scope() {
        return currentScope;
    }

    public String aliasSql(String platformCode) {
        String canonical = canonicalCode(platformCode);
        return quoted(ALIASES.getOrDefault(canonical, List.of(canonical)));
    }

    public String canonicalSql(String expression) {
        return canonicalSqlExpression(expression);
    }

    public static String canonicalSqlExpression(String expression) {
        return """
                CASE
                    WHEN %1$s IN ('doubao', 'doubao_web') THEN 'doubao'
                    WHEN %1$s IN ('deepseek', 'deepseek_ark_web') THEN 'deepseek'
                    WHEN %1$s IN ('tongyi', 'qwen', 'qwen_web') THEN 'tongyi'
                    WHEN %1$s IN ('wenxin', 'ernie', 'wenxin_web') THEN 'wenxin'
                    WHEN %1$s IN ('yuanbao', 'hunyuan', 'tencent_search_web') THEN 'yuanbao'
                    ELSE NULL
                END
                """.formatted(expression);
    }

    public int order(String platformCode) {
        int index = CANONICAL_ORDER.indexOf(canonicalCode(platformCode));
        return index < 0 ? CANONICAL_ORDER.size() : index;
    }

    public String canonicalCode(String platformCode) {
        String normalized = StringUtils.hasText(platformCode)
                ? platformCode.trim().toLowerCase(Locale.ROOT) : "";
        for (Map.Entry<String, List<String>> entry : ALIASES.entrySet()) {
            if (entry.getValue().contains(normalized)) {
                return entry.getKey();
            }
        }
        return normalized;
    }

    static Scope scopeForCanonicalCodes(Collection<String> codes) {
        Set<String> requested = codes == null ? Set.of() : new LinkedHashSet<>(codes);
        List<String> canonicalCodes = CANONICAL_ORDER.stream()
                .filter(requested::contains)
                .toList();
        List<String> aliases = new ArrayList<>();
        canonicalCodes.forEach(code -> aliases.addAll(ALIASES.getOrDefault(code, List.of(code))));
        return new Scope(canonicalCodes, quoted(aliases));
    }

    private static String quoted(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return "''";
        }
        return values.stream()
                .map(value -> "'" + value.replace("'", "''") + "'")
                .reduce((left, right) -> left + "," + right)
                .orElse("''");
    }

    public record Scope(List<String> canonicalCodes, String aliasSql) {
        public Scope {
            canonicalCodes = canonicalCodes == null ? List.of() : List.copyOf(canonicalCodes);
            aliasSql = StringUtils.hasText(aliasSql) ? aliasSql : "''";
        }
    }
}
