package com.huanjing.geo.module.presale.generate.web;

import com.huanjing.geo.module.dispatch.websearch.enums.IntegrationType;

import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Hard-coded credential egress boundary, checked at readiness and again immediately before each call. */
public final class PresaleWebEndpointPolicy {
    private static final Map<IntegrationType, Set<String>> ALLOWED_HOSTS = Map.of(
            IntegrationType.VOLCENGINE_RESPONSES_WEB, Set.of("ark.cn-beijing.volces.com"),
            IntegrationType.DASHSCOPE_NATIVE_WEB, Set.of("dashscope.aliyuncs.com"),
            IntegrationType.TENCENT_TOKENHUB_RESPONSES_WEB, Set.of("tokenhub.tencentmaas.com"),
            IntegrationType.QIANFAN_ERNIE_CHAT_WEB, Set.of("qianfan.baidubce.com"),
            IntegrationType.MIMO_CHAT_WEB, Set.of("api.xiaomimimo.com"),
            IntegrationType.QIHOO_360_AI_SEARCH_WEB, Set.of("api.360.cn"),
            IntegrationType.ZHIPU_CHAT_WEB, Set.of("open.bigmodel.cn")
    );

    private PresaleWebEndpointPolicy() { }

    public static void validate(IntegrationType type, String endpoint) {
        try {
            URI uri = URI.create(endpoint);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)
                    || !ALLOWED_HOSTS.getOrDefault(type, Set.of()).contains(host)) {
                throw new IllegalArgumentException("endpoint is outside the allowlist for " + type);
            }
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("invalid companion endpoint: " + ex.getMessage());
        }
    }
}
