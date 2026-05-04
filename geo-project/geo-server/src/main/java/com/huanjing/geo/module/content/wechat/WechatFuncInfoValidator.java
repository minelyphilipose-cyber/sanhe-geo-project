package com.huanjing.geo.module.content.wechat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.content.config.WechatOpenPlatformProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class WechatFuncInfoValidator {
    private final ObjectMapper objectMapper;
    private final WechatOpenPlatformProperties properties;

    public boolean hasDraftPermissions(String funcInfoJson) {
        Set<Integer> missing = missingRequired(funcInfoJson);
        if (missing.isEmpty()) {
            return true;
        }
        if (!properties.isFuncScopeStrictMode()) {
            log.warn("WeChat func_info missing scopes {} but strict mode is off, accepting authorizer temporarily", missing);
            return true;
        }
        return false;
    }

    public Set<Integer> missingRequired(String funcInfoJson) {
        Set<Integer> actual = new HashSet<>();
        Set<Integer> required = new HashSet<>(properties.getRequiredDraftFuncScopes());
        if (!StringUtils.hasText(funcInfoJson)) {
            return required;
        }
        try {
            JsonNode root = objectMapper.readTree(funcInfoJson);
            if (root.isArray()) {
                for (JsonNode item : root) {
                    int id = item.path("funcscope_category").path("id").asInt(-1);
                    if (id > 0) {
                        actual.add(id);
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to parse WeChat func_info json, treating as missing all required scopes");
            return required;
        }
        Set<Integer> missing = new HashSet<>(required);
        missing.removeAll(actual);
        return missing;
    }
}
