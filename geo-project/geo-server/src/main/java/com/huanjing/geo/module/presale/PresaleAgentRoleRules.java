package com.huanjing.geo.module.presale;

import java.util.List;
import java.util.Locale;

/**
 * 售前报告代理/经销类身份识别规则。
 *
 * <p>身份既可能是字典 key，也可能是前端 allow-create 输入的展示文本，因此同时识别
 * 中英文常见渠道身份关键词。</p>
 */
public final class PresaleAgentRoleRules {

    private static final List<String> AGENT_ROLE_MARKERS = List.of(
            "代理", "经销", "分销", "渠道", "授权", "加盟",
            "dealer", "agent", "distributor", "reseller", "franchise"
    );

    private PresaleAgentRoleRules() {
    }

    public static boolean supportsRepresentedBrands(String industryRole) {
        if (industryRole == null || industryRole.isBlank()) {
            return false;
        }
        String normalized = industryRole.trim().toLowerCase(Locale.ROOT);
        return AGENT_ROLE_MARKERS.stream().anyMatch(normalized::contains);
    }
}
