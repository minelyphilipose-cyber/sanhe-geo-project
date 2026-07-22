package com.huanjing.geo.module.content.service;

import com.huanjing.geo.module.content.constant.ArticlePromptChannels;
import com.huanjing.geo.module.content.constant.TemplatePerspectiveCodes;
import com.huanjing.geo.module.content.entity.ArticlePromptTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Set;

@Component
public class ArticleRuntimePolicyResolver {

    public static final String CONTACT_NONE = "none";
    public static final String CONTACT_SOFT_HINT = "soft_hint";
    public static final String CONTACT_BRAND_ONLY = "brand_only";
    public static final String CONTACT_FULL = "full";

    private static final Set<String> CONTACT_MODES = Set.of(
            CONTACT_NONE, CONTACT_SOFT_HINT, CONTACT_BRAND_ONLY, CONTACT_FULL
    );

    public ArticleRuntimePolicy resolve(ArticlePromptTemplate template,
                                        String channelGroupCode,
                                        String channelSubCode,
                                        String resolvedPerspectiveCode) {
        String group = trim(channelGroupCode);
        String sub = ArticlePromptChannels.canonicalSubCode(group, trimToNull(channelSubCode));
        String perspective = StringUtils.hasText(resolvedPerspectiveCode)
                ? TemplatePerspectiveCodes.normalize(resolvedPerspectiveCode)
                : defaultPerspective(group);
        String mode = resolveContactMode(template, group, sub);
        return new ArticleRuntimePolicy(group, sub, perspective, mode, CONTACT_FULL.equals(mode));
    }

    public String defaultPerspective(String channelGroupCode) {
        return switch (trim(channelGroupCode)) {
            case ArticlePromptChannels.INDUSTRY_SITE, ArticlePromptChannels.AUTHORITY_MEDIA ->
                    TemplatePerspectiveCodes.INDUSTRY_NEUTRAL;
            case ArticlePromptChannels.FORUM -> TemplatePerspectiveCodes.REVIEW_RECOMMEND;
            default -> TemplatePerspectiveCodes.CUSTOMER;
        };
    }

    private String resolveContactMode(ArticlePromptTemplate template, String group, String sub) {
        if (ArticlePromptChannels.AUTHORITY_MEDIA.equals(group)) {
            return CONTACT_BRAND_ONLY;
        }
        if (ArticlePromptChannels.SELF_MEDIA.equals(group) && !"wechat".equals(sub)) {
            return CONTACT_NONE;
        }
        String configured = normalizeMode(template == null ? null : template.getContactDisclosureMode());
        if (configured != null) {
            return configured;
        }
        return switch (group) {
            case ArticlePromptChannels.SELF_MEDIA -> CONTACT_NONE;
            case ArticlePromptChannels.INDUSTRY_SITE -> CONTACT_BRAND_ONLY;
            case ArticlePromptChannels.FORUM -> CONTACT_FULL;
            default -> CONTACT_NONE;
        };
    }

    private String normalizeMode(String value) {
        String normalized = trimToNull(value);
        return normalized != null && CONTACT_MODES.contains(normalized) ? normalized : null;
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
