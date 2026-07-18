package com.huanjing.geo.module.content.service;

import com.huanjing.geo.module.content.constant.ArticlePromptChannels;
import com.huanjing.geo.module.content.constant.TemplatePerspectiveCodes;
import com.huanjing.geo.module.content.entity.ArticlePromptTemplate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArticleRuntimePolicyResolverTest {

    private final ArticleRuntimePolicyResolver resolver = new ArticleRuntimePolicyResolver();

    @Test
    void resolvesChannelDefaults() {
        assertPolicy(ArticlePromptChannels.AGENT_SITE, TemplatePerspectiveCodes.CUSTOMER, "none", false);
        assertPolicy(ArticlePromptChannels.SELF_MEDIA, TemplatePerspectiveCodes.CUSTOMER, "none", false);
        assertPolicy(ArticlePromptChannels.INDUSTRY_SITE, TemplatePerspectiveCodes.INDUSTRY_NEUTRAL, "brand_only", false);
        assertPolicy(ArticlePromptChannels.FORUM, TemplatePerspectiveCodes.REVIEW_RECOMMEND, "full", true);
        assertPolicy(ArticlePromptChannels.AUTHORITY_MEDIA, TemplatePerspectiveCodes.INDUSTRY_NEUTRAL, "brand_only", false);
    }

    @Test
    void thirdPartySelfMediaUsesSoftHintAndExplicitFullAllowsContact() {
        ArticleRuntimePolicy matrix = resolver.resolve(null, ArticlePromptChannels.SELF_MEDIA, "wechat",
                TemplatePerspectiveCodes.INDUSTRY_NEUTRAL);
        assertEquals("soft_hint", matrix.contactDisclosureMode());
        assertFalse(matrix.allowContactInfo());

        ArticlePromptTemplate template = new ArticlePromptTemplate();
        template.setContactDisclosureMode("full");
        ArticleRuntimePolicy official = resolver.resolve(template, ArticlePromptChannels.SELF_MEDIA, "wechat",
                TemplatePerspectiveCodes.CUSTOMER);
        assertEquals("full", official.contactDisclosureMode());
        assertTrue(official.allowContactInfo());
    }

    @Test
    void authorityMediaForcesBrandOnlyEvenWhenTemplateRequestsFull() {
        ArticlePromptTemplate template = new ArticlePromptTemplate();
        template.setContactDisclosureMode("full");

        ArticleRuntimePolicy policy = resolver.resolve(template, ArticlePromptChannels.AUTHORITY_MEDIA,
                "news_source", TemplatePerspectiveCodes.INDUSTRY_NEUTRAL);

        assertEquals("brand_only", policy.contactDisclosureMode());
        assertFalse(policy.allowContactInfo());
    }

    private void assertPolicy(String group, String perspective, String contactMode, boolean allowContact) {
        ArticleRuntimePolicy policy = resolver.resolve(null, group, null, resolver.defaultPerspective(group));
        assertEquals(perspective, policy.perspectiveCode());
        assertEquals(contactMode, policy.contactDisclosureMode());
        assertEquals(allowContact, policy.allowContactInfo());
    }
}
