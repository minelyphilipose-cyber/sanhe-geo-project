package com.huanjing.geo.module.content.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionScenePlatformSuggestionServiceTest {

    private final QuestionScenePlatformSuggestionService service = new QuestionScenePlatformSuggestionService();

    @Test
    void dealSceneOnlySuggestsWechatButDoesNotLockSelection() {
        assertThat(service.suggestedPlatformCodes("deal"))
                .containsExactly("self_media:wechat");
    }

    @Test
    void brandSceneSuggestsNarrativeAndSearchFriendlySelfMediaPlatforms() {
        assertThat(service.suggestedPlatformCodes("brand"))
                .containsExactly("self_media:baijiahao", "self_media:toutiao", "self_media:wechat", "self_media:netease");
    }

    @Test
    void qaSceneUsesCurrentSelfMediaPlatformCodes() {
        assertThat(service.suggestedPlatformCodes("qa"))
                .containsExactly("self_media:zhihu", "self_media:douyin", "self_media:toutiao");
    }

    @Test
    void functionSceneUsesCurrentSelfMediaPlatformCodes() {
        assertThat(service.suggestedPlatformCodes("function"))
                .containsExactly("self_media:baijiahao", "self_media:douyin");
    }

    @Test
    void keyCanonicalizesLegacyDouyinImageTextCode() {
        assertThat(QuestionScenePlatformSuggestionService.key("self_media", "douyin_image_text"))
                .isEqualTo("self_media:douyin");
    }
}
