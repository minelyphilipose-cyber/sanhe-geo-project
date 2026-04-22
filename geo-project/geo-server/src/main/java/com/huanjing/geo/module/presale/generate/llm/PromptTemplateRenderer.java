package com.huanjing.geo.module.presale.generate.llm;

import com.huanjing.geo.module.presale.persist.entity.PresaleReport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PromptTemplateRenderer {

    public String render(String promptTemplate,
                         String promptCode,
                         PlatformCallContext ctx,
                         PresaleReport report) {
        String content = promptTemplate == null ? "" : promptTemplate;
        if (Integer.valueOf(1).equals(ctx.batchNo()) && content.contains("{competitor}")) {
            log.warn("Prompt template contains {competitor} in batch1, promptCode={}", promptCode);
        }
        return content
                .replace("{brand}", safe(report == null ? null : report.getBrandName()))
                .replace("{industry}", safe(report == null ? null : report.getIndustry()))
                .replace("{industry_role}", safe(report == null ? null : report.getIndustryRole()))
                .replace("{region}", safe(report == null ? null : report.getRegion()))
                .replace("{product}", "")
                .replace("{competitor}", safe(ctx == null ? null : ctx.competitorName()));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
