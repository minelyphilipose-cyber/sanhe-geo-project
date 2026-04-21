package com.huanjing.geo.module.presale.ruleengine.builders;

import com.huanjing.geo.module.presale.dto.snapshot.computed.Scores;
import com.huanjing.geo.module.presale.dto.snapshot.raw.SentimentDetail;
import com.huanjing.geo.module.presale.ruleengine.EvidenceDataBuilder;
import com.huanjing.geo.module.presale.ruleengine.RuleBuildInput;
import com.huanjing.geo.module.presale.ruleengine.RuleCodes;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * evidence_data 字段:sentiment_score, positive_count, neutral_count, negative_count
 *
 * 数据来源:
 * - sentiment_score ← L2.scores.sentiment
 * - positive/neutral/negative_count ← L1.sentimentDetail.*
 */
@Component
public class LowSentimentScoreBuilder implements EvidenceDataBuilder {

    @Override
    public String supportRuleCode() {
        return RuleCodes.RULE_LOW_SENTIMENT_SCORE;
    }

    @Override
    public Map<String, Object> build(RuleBuildInput input) {
        Map<String, Object> ev = new LinkedHashMap<>();

        Scores scores = input.getL2().getScores();
        SentimentDetail sd = input.getL1().getSentimentDetail();

        ev.put("sentiment_score",
                Math.round(scores == null || scores.getSentiment() == null ? 0.0 : scores.getSentiment()));
        ev.put("positive_count", sd == null || sd.getPositiveCount() == null ? 0 : sd.getPositiveCount());
        ev.put("neutral_count", sd == null || sd.getNeutralCount() == null ? 0 : sd.getNeutralCount());
        ev.put("negative_count", sd == null || sd.getNegativeCount() == null ? 0 : sd.getNegativeCount());
        return ev;
    }
}
