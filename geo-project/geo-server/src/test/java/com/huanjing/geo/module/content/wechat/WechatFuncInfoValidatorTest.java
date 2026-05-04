package com.huanjing.geo.module.content.wechat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.content.config.WechatOpenPlatformProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WechatFuncInfoValidatorTest {

    @Test
    void strictModeAcceptsWhenRequiredScopesExist() {
        WechatFuncInfoValidator validator = validator(true, List.of(1, 13));
        String funcInfo = """
                [{"funcscope_category":{"id":1}},{"funcscope_category":{"id":13}},{"funcscope_category":{"id":99}}]
                """;

        assertThat(validator.hasDraftPermissions(funcInfo)).isTrue();
        assertThat(validator.missingRequired(funcInfo)).isEmpty();
    }

    @Test
    void strictModeRejectsMissingScopes() {
        WechatFuncInfoValidator validator = validator(true, List.of(1, 13));
        String funcInfo = """
                [{"funcscope_category":{"id":1}}]
                """;

        assertThat(validator.hasDraftPermissions(funcInfo)).isFalse();
        assertThat(validator.missingRequired(funcInfo)).containsExactly(13);
    }

    @Test
    void nonStrictModeWarnsButAcceptsMissingScopes() {
        WechatFuncInfoValidator validator = validator(false, List.of(1, 13));

        assertThat(validator.hasDraftPermissions("[]")).isTrue();
        assertThat(validator.missingRequired("[]")).containsExactlyInAnyOrder(1, 13);
    }

    @Test
    void invalidJsonTreatsAllRequiredScopesAsMissing() {
        WechatFuncInfoValidator validator = validator(true, List.of(1, 13));

        assertThat(validator.hasDraftPermissions("not-json")).isFalse();
        assertThat(validator.missingRequired("not-json")).containsExactlyInAnyOrder(1, 13);
    }

    private WechatFuncInfoValidator validator(boolean strictMode, List<Integer> requiredScopes) {
        WechatOpenPlatformProperties properties = new WechatOpenPlatformProperties();
        properties.setFuncScopeStrictMode(strictMode);
        properties.setRequiredDraftFuncScopes(requiredScopes);
        return new WechatFuncInfoValidator(new ObjectMapper(), properties);
    }
}
