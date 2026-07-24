package com.huanjing.geo.module.project.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.llm.LlmCallFacade;
import com.huanjing.geo.common.llm.LlmCallRequest;
import com.huanjing.geo.common.llm.LlmCallResult;
import com.huanjing.geo.common.llm.LlmModelConfig;
import com.huanjing.geo.common.llm.router.LlmFeature;
import com.huanjing.geo.common.llm.router.LlmRouteRequest;
import com.huanjing.geo.common.llm.router.LlmRouteResult;
import com.huanjing.geo.module.project.entity.BaselineQuestionSnapshot;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.presale.generate.PresaleEvaluationModelRouter;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
class BaselineSemanticJudgeService {
    private static final List<String> NEGATIVE_WORDS = List.of("不推荐", "差评", "投诉", "风险", "负面", "不靠谱", "谨慎", "骗子", "欺诈", "违规", "价格高", "售后差");
    private static final Set<String> SENTIMENTS = Set.of("POSITIVE", "NEUTRAL", "NEGATIVE", "UNKNOWN");
    private static final Set<String> IMPRESSIONS = Set.of("POSITIVE", "NEUTRAL", "NEGATIVE", "INFO_MISSING", "NO_AWARENESS");
    private static final Set<String> BRAND_MENTION_TYPES = Set.of("BRAND_EXACT", "BRAND_ALIAS", "BRAND_SEMANTIC", "NONE");
    private static final Set<String> COMPETITOR_MENTION_TYPES = Set.of("COMPETITOR_EXACT", "COMPETITOR_ALIAS", "COMPETITOR_SEMANTIC");

    private final LlmCallFacade llmCallFacade;
    private final PresaleEvaluationModelRouter evaluationModelRouter;
    private final ObjectMapper objectMapper;

    BaselineSemanticJudgeResult judge(BaselineQuestionSnapshot question,
                                      Project project,
                                      List<String> aliases,
                                      List<BaselineObservationScoringRules.CompetitorName> competitors,
                                      AiPlatformConfig judgePlatform,
                                      String responseText) {
        BaselineSemanticJudgeResult fallback = fallbackRuleResult(question, project, aliases, competitors, responseText);
        if (!StringUtils.hasText(responseText)) {
            return fallback;
        }
        List<AiPlatformConfig> judgePlatforms = evaluationModelRouter.routePlatforms().stream()
                .map(this::useEvaluationLowModel)
                .toList();
        if (judgePlatforms.isEmpty()) {
            return fallback;
        }
        try {
            JsonNode payload = invokeJudge(question, project, aliases, competitors, judgePlatforms, responseText);
            return mergeJudgePayload(fallback, payload, competitors, responseText);
        } catch (Exception ex) {
            fallback.setJudgeError(ex.getMessage());
            log.debug("Baseline semantic judge fallback to rule result, baselineQuestion={}, platform={}, reason={}",
                    question.getId(), judgePlatform == null ? null : judgePlatform.getPlatformCode(), ex.getMessage());
            return fallback;
        }
    }

    private JsonNode invokeJudge(BaselineQuestionSnapshot question,
                                 Project project,
                                 List<String> aliases,
                                 List<BaselineObservationScoringRules.CompetitorName> competitors,
                                 List<AiPlatformConfig> judgePlatforms,
                                 String responseText) throws Exception {
        String prompt = buildPrompt(question, project, aliases, competitors, responseText);
        LlmCallResult callResult = llmCallFacade.execute(LlmCallRequest.routed(new LlmRouteRequest(
                LlmFeature.BASELINE,
                "你是严格的 GEO 基线语义裁判。只输出合法 JSON,不要输出 markdown 或解释。",
                prompt,
                0D,
                10_000,
                45_000,
                LlmModelConfig.LONG_FORM_MAX_REQUEST_TIMEOUT_MS,
                0,
                1200,
                true,
                1,
                0,
                judgePlatforms,
                true
        )));
        LlmRouteResult result = callResult.routeResult();
        JsonNode node = objectMapper.readTree(result.responseText());
        if (node == null || !node.isObject()) {
            throw new IllegalStateException("BASELINE_JUDGE_RESPONSE_NOT_OBJECT");
        }
        return node;
    }

    private AiPlatformConfig useEvaluationLowModel(AiPlatformConfig platform) {
        if (platform != null && StringUtils.hasText(platform.getLowModelId())) {
            platform.setModelId(platform.getLowModelId().trim());
        }
        return platform;
    }

    private String buildPrompt(BaselineQuestionSnapshot question,
                               Project project,
                               List<String> aliases,
                               List<BaselineObservationScoringRules.CompetitorName> competitors,
                               String responseText) throws Exception {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("question", question.getQuestionText());
        input.put("intent_type", question.getIntentType());
        input.put("brand", Map.of(
                "canonical_name", safe(project.getBrandName()),
                "aliases", aliases == null ? List.of() : aliases
        ));
        input.put("competitors", competitors == null ? List.of() : competitors.stream()
                .map(item -> Map.of(
                        "canonical_name", item.name(),
                        "aliases", item.aliases() == null ? List.of() : item.aliases(),
                        "tracked", item.tracked()
                ))
                .toList());
        input.put("answer", responseText);
        return """
                请判断下面 AI 回答中目标品牌和竞品的出现、推荐、情感与认知状态。

                输入:
                %s

                只输出 JSON,结构必须为:
                {
                  "brand_mentioned": true|false,
                  "brand_mention_type": "BRAND_EXACT|BRAND_ALIAS|BRAND_SEMANTIC|NONE",
                  "brand_evidence_text": string|null,
                  "recommended": true|false,
                  "ranking_position": number|null,
                  "sentiment": "POSITIVE|NEUTRAL|NEGATIVE|UNKNOWN",
                  "impression_state": "POSITIVE|NEUTRAL|NEGATIVE|INFO_MISSING|NO_AWARENESS",
                  "judge_evidence": string,
                  "competitor_mentions": [
                    {
                      "canonical_name": "必须使用输入 competitors 里的 canonical_name",
                      "mention_type": "COMPETITOR_EXACT|COMPETITOR_ALIAS|COMPETITOR_SEMANTIC",
                      "evidence_text": string|null
                    }
                  ],
                  "negative_evidence_texts": [string]
                }

                规则:
                1. brand_evidence_text/evidence_text 必须来自回答原文;如果只是语义指代且找不到原文片段,填 null 并使用 *_SEMANTIC。
                2. recommended 表示 AI 把目标品牌作为建议项、可选项或更优项,不是单纯提到。
                3. sentiment 只评价明确提到目标品牌的回答;没有明确提到时用 UNKNOWN。
                4. INFO_MISSING 表示回答说信息不足、未检索到或无法确认;NO_AWARENESS 表示明确表示不了解目标品牌。
                5. competitor_mentions 只能包含输入竞品,不能发明新竞品。
                6. 只有回答明确对目标品牌/门店给出不利评价、风险提示、投诉、骗局、违规、价格明显偏高、不推荐等负面判断时, sentiment 才能为 NEGATIVE, 并必须在 negative_evidence_texts 中给出回答原文片段。竞品优势、中性问题说明、“用户遇到问题可获得支持”、“这个问题没有绝对答案”不算负面。
                """.formatted(objectMapper.writeValueAsString(input));
    }

    private BaselineSemanticJudgeResult mergeJudgePayload(BaselineSemanticJudgeResult fallback,
                                                          JsonNode payload,
                                                          List<BaselineObservationScoringRules.CompetitorName> competitors,
                                                          String responseText) {
        BaselineSemanticJudgeResult result = fallback;
        result.setJudgeUsed(true);
        result.setMentioned(payload.path("brand_mentioned").asBoolean(fallback.isMentioned()) || fallback.isMentioned());
        result.setRecommended(payload.path("recommended").asBoolean(fallback.isRecommended()));
        result.setRankingPosition(payload.path("ranking_position").isNumber() ? payload.path("ranking_position").asInt() : fallback.getRankingPosition());
        result.setSentiment(normalize(payload.path("sentiment").asText(null), SENTIMENTS, fallback.getSentiment()));
        result.setImpressionState(normalize(payload.path("impression_state").asText(null), IMPRESSIONS, fallback.getImpressionState()));
        result.setMentionType(resolveJudgeMentionType(fallback, payload, responseText));
        result.setJudgeEvidence(StringUtils.hasText(payload.path("judge_evidence").asText(null))
                ? payload.path("judge_evidence").asText()
                : fallback.getJudgeEvidence());
        if (!result.isMentioned()) {
            result.setRecommended(false);
            result.setRankingPosition(null);
            result.setSentiment("UNKNOWN");
        }
        mergeBrandHit(result, payload.path("brand_evidence_text").asText(null), responseText);
        mergeCompetitorHits(result, payload.path("competitor_mentions"), competitors, responseText);
        mergeNegativeHits(result, payload.path("negative_evidence_texts"), responseText);
        if ("NEGATIVE".equals(result.getSentiment()) && result.getNegativeHits().isEmpty()) {
            result.setSentiment("NEUTRAL");
            if ("NEGATIVE".equals(result.getImpressionState())) {
                result.setImpressionState("NEUTRAL");
            }
        }
        return result;
    }

    private String resolveJudgeMentionType(BaselineSemanticJudgeResult fallback, JsonNode payload, String responseText) {
        if (fallback.getBrandHit() != null && "BRAND_EXACT".equals(fallback.getBrandHit().getHitType())) {
            return "BRAND_EXACT";
        }
        if (fallback.getBrandHit() != null && "BRAND_ALIAS".equals(fallback.getBrandHit().getHitType())) {
            return "BRAND_ALIAS";
        }
        String judgeType = normalize(payload.path("brand_mention_type").asText(null), BRAND_MENTION_TYPES, fallback.getMentionType());
        String evidence = payload.path("brand_evidence_text").asText(null);
        if (StringUtils.hasText(evidence) && responseText.contains(evidence.trim())) {
            return judgeType == null || "NONE".equals(judgeType) ? "BRAND_SEMANTIC" : judgeType;
        }
        return Boolean.TRUE.equals(payload.path("brand_mentioned").asBoolean(false)) ? "BRAND_SEMANTIC" : fallback.getMentionType();
    }

    private void mergeBrandHit(BaselineSemanticJudgeResult result, String evidence, String responseText) {
        if (result.getBrandHit() != null || !StringUtils.hasText(evidence)) {
            return;
        }
        BaselineSemanticJudgeResult.EntityHit hit = locate(null, null, evidence.trim(), "BRAND_SEMANTIC", true, responseText);
        if (hit != null) {
            result.setBrandHit(hit);
        }
    }

    private void mergeCompetitorHits(BaselineSemanticJudgeResult result,
                                     JsonNode mentions,
                                     List<BaselineObservationScoringRules.CompetitorName> competitors,
                                     String responseText) {
        if (mentions == null || !mentions.isArray() || competitors == null || competitors.isEmpty()) {
            return;
        }
        Map<String, BaselineObservationScoringRules.CompetitorName> competitorByName = new LinkedHashMap<>();
        for (BaselineObservationScoringRules.CompetitorName competitor : competitors) {
            competitorByName.put(competitor.name(), competitor);
        }
        Set<String> existing = new LinkedHashSet<>();
        for (BaselineSemanticJudgeResult.EntityHit hit : result.getCompetitorHits()) {
            existing.add(hit.getCanonicalName());
        }
        for (JsonNode node : mentions) {
            String canonicalName = node.path("canonical_name").asText(null);
            BaselineObservationScoringRules.CompetitorName competitor = competitorByName.get(canonicalName);
            if (competitor == null || existing.contains(competitor.name())) {
                continue;
            }
            String type = normalize(node.path("mention_type").asText(null), COMPETITOR_MENTION_TYPES, "COMPETITOR_SEMANTIC");
            String evidence = node.path("evidence_text").asText(null);
            BaselineSemanticJudgeResult.EntityHit hit = locate(competitor.id(), competitor.name(),
                    StringUtils.hasText(evidence) ? evidence.trim() : competitor.name(), type, competitor.tracked(), responseText);
            if (hit == null) {
                hit = new BaselineSemanticJudgeResult.EntityHit();
                hit.setEntityId(competitor.id());
                hit.setCanonicalName(competitor.name());
                hit.setRawText(StringUtils.hasText(evidence) ? evidence.trim() : competitor.name());
                hit.setHitType("COMPETITOR_SEMANTIC");
                hit.setTracked(competitor.tracked());
            }
            result.getCompetitorHits().add(hit);
            existing.add(competitor.name());
        }
    }

    private void mergeNegativeHits(BaselineSemanticJudgeResult result, JsonNode texts, String responseText) {
        if (texts == null || !texts.isArray()) {
            return;
        }
        for (JsonNode node : texts) {
            String text = node.asText(null);
            BaselineSemanticJudgeResult.EntityHit hit = locate(null, null, text, "NEGATIVE", false, responseText);
            if (hit != null) {
                result.getNegativeHits().add(hit);
            }
        }
    }

    private BaselineSemanticJudgeResult fallbackRuleResult(BaselineQuestionSnapshot question,
                                                           Project project,
                                                           List<String> aliases,
                                                           List<BaselineObservationScoringRules.CompetitorName> competitors,
                                                           String responseText) {
        BaselineObservationScoringResult score = BaselineObservationScoringRules.score(
                responseText, question.getIntentType(), project.getBrandName(), aliases);
        BaselineSemanticJudgeResult result = new BaselineSemanticJudgeResult();
        result.setMentioned(score.isMentioned());
        result.setRecommended(score.isRecommended());
        result.setRankingPosition(score.getRankingPosition());
        result.setSentiment(score.getSentiment());
        result.setImpressionState(score.getImpressionState());
        result.setMentionType(score.getMentionType());
        result.setJudgeEvidence(score.getJudgeEvidence());
        result.setBrandHit(firstBrandHit(project.getBrandName(), aliases, responseText));
        result.setCompetitorHits(ruleCompetitorHits(competitors, responseText));
        result.setNegativeHits(ruleNegativeHits(responseText));
        return result;
    }

    private BaselineSemanticJudgeResult.EntityHit firstBrandHit(String brandName, List<String> aliases, String responseText) {
        BaselineSemanticJudgeResult.EntityHit exact = locate(null, brandName, brandName, "BRAND_EXACT", true, responseText);
        if (exact != null) {
            return exact;
        }
        if (aliases != null) {
            for (String alias : aliases) {
                BaselineSemanticJudgeResult.EntityHit hit = locate(null, brandName, alias, "BRAND_ALIAS", true, responseText);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }

    private List<BaselineSemanticJudgeResult.EntityHit> ruleCompetitorHits(List<BaselineObservationScoringRules.CompetitorName> competitors,
                                                                           String responseText) {
        if (competitors == null || competitors.isEmpty()) {
            return List.of();
        }
        List<BaselineSemanticJudgeResult.EntityHit> hits = new ArrayList<>();
        for (BaselineObservationScoringRules.CompetitorName competitor : competitors) {
            for (String term : competitor.matchTerms()) {
                BaselineSemanticJudgeResult.EntityHit hit = locate(competitor.id(), competitor.name(), term,
                        term.equals(competitor.name()) ? "COMPETITOR_EXACT" : "COMPETITOR_ALIAS",
                        competitor.tracked(), responseText);
                if (hit != null) {
                    hits.add(hit);
                    break;
                }
            }
        }
        return hits;
    }

    private List<BaselineSemanticJudgeResult.EntityHit> ruleNegativeHits(String responseText) {
        if (!StringUtils.hasText(responseText)) {
            return List.of();
        }
        List<BaselineSemanticJudgeResult.EntityHit> hits = new ArrayList<>();
        String lower = responseText.toLowerCase(Locale.ROOT);
        for (String word : NEGATIVE_WORDS) {
            int start = lower.indexOf(word.toLowerCase(Locale.ROOT));
            if (start >= 0) {
                BaselineSemanticJudgeResult.EntityHit hit = new BaselineSemanticJudgeResult.EntityHit();
                hit.setRawText(responseText.substring(start, Math.min(responseText.length(), start + word.length())));
                hit.setHitType("NEGATIVE");
                hit.setStartOffset(start);
                hit.setEndOffset(start + word.length());
                hits.add(hit);
                break;
            }
        }
        return hits;
    }

    private BaselineSemanticJudgeResult.EntityHit locate(Long entityId,
                                                         String canonicalName,
                                                         String rawText,
                                                         String hitType,
                                                         boolean tracked,
                                                         String responseText) {
        if (!StringUtils.hasText(rawText) || !StringUtils.hasText(responseText)) {
            return null;
        }
        String term = rawText.trim();
        int start = responseText.indexOf(term);
        if (start < 0) {
            return null;
        }
        BaselineSemanticJudgeResult.EntityHit hit = new BaselineSemanticJudgeResult.EntityHit();
        hit.setEntityId(entityId);
        hit.setCanonicalName(StringUtils.hasText(canonicalName) ? canonicalName : term);
        hit.setRawText(term);
        hit.setHitType(hitType);
        hit.setTracked(tracked);
        hit.setMentionCount(countOccurrences(responseText, term));
        hit.setStartOffset(start);
        hit.setEndOffset(start + term.length());
        return hit;
    }

    private int countOccurrences(String text, String term) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(term)) {
            return 0;
        }
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(term, index)) >= 0) {
            count++;
            index += term.length();
        }
        return Math.max(count, 1);
    }

    private String normalize(String value, Set<String> allowed, String fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return allowed.contains(normalized) ? normalized : fallback;
    }

    private String safe(String text) {
        return text == null ? "" : text;
    }
}
