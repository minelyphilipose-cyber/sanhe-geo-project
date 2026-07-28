package com.huanjing.geo.module.mobiledashboard.service;

import com.huanjing.geo.common.llm.router.LlmPlatformCodeFilters;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Data
@Component
@ConfigurationProperties(prefix = "geo.mobile-dashboard.entity-judge")
public class MobileEntityJudgeRuntimeConfig {
    private boolean enabled = false;
    private int maxProjectsPerRun = 20;
    private int perProjectLimit = 10;
    private long workerMs = 60_000L;
    private String platformCodes = "deepseek,doubao,qwen,wenxin";

    public Set<String> platformCodeSet() {
        return LlmPlatformCodeFilters.parseCodes(platformCodes);
    }

    public List<String> platformCodeList() {
        return platformCodeSet().stream().toList();
    }
}
