package com.huanjing.geo.module.presale.ruleengine.builders;

import com.huanjing.geo.module.presale.dto.snapshot.raw.SentimentDetail;
import com.huanjing.geo.module.presale.ruleengine.EvidenceDataBuilder;
import com.huanjing.geo.module.presale.ruleengine.RuleBuildInput;
import com.huanjing.geo.module.presale.ruleengine.RuleCodes;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class BrandSentimentSampleThinBuilder implements EvidenceDataBuilder {

    @Override
    public String supportRuleCode() {
        return RuleCodes.RULE_BRAND_SENTIMENT_SAMPLE_THIN;
    }

    @Override
    public Map<String, Object> build(RuleBuildInput input) {
        SentimentDetail detail = input == null || input.getL1() == null ? null : input.getL1().getSentimentDetail();
        int positive = detail == null || detail.getPositiveCount() == null ? 0 : detail.getPositiveCount();
        int neutral = detail == null || detail.getNeutralCount() == null ? 0 : detail.getNeutralCount();
        int negative = detail == null || detail.getNegativeCount() == null ? 0 : detail.getNegativeCount();

        Map<String, Object> ev = new LinkedHashMap<>();
        ev.put("brand_sentiment_sample_count", positive + neutral + negative);
        ev.put("positive_count", positive);
        ev.put("neutral_count", neutral);
        ev.put("negative_count", negative);
        return ev;
    }
}
