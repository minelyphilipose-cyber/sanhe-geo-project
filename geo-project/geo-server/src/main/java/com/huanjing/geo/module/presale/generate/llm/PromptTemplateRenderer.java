package com.huanjing.geo.module.presale.generate.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PromptTemplateRenderer {

    public String render(String promptTemplate,
                         String promptCode,
                         Integer batchNo,
                         String brand,
                         String industry,
                         String industryRole,
                         String region,
                         String competitor) {
        String content = promptTemplate == null ? "" : promptTemplate;
        if (Integer.valueOf(1).equals(batchNo) && content.contains("{competitor}")) {
            log.warn("Prompt template contains {competitor} in batch1, promptCode={}", promptCode);
        }
        return content
                .replace("{brand}", safe(brand))
                .replace("{industry}", safe(industry))
                .replace("{industry_role}", safe(industryRole))
                .replace("{region}", safe(region))
                .replace("{product}", "")
                .replace("{competitor}", safe(competitor));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}

