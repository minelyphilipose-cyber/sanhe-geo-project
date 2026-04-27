package com.huanjing.geo.module.presale.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.presale.dto.request.PromptTemplateDraftRequest;
import com.huanjing.geo.module.presale.persist.entity.PresalePromptTemplate;
import com.huanjing.geo.module.presale.persist.entity.PresaleReportVersionPromptTemplate;
import com.huanjing.geo.module.presale.persist.mapper.PresalePromptTemplateMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class PromptTemplateDraftValidator {
    public static final String ERROR_CODE_TEMPLATE_VERSION_CHANGED = "template_version_changed";
    private static final int MAX_ACTIVE_TEMPLATES = 60;
    private static final int MAX_PROMPT_CONTENT_LENGTH = 1000;
    private static final String COMPETITOR_VAR = "{competitor}";
    private static final Set<String> ALLOWED_VARIABLES = Set.of(
            "{brand}",
            "{product}",
            "{industry}",
            "{industry_role}",
            "{region}",
            "{user_type}",
            "{competitor}"
    );
    private static final Pattern BRACED_TOKEN_PATTERN = Pattern.compile("\\{[^{}]*\\}");
    private static final Pattern VALID_TOKEN_FORMAT = Pattern.compile("\\{[a-z_]+\\}");

    private final PresalePromptTemplateMapper promptTemplateMapper;

    public PromptTemplateDraftValidator(PresalePromptTemplateMapper promptTemplateMapper) {
        this.promptTemplateMapper = promptTemplateMapper;
    }

    public List<PresalePromptTemplate> listActiveTemplates(String activeVersion) {
        return promptTemplateMapper.selectList(
                new LambdaQueryWrapper<PresalePromptTemplate>()
                        .eq(PresalePromptTemplate::getEnabled, 1)
                        .eq(PresalePromptTemplate::getTemplateVersion, activeVersion)
                        .orderByAsc(PresalePromptTemplate::getSortOrder)
                        .orderByAsc(PresalePromptTemplate::getId)
        );
    }

    public List<PresaleReportVersionPromptTemplate> validateAndBuildSnapshots(
            String requestTemplateVersion,
            List<PromptTemplateDraftRequest> drafts,
            String activeVersion,
            Long reportId,
            Long reportVersionId,
            LocalDateTime createdAt) {
        if (requestTemplateVersion == null || !requestTemplateVersion.equals(activeVersion)) {
            throw new BizException(
                    409,
                    "Prompt 模板版本已更新",
                    409,
                    Map.of("errorCode", ERROR_CODE_TEMPLATE_VERSION_CHANGED)
            );
        }

        List<PresalePromptTemplate> activeTemplates = listActiveTemplates(activeVersion);
        if (activeTemplates.size() > MAX_ACTIVE_TEMPLATES) {
            throw new BizException(
                    500,
                    "当前启用 Prompt 模板数量超过 60 条，请联系管理员",
                    500,
                    null
            );
        }
        List<ValidationError> errors = validate(drafts, activeTemplates);
        if (!errors.isEmpty()) {
            throw new BizException(
                    400,
                    "Prompt template validation failed",
                    200,
                    Map.of("errors", errors)
            );
        }

        Map<Long, PromptTemplateDraftRequest> draftBySourceId = drafts.stream()
                .collect(Collectors.toMap(PromptTemplateDraftRequest::getSourceTemplateId, d -> d));
        List<PresaleReportVersionPromptTemplate> snapshots = new ArrayList<>();
        int order = 1;
        for (PresalePromptTemplate source : activeTemplates) {
            PromptTemplateDraftRequest draft = draftBySourceId.get(source.getId());
            snapshots.add(toSnapshot(source, draft.getPromptContent(), reportId, reportVersionId, order++, createdAt));
        }
        return snapshots;
    }

    public List<ValidationError> validate(List<PromptTemplateDraftRequest> drafts,
                                          List<PresalePromptTemplate> activeTemplates) {
        List<ValidationError> errors = new ArrayList<>();
        if (activeTemplates == null || activeTemplates.isEmpty()) {
            errors.add(new ValidationError(null, "promptTemplates", "当前启用 Prompt 模板为空"));
            return errors;
        }
        if (drafts == null || drafts.isEmpty()) {
            errors.add(new ValidationError(null, "promptTemplates", "Prompt 清单不能为空"));
            return errors;
        }

        Map<Long, PresalePromptTemplate> globalTemplateMap = activeTemplates.stream()
                .collect(Collectors.toMap(PresalePromptTemplate::getId, t -> t, (a, b) -> a, LinkedHashMap::new));
        if (drafts.size() != activeTemplates.size()) {
            errors.add(new ValidationError(null, "promptTemplates",
                    "Prompt 数量必须为 " + activeTemplates.size() + " 条，当前提交了 " + drafts.size() + " 条"));
        }

        Set<Long> seen = new HashSet<>();
        Map<String, Integer> draftCategoryCounts = new HashMap<>();
        for (int i = 0; i < drafts.size(); i++) {
            PromptTemplateDraftRequest draft = drafts.get(i);
            if (draft == null || draft.getSourceTemplateId() == null) {
                errors.add(new ValidationError(i, "sourceTemplateId", "源模板 ID 不能为空"));
                continue;
            }
            if (!seen.add(draft.getSourceTemplateId())) {
                errors.add(new ValidationError(i, "sourceTemplateId", "源模板 ID 重复"));
                continue;
            }

            PresalePromptTemplate source = globalTemplateMap.get(draft.getSourceTemplateId());
            if (source == null) {
                errors.add(new ValidationError(i, "sourceTemplateId", "源模板不存在或已禁用"));
                continue;
            }
            draftCategoryCounts.merge(source.getCategory(), 1, Integer::sum);
            validatePromptContent(i, draft.getPromptContent(), source, errors);
        }

        Map<String, Integer> activeCategoryCounts = activeTemplates.stream()
                .collect(Collectors.groupingBy(PresalePromptTemplate::getCategory, Collectors.summingInt(t -> 1)));
        for (Map.Entry<String, Integer> entry : activeCategoryCounts.entrySet()) {
            int actual = draftCategoryCounts.getOrDefault(entry.getKey(), 0);
            if (actual != entry.getValue()) {
                errors.add(new ValidationError(null, "promptTemplates",
                        entry.getKey() + " Prompt 数量必须为 " + entry.getValue()
                                + " 条，当前提交了 " + actual + " 条"));
            }
        }
        return errors.stream()
                .sorted(Comparator.comparing((ValidationError e) -> e.index == null ? Integer.MAX_VALUE : e.index)
                        .thenComparing(e -> e.field == null ? "" : e.field))
                .toList();
    }

    private void validatePromptContent(int index,
                                       String promptContent,
                                       PresalePromptTemplate source,
                                       List<ValidationError> errors) {
        if (promptContent == null || promptContent.isBlank()) {
            errors.add(new ValidationError(index, "promptContent", "Prompt 内容不能为空"));
            return;
        }
        if (promptContent.length() > MAX_PROMPT_CONTENT_LENGTH) {
            errors.add(new ValidationError(index, "promptContent", "Prompt 内容最多 1000 字"));
        }
        validateVariables(index, promptContent, errors);

        boolean shouldHaveCompetitor = Integer.valueOf(1).equals(source.getHasCompetitorVar());
        boolean hasCompetitor = promptContent.contains(COMPETITOR_VAR);
        if (shouldHaveCompetitor && !hasCompetitor) {
            errors.add(new ValidationError(index, "promptContent", "对比型 Prompt 必须包含 {competitor}"));
        }
        if (!shouldHaveCompetitor && hasCompetitor) {
            errors.add(new ValidationError(index, "promptContent", "通用 Prompt 不能包含 {competitor}"));
        }
    }

    private void validateVariables(int index, String promptContent, List<ValidationError> errors) {
        Matcher matcher = BRACED_TOKEN_PATTERN.matcher(promptContent);
        Set<String> reported = new HashSet<>();
        int tokenCount = 0;
        while (matcher.find()) {
            tokenCount++;
            String token = matcher.group();
            if (!VALID_TOKEN_FORMAT.matcher(token).matches()) {
                if (reported.add(token)) {
                    errors.add(new ValidationError(index, "promptContent", "变量格式不合法: " + token));
                }
                continue;
            }
            if (!ALLOWED_VARIABLES.contains(token) && reported.add(token)) {
                errors.add(new ValidationError(index, "promptContent", "未知变量: " + token));
            }
        }
        if (tokenCount == 0 && (promptContent.contains("{") || promptContent.contains("}"))) {
            errors.add(new ValidationError(index, "promptContent", "Prompt 中出现 '{' 或 '}' 字符但不是合法变量格式"));
        }
    }

    private PresaleReportVersionPromptTemplate toSnapshot(PresalePromptTemplate source,
                                                          String promptContent,
                                                          Long reportId,
                                                          Long reportVersionId,
                                                          int sortOrderInVersion,
                                                          LocalDateTime createdAt) {
        PresaleReportVersionPromptTemplate row = new PresaleReportVersionPromptTemplate();
        row.setReportId(reportId);
        row.setReportVersionId(reportVersionId);
        row.setSourceTemplateId(source.getId());
        row.setSourcePromptCode(source.getPromptCode());
        row.setSourceTemplateVersion(source.getTemplateVersion());
        row.setCategory(source.getCategory());
        row.setBusinessValue(source.getBusinessValue());
        row.setPromptContent(promptContent);
        row.setHasCompetitorVar(source.getHasCompetitorVar());
        row.setSortOrderInVersion(sortOrderInVersion);
        row.setRemark(source.getRemark());
        row.setIsUserAdded(0);
        row.setCreatedAt(createdAt);
        return row;
    }

    public record ValidationError(Integer index, String field, String message) {
    }
}
