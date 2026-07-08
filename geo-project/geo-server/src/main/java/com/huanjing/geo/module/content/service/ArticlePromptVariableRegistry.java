package com.huanjing.geo.module.content.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ArticlePromptVariableRegistry {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z][a-zA-Z0-9_]*)\\s*}}");
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    private static final Map<String, VariableDefinition> DEFINITIONS = definitions();

    public List<VariableDefinition> list() {
        return List.copyOf(DEFINITIONS.values());
    }

    public Map<String, VariableDefinition> definitionMap() {
        return DEFINITIONS;
    }

    public Set<String> extractVariables(String text) {
        Set<String> variables = new LinkedHashSet<>();
        if (!StringUtils.hasText(text)) {
            return variables;
        }
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        while (matcher.find()) {
            variables.add(matcher.group(1));
        }
        return variables;
    }

    public void validateTemplateVariables(String systemPrompt, String userPromptTemplate, String variablesJson) {
        Set<String> unknown = new LinkedHashSet<>();
        for (String variable : extractVariables(systemPrompt)) {
            if (!DEFINITIONS.containsKey(variable)) {
                unknown.add(variable);
            }
        }
        for (String variable : extractVariables(userPromptTemplate)) {
            if (!DEFINITIONS.containsKey(variable)) {
                unknown.add(variable);
            }
        }
        for (String variable : parseVariablesJson(variablesJson)) {
            if (!DEFINITIONS.containsKey(variable)) {
                unknown.add(variable);
            }
        }
        if (!unknown.isEmpty()) {
            throw new BizException(400, "Unsupported prompt template variables: " + String.join(", ", unknown));
        }
    }

    public String render(String raw, Map<String, String> values) {
        String rendered = StringUtils.hasText(raw) ? raw : "";
        for (VariableDefinition definition : DEFINITIONS.values()) {
            String value = values == null ? null : values.get(definition.code());
            rendered = rendered.replace("{{" + definition.code() + "}}", definition.resolve(value));
        }
        Set<String> remaining = extractVariables(rendered);
        if (!remaining.isEmpty()) {
            throw new BizException(400, "Unsupported prompt template variables during render: " + String.join(", ", remaining));
        }
        return rendered;
    }

    private List<String> parseVariablesJson(String variablesJson) {
        if (!StringUtils.hasText(variablesJson)) {
            return List.of();
        }
        try {
            List<String> variables = objectMapper.readValue(variablesJson, STRING_LIST_TYPE);
            List<String> normalized = new ArrayList<>();
            for (String variable : variables) {
                if (StringUtils.hasText(variable)) {
                    normalized.add(variable.trim());
                }
            }
            return normalized;
        } catch (JsonProcessingException ex) {
            throw new BizException(400, "variablesJson must be a JSON string array");
        }
    }

    private static Map<String, VariableDefinition> definitions() {
        Map<String, VariableDefinition> map = new LinkedHashMap<>();
        add(map, "topic", "主题", "当前生成主题", VariableSource.TOPIC, EmptyStrategy.SAFE_TEXT, "当前主题", "装修公司怎么选");
        add(map, "topicAsQuestion", "问题", "当前主题的问题化表达", VariableSource.TOPIC, EmptyStrategy.SAFE_TEXT, "当前问题", "装修公司怎么选?");
        add(map, "brandName", "品牌名称", "品牌名称", VariableSource.BRAND, EmptyStrategy.SAFE_TEXT, "该品牌", "示例品牌");
        add(map, "industry", "行业", "项目或品牌行业名称，优先使用字典展示值", VariableSource.PROJECT_BRAND, EmptyStrategy.DASH, null, "美容美业");
        add(map, "category", "品类", "与行业同源的品类展示名称", VariableSource.PROJECT_BRAND, EmptyStrategy.DASH, null, "美容美业");
        add(map, "projectName", "项目名称", "当前项目名称", VariableSource.PROJECT, EmptyStrategy.SAFE_TEXT, "当前项目", "示例项目");
        add(map, "channelName", "平台名称", "模板所属平台展示名称", VariableSource.TEMPLATE, EmptyStrategy.SAFE_TEXT, "当前平台", "百家号");
        add(map, "articleTypeName", "文章类型", "模板文章体裁展示名称", VariableSource.TEMPLATE, EmptyStrategy.SAFE_TEXT, "当前文章类型", "选择指南");
        add(map, "relatedKeywords", "相关关键词", "主题相关关键词列表", VariableSource.TOPIC, EmptyStrategy.KEEP_EMPTY, null, "口碑、价格、服务");
        add(map, "forbiddenPhrases", "禁用表达", "项目配置的禁用表达", VariableSource.PROJECT, EmptyStrategy.SAFE_TEXT, "未配置额外禁用表达", "最好、第一");
        add(map, "channelGuide", "平台写法说明", "平台内容风格说明", VariableSource.TEMPLATE, EmptyStrategy.SAFE_TEXT, "按当前平台常规内容风格撰写", "客观资料口吻");
        add(map, "region", "区域", "项目或品牌区域", VariableSource.PROJECT_BRAND, EmptyStrategy.DASH, null, "上海");
        add(map, "targetAudience", "目标受众", "项目目标受众", VariableSource.PROJECT, EmptyStrategy.SAFE_TEXT, "目标用户", "本地养生用户");
        add(map, "contentAngle", "内容角度", "系统为本篇生成的内容角度", VariableSource.RUNTIME, EmptyStrategy.SAFE_TEXT, "围绕当前主题展开", "选择标准");
        add(map, "audiencePerspective", "用户视角", "系统为本篇生成的用户视角", VariableSource.RUNTIME, EmptyStrategy.SAFE_TEXT, "目标读者视角", "初次了解服务的用户");
        add(map, "businessFocus", "业务重点", "根据主题和品牌资料推导的业务重点", VariableSource.RUNTIME, EmptyStrategy.SAFE_TEXT, "围绕主营业务展开", "服务流程");
        add(map, "recentTitles", "近期标题", "同批次近期标题，用于避免重复", VariableSource.RUNTIME, EmptyStrategy.KEEP_EMPTY, null, "标题A；标题B");
        add(map, "contactBlock", "联系方式块", "后端根据联系方式披露模式拼接的文章结尾联系方式", VariableSource.RUNTIME, EmptyStrategy.KEEP_EMPTY, null, "如需了解更多信息,可访问官网。");
        add(map, "titleGuide", "标题参考", "标题生成参考", VariableSource.RUNTIME, EmptyStrategy.KEEP_EMPTY, null, "标题尽量包含地域和服务");
        add(map, "titleElements", "标题要素", "与标题参考同源的标题要素", VariableSource.RUNTIME, EmptyStrategy.KEEP_EMPTY, null, "地域、服务、疑问词");
        add(map, "titleStrategy", "标题策略", "系统为自媒体文章生成的标题意图策略", VariableSource.RUNTIME, EmptyStrategy.KEEP_EMPTY, null, "条件判断型：突出适配边界");
        add(map, "structureStrategy", "结构策略", "系统为自媒体文章生成的结构方向", VariableSource.RUNTIME, EmptyStrategy.KEEP_EMPTY, null, "公开信息核验型：围绕主体信息和资质材料展开");
        add(map, "perspectivePolicy", "视角约束", "根据模板视角生成的写作边界说明", VariableSource.RUNTIME, EmptyStrategy.KEEP_EMPTY, null, "第三方中立视角：不以品牌方身份发声");
        add(map, "companyFullName", "公司全称", "项目公司名称或品牌名", VariableSource.PROJECT_BRAND, EmptyStrategy.SAFE_TEXT, "暂未提供公司全称", "示例科技有限公司");
        add(map, "brandShortName", "品牌简称", "品牌简称或品牌名", VariableSource.BRAND, EmptyStrategy.SAFE_TEXT, "该品牌", "示例品牌");
        add(map, "mainBusiness", "主营业务", "品牌主营业务", VariableSource.BRAND, EmptyStrategy.SAFE_TEXT, "暂未提供明确主营业务资料", "芳疗身体SPA、面部抗衰");
        add(map, "coreProducts", "核心产品", "品牌核心产品或服务", VariableSource.BRAND, EmptyStrategy.SAFE_TEXT, "暂未提供明确核心产品资料", "身体SPA、面部护理");
        add(map, "brandPositioning", "品牌定位", "品牌定位描述", VariableSource.BRAND, EmptyStrategy.SAFE_TEXT, "暂未提供明确品牌定位资料", "本地生活服务品牌");
        add(map, "serviceArea", "服务区域", "品牌服务区域", VariableSource.BRAND, EmptyStrategy.DASH, null, "阜阳");
        add(map, "brandIntro", "品牌介绍", "品牌业务介绍", VariableSource.BRAND, EmptyStrategy.SAFE_TEXT, "暂未提供品牌介绍", "专注本地生活服务");
        add(map, "brandQualificationDescription", "资质描述", "品牌资质说明", VariableSource.BRAND, EmptyStrategy.SAFE_TEXT, "暂未提供明确资质资料", "已提供相关经营资质");
        add(map, "brandCaseDescription", "案例描述", "品牌案例说明", VariableSource.BRAND, EmptyStrategy.SAFE_TEXT, "暂未提供明确案例资料", "已服务多类本地客户");
        return Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }

    private static void add(Map<String, VariableDefinition> map,
                            String code,
                            String name,
                            String description,
                            VariableSource source,
                            EmptyStrategy emptyStrategy,
                            String emptyText,
                            String sampleValue) {
        map.put(code, new VariableDefinition(code, name, description, source.name(), emptyStrategy.name(), emptyText, sampleValue));
    }

    public enum VariableSource {
        TOPIC,
        PROJECT,
        BRAND,
        PROJECT_BRAND,
        TEMPLATE,
        RUNTIME
    }

    public enum EmptyStrategy {
        KEEP_EMPTY,
        DASH,
        SAFE_TEXT
    }

    public record VariableDefinition(
            String code,
            String name,
            String description,
            String source,
            String emptyStrategy,
            String emptyText,
            String sampleValue
    ) {
        private String resolve(String value) {
            String normalized = normalizeEmpty(value);
            if (normalized != null) {
                return normalized;
            }
            EmptyStrategy strategy = EmptyStrategy.valueOf(emptyStrategy);
            return switch (strategy) {
                case KEEP_EMPTY -> "";
                case DASH -> "-";
                case SAFE_TEXT -> StringUtils.hasText(emptyText) ? emptyText : "-";
            };
        }

        private String normalizeEmpty(String value) {
            if (!StringUtils.hasText(value)) {
                return null;
            }
            String trimmed = value.trim();
            return "-".equals(trimmed) ? null : trimmed;
        }
    }
}
