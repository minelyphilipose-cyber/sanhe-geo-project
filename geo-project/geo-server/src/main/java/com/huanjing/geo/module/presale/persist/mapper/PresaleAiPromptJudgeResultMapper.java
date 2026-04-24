package com.huanjing.geo.module.presale.persist.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiPromptJudgeResult;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PresaleAiPromptJudgeResultMapper extends BaseMapper<PresaleAiPromptJudgeResult> {

    @Insert("INSERT INTO presale_ai_prompt_judge_result (" +
            "prompt_result_id, version_id, batch_no, platform_code, prompt_template_id, category, competitor_name, " +
            "judge_status, judge_attempt_count, judge_model_id, judge_temperature, judge_error, " +
            "sentiment, sentiment_score, attribute_hit_rate, tone, " +
            "preferred_brand, target_sentiment, reasoning_quality, " +
            "attributes_hit, factual_errors, target_advantages, target_disadvantages, competitor_advantages, " +
            "judge_payload_json, raw_judge_response" +
            ") VALUES (" +
            "#{row.promptResultId}, #{row.versionId}, #{row.batchNo}, #{row.platformCode}, #{row.promptTemplateId}, #{row.category}, #{row.competitorName}, " +
            "#{row.judgeStatus}, #{row.judgeAttemptCount}, #{row.judgeModelId}, #{row.judgeTemperature}, #{row.judgeError}, " +
            "#{row.sentiment}, #{row.sentimentScore}, #{row.attributeHitRate}, #{row.tone}, " +
            "#{row.preferredBrand}, #{row.targetSentiment}, #{row.reasoningQuality}, " +
            "#{row.attributesHit}, #{row.factualErrors}, #{row.targetAdvantages}, #{row.targetDisadvantages}, #{row.competitorAdvantages}, " +
            "#{row.judgePayloadJson}, #{row.rawJudgeResponse}" +
            ") ON DUPLICATE KEY UPDATE " +
            "version_id = VALUES(version_id), " +
            "batch_no = VALUES(batch_no), " +
            "platform_code = VALUES(platform_code), " +
            "prompt_template_id = VALUES(prompt_template_id), " +
            "category = VALUES(category), " +
            "competitor_name = VALUES(competitor_name), " +
            "judge_status = VALUES(judge_status), " +
            "judge_attempt_count = VALUES(judge_attempt_count), " +
            "judge_model_id = VALUES(judge_model_id), " +
            "judge_temperature = VALUES(judge_temperature), " +
            "judge_error = VALUES(judge_error), " +
            "sentiment = VALUES(sentiment), " +
            "sentiment_score = VALUES(sentiment_score), " +
            "attribute_hit_rate = VALUES(attribute_hit_rate), " +
            "tone = VALUES(tone), " +
            "preferred_brand = VALUES(preferred_brand), " +
            "target_sentiment = VALUES(target_sentiment), " +
            "reasoning_quality = VALUES(reasoning_quality), " +
            "attributes_hit = VALUES(attributes_hit), " +
            "factual_errors = VALUES(factual_errors), " +
            "target_advantages = VALUES(target_advantages), " +
            "target_disadvantages = VALUES(target_disadvantages), " +
            "competitor_advantages = VALUES(competitor_advantages), " +
            "judge_payload_json = VALUES(judge_payload_json), " +
            "raw_judge_response = VALUES(raw_judge_response), " +
            "updated_at = CURRENT_TIMESTAMP")
    int upsertByPromptResultId(@Param("row") PresaleAiPromptJudgeResult row);
}
