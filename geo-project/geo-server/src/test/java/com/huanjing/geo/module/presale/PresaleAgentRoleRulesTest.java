package com.huanjing.geo.module.presale;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PresaleAgentRoleRulesTest {

    @Test
    void matchesOnlyConfiguredChineseMarkersAfterSpaceNormalization() {
        assertTrue(PresaleAgentRoleRules.supportsRepresentedBrands(" 汽车　经 销 商 "));
        assertTrue(PresaleAgentRoleRules.supportsRepresentedBrands("区域渠道合作商"));
        assertFalse(PresaleAgentRoleRules.supportsRepresentedBrands("dealer"));
        assertFalse(PresaleAgentRoleRules.supportsRepresentedBrands("distributor"));
        assertFalse(PresaleAgentRoleRules.supportsRepresentedBrands("授权服务商"));
    }

    @Test
    void dealerModeRequiresChineseRoleAndAtLeastOneRepresentedBrand() {
        assertTrue(PresaleAgentRoleRules.isDealerMode("品牌代理商", List.of("吉利汽车")));
        assertFalse(PresaleAgentRoleRules.isDealerMode("品牌代理商", List.of()));
        assertFalse(PresaleAgentRoleRules.isDealerMode("dealer", List.of("吉利汽车")));
        assertFalse(PresaleAgentRoleRules.isDealerMode("普通门店", List.of("吉利汽车")));
    }
}
