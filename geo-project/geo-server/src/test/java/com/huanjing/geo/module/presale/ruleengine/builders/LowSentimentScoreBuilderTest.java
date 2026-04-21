package com.huanjing.geo.module.presale.ruleengine.builders;

import com.huanjing.geo.module.presale.dto.snapshot.computed.ComputedSnapshotDTO;
import com.huanjing.geo.module.presale.dto.snapshot.computed.Scores;
import com.huanjing.geo.module.presale.dto.snapshot.raw.RawSnapshotDTO;
import com.huanjing.geo.module.presale.dto.snapshot.raw.SentimentDetail;
import com.huanjing.geo.module.presale.ruleengine.RuleBuildInput;
import com.huanjing.geo.module.presale.ruleengine.RuleCodes;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LowSentimentScoreBuilderTest {

    @Test
    void supportRuleCode_isCorrect() {
        assertThat(new LowSentimentScoreBuilder().supportRuleCode())
                .isEqualTo(RuleCodes.RULE_LOW_SENTIMENT_SCORE);
    }

    @Test
    void build_populatesAllCounts() {
        Scores s = new Scores();
        s.setSentiment(55.0);
        ComputedSnapshotDTO l2 = new ComputedSnapshotDTO();
        l2.setScores(s);

        SentimentDetail sd = new SentimentDetail();
        sd.setPositiveCount(100);
        sd.setNeutralCount(150);
        sd.setNegativeCount(25);
        RawSnapshotDTO l1 = new RawSnapshotDTO();
        l1.setSentimentDetail(sd);

        Map<String, Object> ev = new LowSentimentScoreBuilder().build(
                RuleBuildInput.builder().l1(l1).l2(l2).build());

        assertThat(ev.get("sentiment_score")).isEqualTo(55L);
        assertThat(ev.get("positive_count")).isEqualTo(100);
        assertThat(ev.get("neutral_count")).isEqualTo(150);
        assertThat(ev.get("negative_count")).isEqualTo(25);
    }

    @Test
    void build_whenScoresMissing_returnsZero() {
        Map<String, Object> ev = new LowSentimentScoreBuilder().build(
                RuleBuildInput.builder().l1(new RawSnapshotDTO()).l2(new ComputedSnapshotDTO()).build());

        assertThat(ev.get("sentiment_score")).isEqualTo(0L);
        assertThat(ev.get("positive_count")).isEqualTo(0);
        assertThat(ev.get("neutral_count")).isEqualTo(0);
        assertThat(ev.get("negative_count")).isEqualTo(0);
    }
}
