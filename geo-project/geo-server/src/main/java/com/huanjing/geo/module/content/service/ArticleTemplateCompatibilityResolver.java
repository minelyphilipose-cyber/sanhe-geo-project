package com.huanjing.geo.module.content.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
public class ArticleTemplateCompatibilityResolver {

    public List<ArticleTemplateAllocationService.TemplateWithVersion> preferredCandidates(
            List<ArticleTemplateAllocationService.TemplateWithVersion> templates,
            String requestedSceneCode) {
        if (templates == null || templates.isEmpty()) {
            return List.of();
        }
        String requested = trimToNull(requestedSceneCode);
        if (requested != null) {
            List<ArticleTemplateAllocationService.TemplateWithVersion> exact = templates.stream()
                    .filter(item -> requested.equals(trimToNull(item.template().getQuestionSceneCode())))
                    .toList();
            if (!exact.isEmpty()) {
                return exact;
            }
        }
        List<ArticleTemplateAllocationService.TemplateWithVersion> general = templates.stream()
                .filter(item -> trimToNull(item.template().getQuestionSceneCode()) == null)
                .toList();
        return general.isEmpty() ? templates : general;
    }

    public ArticleTemplateCompatibilityLevel level(String requestedSceneCode, String templateSceneCode) {
        String requested = trimToNull(requestedSceneCode);
        String template = trimToNull(templateSceneCode);
        if (requested != null && requested.equals(template)) {
            return ArticleTemplateCompatibilityLevel.EXACT;
        }
        if (template == null) {
            return ArticleTemplateCompatibilityLevel.GENERAL;
        }
        return ArticleTemplateCompatibilityLevel.CARRIER_ONLY;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
