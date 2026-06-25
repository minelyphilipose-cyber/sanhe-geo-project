package com.huanjing.geo.module.content.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.llm.LlmCallFacade;
import com.huanjing.geo.common.llm.LlmCallRequest;
import com.huanjing.geo.module.customer.entity.BrandOffering;
import com.huanjing.geo.module.customer.mapper.BrandOfferingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class BrandOfferingPromptSelector {

    private static final String SELECT_SYSTEM_PROMPT = """
            你是品牌产品资料选择器。根据文章主题、用户问题、文章类型和渠道，从候选产品/服务项目/特色业务项中选择 0-3 个最相关条目。
            只返回 JSON，不输出解释。
            如果没有明显相关项，返回空数组。
            输出格式：{"selectedIds":[1,2],"reason":"简短原因"}
            """;

    private final BrandOfferingMapper offeringMapper;
    private final ArticleModelResolver modelResolver;
    private final LlmCallFacade llmCallFacade;
    private final ObjectMapper objectMapper;

    public SelectionResult select(Long brandId,
                                  String topic,
                                  String topicAsQuestion,
                                  String articleType,
                                  String contentStyle) {
        if (brandId == null) {
            return SelectionResult.empty();
        }
        List<BrandOffering> candidates = offeringMapper.selectList(new LambdaQueryWrapper<BrandOffering>()
                .eq(BrandOffering::getBrandId, brandId)
                .eq(BrandOffering::getStatus, "active")
                .isNull(BrandOffering::getDeletedAt)
                .orderByAsc(BrandOffering::getPriority, BrandOffering::getId));
        if (candidates.isEmpty()) {
            return SelectionResult.empty();
        }
        List<Long> modelSelectedIds = selectWithModel(candidates, topic, topicAsQuestion, articleType, contentStyle);
        List<Long> selectedIds = modelSelectedIds.isEmpty()
                ? fallbackSelect(candidates, topic, topicAsQuestion)
                : modelSelectedIds;
        List<SelectedOffering> selected = candidates.stream()
                .filter(item -> selectedIds.contains(item.getId()))
                .limit(3)
                .map(this::toSelected)
                .toList();
        return selected.isEmpty() ? SelectionResult.empty() : new SelectionResult(selected);
    }

    private List<Long> selectWithModel(List<BrandOffering> candidates,
                                       String topic,
                                       String topicAsQuestion,
                                       String articleType,
                                       String contentStyle) {
        try {
            ArticleModelResolver.ModelSelection model = modelResolver.resolve(null, null, SELECT_SYSTEM_PROMPT, false);
            String prompt = buildSelectionPrompt(candidates, topic, topicAsQuestion, articleType, contentStyle);
            String response = llmCallFacade.execute(LlmCallRequest.direct(prompt, model.config())).invokeResult().responseText();
            JsonNode root = objectMapper.readTree(extractJson(response));
            JsonNode ids = root.get("selectedIds");
            if (ids == null || !ids.isArray()) {
                return List.of();
            }
            Set<Long> candidateIds = candidates.stream().map(BrandOffering::getId).collect(java.util.stream.Collectors.toSet());
            List<Long> result = new ArrayList<>();
            ids.forEach(node -> {
                long id = node.asLong(0L);
                if (id > 0 && candidateIds.contains(id) && !result.contains(id)) {
                    result.add(id);
                }
            });
            return result.stream().limit(3).toList();
        } catch (Exception ex) {
            log.debug("brand offering model selection failed, fallback to keyword matching: {}", ex.getMessage());
            return List.of();
        }
    }

    private String buildSelectionPrompt(List<BrandOffering> candidates,
                                        String topic,
                                        String topicAsQuestion,
                                        String articleType,
                                        String contentStyle) {
        List<Map<String, Object>> summaries = candidates.stream()
                .limit(50)
                .map(item -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", item.getId());
                    row.put("name", item.getOfferingName());
                    row.put("aliases", parseAliases(item.getOfferingAliasesJson()));
                    row.put("targetUsers", safe(item.getTargetUsers()));
                    row.put("useScenarios", safe(item.getUseScenarios()));
                    row.put("priority", item.getPriority());
                    return row;
                })
                .toList();
        return """
                【文章任务】
                主题：%s
                用户问题：%s
                文章类型：%s
                渠道风格：%s

                【候选产品/服务项目/特色业务项】
                %s

                请选出与本篇最相关的 0-3 个 id。
                """.formatted(
                safe(topic),
                safe(topicAsQuestion),
                safe(articleType),
                safe(contentStyle),
                JSONUtil.toJsonStr(summaries)
        );
    }

    private List<Long> fallbackSelect(List<BrandOffering> candidates, String topic, String topicAsQuestion) {
        String haystack = (safe(topic) + " " + safe(topicAsQuestion)).toLowerCase();
        if (!StringUtils.hasText(haystack)) {
            return candidates.stream().limit(1).map(BrandOffering::getId).toList();
        }
        return candidates.stream()
                .filter(item -> matches(item, haystack))
                .limit(3)
                .map(BrandOffering::getId)
                .toList();
    }

    private boolean matches(BrandOffering item, String haystack) {
        List<String> terms = new ArrayList<>();
        terms.add(item.getOfferingName());
        terms.addAll(parseAliases(item.getOfferingAliasesJson()));
        terms.add(item.getTargetUsers());
        terms.add(item.getUseScenarios());
        return terms.stream()
                .filter(StringUtils::hasText)
                .map(value -> value.trim().toLowerCase())
                .anyMatch(haystack::contains);
    }

    private SelectedOffering toSelected(BrandOffering item) {
        return new SelectedOffering(
                item.getId(),
                item.getOfferingName(),
                parseAliases(item.getOfferingAliasesJson()),
                item.getTargetUsers(),
                item.getUseScenarios(),
                item.getOfferingIntro(),
                item.getQualificationDescription()
        );
    }

    private List<String> parseAliases(String aliasesJson) {
        if (!StringUtils.hasText(aliasesJson)) {
            return List.of();
        }
        try {
            List<String> values = new ArrayList<>();
            JSONUtil.parseArray(aliasesJson).forEach(item -> {
                if (item != null && StringUtils.hasText(String.valueOf(item))) {
                    values.add(String.valueOf(item).trim());
                }
            });
            return values;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String extractJson(String value) {
        if (!StringUtils.hasText(value)) {
            return "{}";
        }
        String trimmed = value.trim()
                .replaceFirst("^```(?:json)?\\s*", "")
                .replaceFirst("\\s*```$", "")
                .trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private String safe(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    public record SelectionResult(List<SelectedOffering> offerings) {
        static SelectionResult empty() {
            return new SelectionResult(List.of());
        }
    }

    public record SelectedOffering(Long id,
                                   String name,
                                   List<String> aliases,
                                   String targetUsers,
                                   String useScenarios,
                                   String intro,
                                   String qualificationDescription) {
    }
}
