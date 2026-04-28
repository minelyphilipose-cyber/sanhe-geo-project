package com.huanjing.geo.module.presale.ruleengine.builders;

import com.huanjing.geo.module.presale.dto.snapshot.raw.SentimentDetail;
import com.huanjing.geo.module.presale.ruleengine.EvidenceDataBuilder;
import com.huanjing.geo.module.presale.ruleengine.RuleBuildInput;
import com.huanjing.geo.module.presale.ruleengine.RuleCodes;
import com.huanjing.geo.module.presale.ruleengine.util.TextFormatUtil;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * evidence_data 字段:
 * - key_topic, affected_platform_count, affected_platforms_text, negative_count, negative_evidence_count
 *
 * 数据来源:L1.sentimentDetail
 * - negative_count ← sentimentDetail.negativeCount
 * - negative_evidence_count ← negativeEvidence 条数
 * - key_topic      ← 简化实现:取 negativeEvidence 中首条 snippet 关联的主题
 *                   (L1 DTO 没有 key_topic 字段,由 Builder 从 evidence 归纳,v1 用占位)
 * - affected_platforms_text ← negativeEvidence 中 platformName 去重拼接
 */
@Component
public class NegativeEvidenceBuilder implements EvidenceDataBuilder {

    @Override
    public String supportRuleCode() {
        return RuleCodes.RULE_NEGATIVE_EVIDENCE;
    }

    @Override
    public Map<String, Object> build(RuleBuildInput input) {
        Map<String, Object> ev = new LinkedHashMap<>();

        SentimentDetail sd = input.getL1().getSentimentDetail();
        int negativeCount = sd == null || sd.getNegativeCount() == null ? 0 : sd.getNegativeCount();

        // 受影响平台列表(去重,保持首次出现顺序)
        List<String> platforms = new ArrayList<>();
        Set<String> seen = new TreeSet<>();
        if (sd != null && sd.getNegativeEvidence() != null) {
            for (SentimentDetail.NegativeEvidence ne : sd.getNegativeEvidence()) {
                if (ne == null || ne.getPlatformName() == null) continue;
                if (seen.add(ne.getPlatformName())) {
                    platforms.add(ne.getPlatformName());
                }
            }
        }

        ev.put("key_topic", deriveKeyTopic(sd));
        int evidenceCount = sd == null || sd.getNegativeEvidence() == null ? 0 : sd.getNegativeEvidence().size();

        ev.put("affected_platform_count", platforms.size());
        ev.put("affected_platforms_text", TextFormatUtil.formatPlatformNames(platforms));
        ev.put("negative_count", negativeCount);
        ev.put("negative_evidence_count", evidenceCount);
        return ev;
    }

    /**
     * key_topic 生成简化逻辑:取 top_keywords 中首个 NEGATIVE 关键词,或占位符。
     * 真实实现应依赖 LLM 归纳,v1 此处用最接近的可用字段。
     */
    private String deriveKeyTopic(SentimentDetail sd) {
        if (sd == null || sd.getTopKeywords() == null) return "负面反馈";
        for (SentimentDetail.SentimentKeyword kw : sd.getTopKeywords()) {
            if (kw != null
                    && SentimentDetail.Sentiment.NEGATIVE == kw.getSentiment()
                    && kw.getKeyword() != null) {
                return kw.getKeyword();
            }
        }
        return "负面反馈";
    }
}
