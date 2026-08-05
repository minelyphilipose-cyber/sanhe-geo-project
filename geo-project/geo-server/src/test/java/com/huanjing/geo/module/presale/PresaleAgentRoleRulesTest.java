package com.huanjing.geo.module.presale;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PresaleAgentRoleRulesTest {

    @Test
    void supportsDictionaryKeysAndCustomChineseRoles() {
        assertTrue(PresaleAgentRoleRules.supportsRepresentedBrands("dealer"));
        assertTrue(PresaleAgentRoleRules.supportsRepresentedBrands("汽车品牌代理商"));
        assertTrue(PresaleAgentRoleRules.supportsRepresentedBrands("regional_distributor"));
        assertFalse(PresaleAgentRoleRules.supportsRepresentedBrands("service_provider"));
        assertFalse(PresaleAgentRoleRules.supportsRepresentedBrands(null));
    }
}
