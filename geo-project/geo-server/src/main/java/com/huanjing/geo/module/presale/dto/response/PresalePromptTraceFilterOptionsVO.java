package com.huanjing.geo.module.presale.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PresalePromptTraceFilterOptionsVO {
    private List<PlatformOption> platforms;
    private List<String> categories;

    @Data
    @Builder
    public static class PlatformOption {
        private String label;
        private String value;
    }
}
