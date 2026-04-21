package com.huanjing.geo.module.presale.ruleengine.builders;

import com.huanjing.geo.module.presale.dto.snapshot.computed.ComputedSnapshotDTO;
import com.huanjing.geo.module.presale.dto.snapshot.raw.RawSnapshotDTO;
import com.huanjing.geo.module.presale.dto.snapshot.raw.SentimentDetail;
import com.huanjing.geo.module.presale.ruleengine.RuleBuildInput;
import com.huanjing.geo.module.presale.ruleengine.RuleCodes;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NegativeEvidenceBuilderTest {

    @Test
    void supportRuleCode_isCorrect() {
        assertThat(new NegativeEvidenceBuilder().supportRuleCode())
                .isEqualTo(RuleCodes.RULE_NEGATIVE_EVIDENCE);
    }

    @Test
    void build_dedupesAffectedPlatforms() {
        SentimentDetail.NegativeEvidence e1 = SentimentDetail.NegativeEvidence.builder()
                .platformCode("doubao").platformName("豆包").snippet("服务差").build();
        SentimentDetail.NegativeEvidence e2 = SentimentDetail.NegativeEvidence.builder()
                .platformCode("doubao").platformName("豆包").snippet("等位久").build();
        SentimentDetail.NegativeEvidence e3 = SentimentDetail.NegativeEvidence.builder()
                .platformCode("wenxin").platformName("文心一言").snippet("价格高").build();

        SentimentDetail.SentimentKeyword kw = SentimentDetail.SentimentKeyword.builder()
                .keyword("服务质量")
                .sentiment(SentimentDetail.Sentiment.NEGATIVE)
                .build();

        SentimentDetail sd = new SentimentDetail();
        sd.setNegativeCount(3);
        sd.setNegativeEvidence(Arrays.asList(e1, e2, e3));
        sd.setTopKeywords(Arrays.asList(kw));

        RawSnapshotDTO l1 = new RawSnapshotDTO();
        l1.setSentimentDetail(sd);

        Map<String, Object> ev = new NegativeEvidenceBuilder().build(
                RuleBuildInput.builder().l1(l1).l2(new ComputedSnapshotDTO()).build());

        assertThat(ev.get("negative_count")).isEqualTo(3);
        assertThat(ev.get("affected_platform_count")).isEqualTo(2);
        assertThat(ev.get("affected_platforms_text").toString()).contains("豆包", "文心一言");
        assertThat(ev.get("key_topic")).isEqualTo("服务质量");
    }

    @Test
    void build_whenNoSentimentData_usesFallback() {
        RawSnapshotDTO l1 = new RawSnapshotDTO();
        Map<String, Object> ev = new NegativeEvidenceBuilder().build(
                RuleBuildInput.builder().l1(l1).l2(new ComputedSnapshotDTO()).build());

        assertThat(ev.get("negative_count")).isEqualTo(0);
        assertThat(ev.get("affected_platform_count")).isEqualTo(0);
        assertThat(ev.get("affected_platforms_text")).isEqualTo("");
        assertThat(ev.get("key_topic")).isEqualTo("负面反馈");
    }
}
