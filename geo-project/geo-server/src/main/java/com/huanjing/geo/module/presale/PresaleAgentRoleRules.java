package com.huanjing.geo.module.presale;

import java.util.List;

/**
 * 售前报告代理/经销类身份识别规则。
 *
 * <p>只识别中文展示名称。字典 key、英文代码、拼音均不参与判断。</p>
 */
public final class PresaleAgentRoleRules {

    private static final List<String> AGENT_ROLE_MARKERS = List.of("代理", "经销", "渠道", "加盟");

    private PresaleAgentRoleRules() {
    }

    public static boolean supportsRepresentedBrands(String industryRoleName) {
        if (industryRoleName == null || industryRoleName.isBlank()) {
            return false;
        }
        String normalized = industryRoleName.trim().replace(" ", "").replace("　", "");
        return AGENT_ROLE_MARKERS.stream().anyMatch(normalized::contains);
    }

    public static boolean isDealerMode(String industryRoleName, List<String> representedBrands) {
        return supportsRepresentedBrands(industryRoleName)
                && representedBrands != null
                && representedBrands.stream().anyMatch(value -> value != null && !value.isBlank());
    }
}
