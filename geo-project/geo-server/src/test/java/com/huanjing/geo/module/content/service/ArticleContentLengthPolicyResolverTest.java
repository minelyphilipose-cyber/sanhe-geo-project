package com.huanjing.geo.module.content.service;

import com.huanjing.geo.module.content.constant.ArticlePromptChannels;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArticleContentLengthPolicyResolverTest {

    private final ArticleContentLengthPolicyResolver resolver = new ArticleContentLengthPolicyResolver();

    @Test
    void douyinAndXiaohongshuOverrideRequestedLength() {
        ArticleContentLengthPolicy douyin = resolver.resolve(
                ArticlePromptChannels.SELF_MEDIA, "douyin_image_text", "long");
        ArticleContentLengthPolicy xiaohongshu = resolver.resolve(
                ArticlePromptChannels.SELF_MEDIA, "xiaohongshu", "long");

        assertThat(douyin.targetMinChars()).isEqualTo(400);
        assertThat(douyin.targetMaxChars()).isEqualTo(700);
        assertThat(douyin.source()).isEqualTo(ArticleContentLengthPolicyResolver.SOURCE_CHANNEL_POLICY);
        assertThat(xiaohongshu.targetMinChars()).isEqualTo(600);
        assertThat(xiaohongshu.targetMaxChars()).isEqualTo(900);
        assertThat(xiaohongshu.source()).isEqualTo(ArticleContentLengthPolicyResolver.SOURCE_CHANNEL_POLICY);
    }

    @Test
    void otherSelfMediaRequireAtLeastTwoThousandCharacters() {
        for (String subCode : ArticlePromptChannels.SELF_MEDIA_SUB_CODES) {
            if ("douyin".equals(subCode) || "xiaohongshu".equals(subCode)) {
                continue;
            }
            ArticleContentLengthPolicy policy = resolver.resolve(
                    ArticlePromptChannels.SELF_MEDIA, subCode, "short");

            assertThat(policy.targetMinChars()).as(subCode).isEqualTo(2000);
            assertThat(policy.targetMaxChars()).as(subCode).isEqualTo(3000);
            assertThat(policy.source()).as(subCode)
                    .isEqualTo(ArticleContentLengthPolicyResolver.SOURCE_CHANNEL_POLICY);
        }
    }

    @Test
    void neutralEducationXiaohongshuUsesTighterLengthPolicy() {
        ArticleContentLengthPolicy policy = resolver.resolve(
                ArticlePromptChannels.SELF_MEDIA, "xiaohongshu", "long", true);

        assertThat(policy.targetMinChars()).isEqualTo(500);
        assertThat(policy.targetMaxChars()).isEqualTo(700);
        assertThat(policy.source()).isEqualTo(ArticleContentLengthPolicyResolver.SOURCE_CHANNEL_POLICY);
    }

    @Test
    void nonSelfMediaKeepRequestedLengthTiers() {
        assertThat(resolver.resolve(ArticlePromptChannels.FORUM, null, "short"))
                .extracting(ArticleContentLengthPolicy::targetMinChars, ArticleContentLengthPolicy::targetMaxChars)
                .containsExactly(500, 700);
        assertThat(resolver.resolve(ArticlePromptChannels.INDUSTRY_SITE, null, "medium"))
                .extracting(ArticleContentLengthPolicy::targetMinChars, ArticleContentLengthPolicy::targetMaxChars)
                .containsExactly(1200, 1800);
        assertThat(resolver.resolve(ArticlePromptChannels.AUTHORITY_MEDIA, "news_source", "long"))
                .extracting(ArticleContentLengthPolicy::targetMinChars, ArticleContentLengthPolicy::targetMaxChars)
                .containsExactly(2500, 3500);
    }
}
