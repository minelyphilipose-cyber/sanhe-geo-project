package com.huanjing.geo.common.llm.router;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Data
@Component
@ConfigurationProperties(prefix = "geo.llm.routing")
public class LlmRoutingRuntimeConfig {
    private String articleExcludedPlatformCodes = "hunyuan,yuanbao";

    public Set<String> articleExcludedPlatformCodeSet() {
        return LlmPlatformCodeFilters.parseCodes(articleExcludedPlatformCodes);
    }

    public List<String> articleExcludedPlatformCodeList() {
        return articleExcludedPlatformCodeSet().stream().toList();
    }
}
