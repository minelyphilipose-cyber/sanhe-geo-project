package com.huanjing.geo.module.presale.ruleengine.builders;

import com.huanjing.geo.module.presale.dto.snapshot.raw.PlatformBreakdown;
import com.huanjing.geo.module.presale.dto.snapshot.raw.TestSummary;
import com.huanjing.geo.module.presale.ruleengine.EvidenceDataBuilder;
import com.huanjing.geo.module.presale.ruleengine.RuleBuildInput;
import com.huanjing.geo.module.presale.ruleengine.RuleCodes;
import com.huanjing.geo.module.presale.ruleengine.util.TextFormatUtil;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * evidence_data 字段:
 * effective_platforms, degraded_count, degraded_platforms_text
 *
 * <p><b>Degraded 来源:与规则 trigger 表达式保持一致,以 testSummary 为权威源</b>
 * (Codex P1·E 审阅 P2-2 修复)。规则 SpEL 是:</p>
 * <pre>
 *   (#l1.testSummary.totalPlatforms - #l1.testSummary.degradedPlatforms.size()) &lt; 8
 * </pre>
 * <p>若 Builder 从 platformBreakdown 独立统计 is_degraded,会在
 * platformBreakdown 被上游过滤/截断时与规则触发口径偏离,造成 evidence 误导。</p>
 *
 * <p>策略:</p>
 * <ol>
 *   <li>以 {@code testSummary.degradedPlatforms}(List&lt;String&gt; code) 为权威集合</li>
 *   <li>从 {@code platformBreakdown} 建立 code → name 映射(仅为文本展示服务)</li>
 *   <li>映射缺失时兜底:用 code 字符串本身作为 name</li>
 *   <li>testSummary.degradedPlatforms 为 null 时才回退到 platformBreakdown.isDegraded 聚合</li>
 * </ol>
 */
@Component
public class PlatformCountLowBuilder implements EvidenceDataBuilder {

    @Override
    public String supportRuleCode() {
        return RuleCodes.RULE_PLATFORM_COUNT_LOW;
    }

    @Override
    public Map<String, Object> build(RuleBuildInput input) {
        Map<String, Object> ev = new LinkedHashMap<>();

        TestSummary ts = input.getL1().getTestSummary();
        List<PlatformBreakdown> breakdown = input.getL1().getPlatformBreakdown();

        int totalPlatforms = ts == null || ts.getTotalPlatforms() == null ? 0 : ts.getTotalPlatforms();

        // 权威 degraded code 列表
        List<String> degradedCodes;
        if (ts != null && ts.getDegradedPlatforms() != null) {
            degradedCodes = ts.getDegradedPlatforms();
        } else {
            // 回退:testSummary 缺失时从 breakdown 聚合(兼容性路径)
            degradedCodes = collectDegradedCodesFromBreakdown(breakdown);
        }

        int degradedCount = degradedCodes.size();
        int effective = Math.max(0, totalPlatforms - degradedCount);

        // 把 degraded code 转成 platform_name 用于展示
        List<String> degradedNames = resolveCodesToNames(degradedCodes, breakdown);

        ev.put("effective_platforms", effective);
        ev.put("degraded_count", degradedCount);
        ev.put("degraded_platforms_text", TextFormatUtil.formatPlatformNames(degradedNames));
        return ev;
    }

    /**
     * testSummary 缺失时的兜底:从 platformBreakdown.isDegraded 收集 code。
     */
    private List<String> collectDegradedCodesFromBreakdown(List<PlatformBreakdown> breakdown) {
        List<String> codes = new ArrayList<>();
        if (breakdown == null) return codes;
        for (PlatformBreakdown p : breakdown) {
            if (p == null) continue;
            if (Boolean.TRUE.equals(p.getIsDegraded()) && p.getPlatformCode() != null) {
                codes.add(p.getPlatformCode());
            }
        }
        return codes;
    }

    /**
     * 把 code 列表翻译为 name 列表,顺序与输入一致。
     * breakdown 中找不到对应 code 时,用 code 本身兜底(避免展示空白)。
     */
    private List<String> resolveCodesToNames(List<String> codes, List<PlatformBreakdown> breakdown) {
        if (codes == null || codes.isEmpty()) return Collections.emptyList();

        Map<String, String> codeToName = new HashMap<>();
        if (breakdown != null) {
            for (PlatformBreakdown p : breakdown) {
                if (p == null || p.getPlatformCode() == null) continue;
                codeToName.put(p.getPlatformCode(), p.getPlatformName());
            }
        }

        List<String> names = new ArrayList<>(codes.size());
        for (String code : codes) {
            String name = codeToName.get(code);
            names.add(name != null ? name : code); // 映射缺失兜底
        }
        return names;
    }
}
