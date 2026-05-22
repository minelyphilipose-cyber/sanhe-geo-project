package com.huanjing.geo.module.content.service;

import com.huanjing.geo.module.content.constant.ArticlePromptChannels;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@Service
public class QuestionScenePlatformSuggestionService {

    private static final Map<String, List<String>> SUGGESTIONS = Map.of(
            "brand", List.of(
                    key(ArticlePromptChannels.SELF_MEDIA, "baijiahao"),
                    key(ArticlePromptChannels.SELF_MEDIA, "toutiao"),
                    key(ArticlePromptChannels.SELF_MEDIA, "wechat"),
                    key(ArticlePromptChannels.SELF_MEDIA, "netease")
            ),
            "decision", List.of(
                    key(ArticlePromptChannels.SELF_MEDIA, "baijiahao"),
                    key(ArticlePromptChannels.SELF_MEDIA, "zhihu")
            ),
            "deal", List.of(
                    key(ArticlePromptChannels.SELF_MEDIA, "wechat")
            ),
            "compare", List.of(
                    key(ArticlePromptChannels.SELF_MEDIA, "baijiahao"),
                    key(ArticlePromptChannels.SELF_MEDIA, "zhihu")
            ),
            "qa", List.of(
                    key(ArticlePromptChannels.SELF_MEDIA, "zhihu"),
                    key(ArticlePromptChannels.SELF_MEDIA, "douyin"),
                    key(ArticlePromptChannels.SELF_MEDIA, "toutiao")
            ),
            "function", List.of(
                    key(ArticlePromptChannels.SELF_MEDIA, "baijiahao"),
                    key(ArticlePromptChannels.SELF_MEDIA, "douyin")
            )
    );

    public List<String> suggestedPlatformCodes(String questionSceneCode) {
        if (!StringUtils.hasText(questionSceneCode)) {
            return List.of();
        }
        return SUGGESTIONS.getOrDefault(questionSceneCode.trim(), List.of());
    }

    public List<SceneSuggestion> suggestions() {
        return SUGGESTIONS.entrySet().stream()
                .map(entry -> new SceneSuggestion(entry.getKey(), entry.getValue()))
                .toList();
    }

    public static String key(String groupCode, String subCode) {
        String canonicalSubCode = ArticlePromptChannels.canonicalSubCode(groupCode,
                StringUtils.hasText(subCode) ? subCode.trim() : "");
        return groupCode + ":" + (StringUtils.hasText(canonicalSubCode) ? canonicalSubCode : "");
    }

    public record SceneSuggestion(String questionSceneCode, List<String> platformCodes) {
    }
}
