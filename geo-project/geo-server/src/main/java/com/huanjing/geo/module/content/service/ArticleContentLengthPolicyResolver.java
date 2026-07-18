package com.huanjing.geo.module.content.service;

import com.huanjing.geo.module.content.constant.ArticlePromptChannels;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ArticleContentLengthPolicyResolver {

    public static final String SOURCE_CHANNEL_POLICY = "channel_policy";
    public static final String SOURCE_REQUESTED_LENGTH = "requested_length";

    public ArticleContentLengthPolicy resolve(String channelGroupCode,
                                              String channelSubCode,
                                              String requestedLengthCode) {
        String requested = normalizeLengthCode(requestedLengthCode);
        String subCode = ArticlePromptChannels.canonicalSubCode(channelGroupCode, channelSubCode);
        if (ArticlePromptChannels.SELF_MEDIA.equals(channelGroupCode)) {
            return switch (subCode == null ? "" : subCode) {
                case "douyin" -> policy(requested, 400, 700, SOURCE_CHANNEL_POLICY);
                case "xiaohongshu" -> policy(requested, 600, 900, SOURCE_CHANNEL_POLICY);
                default -> policy(requested, 2000, 3000, SOURCE_CHANNEL_POLICY);
            };
        }
        return switch (requested) {
            case "short" -> policy(requested, 500, 700, SOURCE_REQUESTED_LENGTH);
            case "long" -> policy(requested, 2500, 3500, SOURCE_REQUESTED_LENGTH);
            default -> policy(requested, 1200, 1800, SOURCE_REQUESTED_LENGTH);
        };
    }

    public String promptRequirement(ArticleContentLengthPolicy policy) {
        if (policy.targetMinChars() == 2000 && policy.targetMaxChars() == 3000) {
            return "正文不含标题不少于2000字，通常控制在2000～3000字；不要通过重复观点、堆砌套话或无关背景凑字数。";
        }
        return "正文不含标题控制在约" + policy.targetMinChars() + "～" + policy.targetMaxChars()
                + "字；在篇幅范围内优先保证核心问题讲清楚、前后逻辑完整。";
    }

    private ArticleContentLengthPolicy policy(String requested, int min, int max, String source) {
        return new ArticleContentLengthPolicy(requested, min, max, source);
    }

    private String normalizeLengthCode(String requestedLengthCode) {
        if (!StringUtils.hasText(requestedLengthCode)) {
            return "medium";
        }
        return switch (requestedLengthCode.trim().toLowerCase()) {
            case "short" -> "short";
            case "long" -> "long";
            default -> "medium";
        };
    }
}
