package com.huanjing.geo.module.presale.generate.llm;

import com.huanjing.geo.module.presale.persist.entity.PresaleReport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PromptTemplateRenderer {

    public String render(String promptTemplate, RenderVariables vars) {
        String content = promptTemplate == null ? "" : promptTemplate;
        if (vars != null && Integer.valueOf(1).equals(vars.batchNo()) && content.contains("{competitor}")) {
            log.warn("Prompt template contains {competitor} in batch1");
        }
        return content
                .replace("{brand}", safe(vars == null ? null : vars.brand()))
                .replace("{product}", safe(vars == null ? null : vars.product()))
                .replace("{industry}", safe(vars == null ? null : vars.industry()))
                .replace("{industry_role}", safe(vars == null ? null : vars.industryRole()))
                .replace("{region}", safe(vars == null ? null : vars.region()))
                .replace("{user_type}", safe(vars == null ? null : vars.userType()))
                .replace("{competitor}", safe(vars == null ? null : vars.competitor()));
    }

    /**
     * Compatibility adapter for older tests/callers. Generation code should use
     * {@link #render(String, RenderVariables)} so the renderer stays decoupled
     * from persistence entities.
     */
    @Deprecated
    public String render(String promptTemplate,
                         String promptCode,
                         PlatformCallContext ctx,
                         PresaleReport report) {
        return render(promptTemplate, variables(ctx, report));
    }

    public RenderVariables variables(PlatformCallContext ctx, PresaleReport report) {
        String brand = report == null ? null : report.getBrandName();
        return new RenderVariables(
                ctx == null ? null : ctx.batchNo(),
                brand,
                brand,
                report == null ? null : report.getIndustry(),
                report == null ? null : report.getIndustryRole(),
                report == null ? null : report.getRegion(),
                report == null ? null : report.getUserType(),
                ctx == null ? null : ctx.competitorName()
        );
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public record RenderVariables(Integer batchNo,
                                  String brand,
                                  String product,
                                  String industry,
                                  String industryRole,
                                  String region,
                                  String userType,
                                  String competitor) {
    }
}
