package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.audit.ActorType;
import com.huanjing.geo.module.audit.AuditResult;
import com.huanjing.geo.module.audit.dto.AuditEvent;
import com.huanjing.geo.module.audit.service.AuditService;
import com.huanjing.geo.module.content.constant.ArticlePromptChannels;
import com.huanjing.geo.module.content.constant.TemplatePerspectiveCodes;
import com.huanjing.geo.module.content.dto.ArticlePromptTemplateDtos.TemplateDetailVO;
import com.huanjing.geo.module.content.dto.ArticlePromptTemplateDtos.TemplateSaveRequest;
import com.huanjing.geo.module.content.dto.ArticlePromptTemplateDtos.TemplateVO;
import com.huanjing.geo.module.content.dto.ArticlePromptTemplateDtos.VersionCreateRequest;
import com.huanjing.geo.module.content.dto.ArticlePromptTemplateDtos.VersionVO;
import com.huanjing.geo.module.content.dto.ArticlePromptTemplateDtos.WeightUpdateRequest;
import com.huanjing.geo.module.content.entity.ArticlePromptTemplate;
import com.huanjing.geo.module.content.entity.ArticlePromptTemplateVersion;
import com.huanjing.geo.module.content.mapper.ArticlePromptTemplateMapper;
import com.huanjing.geo.module.content.mapper.ArticlePromptTemplateVersionMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ArticlePromptTemplateService {

    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_DISABLED = "disabled";
    public static final String VERSION_DRAFT = "draft";
    public static final String VERSION_PUBLISHED = "published";
    public static final String VERSION_ARCHIVED = "archived";
    public static final String CONTACT_FULL = "full";
    public static final String CONTACT_SOFT_HINT = "soft_hint";
    public static final String CONTACT_BRAND_ONLY = "brand_only";
    public static final String CONTACT_NONE = "none";
    public static final Map<String, String> QUESTION_SCENE_LABELS = Map.of(
            "brand", "品牌",
            "decision", "决策",
            "deal", "成交",
            "compare", "对比",
            "qa", "问答",
            "function", "功能"
    );
    public static final int MIN_TEMPLATE_WEIGHT = 0;
    public static final int MAX_TEMPLATE_WEIGHT = 100;

    private final ArticlePromptTemplateMapper templateMapper;
    private final ArticlePromptTemplateVersionMapper versionMapper;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;
    private final ArticlePromptVariableRegistry variableRegistry;
    private final TemplatePerspectiveService perspectiveService;

    public List<ArticlePromptVariableRegistry.VariableDefinition> variables() {
        currentUserService.ensurePermission("project.read");
        return variableRegistry.list();
    }

    public Page<TemplateVO> page(String channelGroupCode,
                                 String channelSubCode,
                                 String agentSiteModule,
                                 String questionSceneCode,
                                 String perspectiveCode,
                                 String status,
                                 String keyword,
                                 long current,
                                 long size) {
        currentUserService.ensurePermission("project.read");
        LambdaQueryWrapper<ArticlePromptTemplate> wrapper = new LambdaQueryWrapper<ArticlePromptTemplate>()
                .eq(StringUtils.hasText(channelGroupCode), ArticlePromptTemplate::getChannelGroupCode, trim(channelGroupCode))
                .eq(StringUtils.hasText(channelSubCode), ArticlePromptTemplate::getChannelSubCode, trim(channelSubCode))
                .eq(StringUtils.hasText(agentSiteModule), ArticlePromptTemplate::getAgentSiteModule, trim(agentSiteModule))
                .eq(StringUtils.hasText(questionSceneCode), ArticlePromptTemplate::getQuestionSceneCode, trim(questionSceneCode))
                .eq(StringUtils.hasText(perspectiveCode), ArticlePromptTemplate::getPerspectiveCode,
                        TemplatePerspectiveCodes.normalize(perspectiveCode))
                .eq(StringUtils.hasText(status), ArticlePromptTemplate::getStatus, trim(status))
                .and(StringUtils.hasText(keyword), q -> q
                        .like(ArticlePromptTemplate::getName, trim(keyword))
                        .or()
                        .like(ArticlePromptTemplate::getDescription, trim(keyword)))
                .orderByDesc(ArticlePromptTemplate::getUpdatedAt, ArticlePromptTemplate::getId);
        Page<ArticlePromptTemplate> page = templateMapper.selectPage(new Page<>(current, size), wrapper);
        Page<TemplateVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toVO).toList());
        return result;
    }

    public TemplateDetailVO detail(Long id) {
        currentUserService.ensurePermission("project.read");
        ArticlePromptTemplate template = requireTemplate(id);
        List<ArticlePromptTemplateVersion> versions = versionMapper.selectList(
                new LambdaQueryWrapper<ArticlePromptTemplateVersion>()
                        .eq(ArticlePromptTemplateVersion::getTemplateId, id)
                        .orderByDesc(ArticlePromptTemplateVersion::getVersionNo)
        );
        ArticlePromptTemplateVersion current = template.getCurrentVersionId() == null
                ? null
                : versionMapper.selectById(template.getCurrentVersionId());
        return new TemplateDetailVO(
                toVO(template),
                current == null ? null : toVersionVO(current),
                versions.stream().map(this::toVersionVO).toList()
        );
    }

    @Transactional
    public TemplateDetailVO create(TemplateSaveRequest req) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("content.prompt_template.manage");
        validateTemplate(req.channelGroupCode(), req.channelSubCode(), req.agentSiteModule(), req.status(), req.weight(),
                req.contactDisclosureMode(), req.questionSceneCode(), req.perspectiveCode());
        variableRegistry.validateTemplateVariables(req.systemPrompt(), req.userPromptTemplate(), req.variablesJson());

        ArticlePromptTemplate template = new ArticlePromptTemplate();
        fillTemplate(template, req);
        template.setCurrentVersionId(null);
        template.setCreatedBy(operator.getId());
        templateMapper.insert(template);

        ArticlePromptTemplateVersion version = createVersionRow(template.getId(), 1, req.systemPrompt(),
                req.userPromptTemplate(), req.variablesJson(), req.qualityRulesJson(), true, operator.getId());
        template.setCurrentVersionId(version.getId());
        if (STATUS_DRAFT.equals(template.getStatus())) {
            template.setStatus(STATUS_ACTIVE);
        }
        templateMapper.updateById(template);
        audit(operator, "article_prompt_template.create", template.getId(), Map.of("name", template.getName()));
        return detail(template.getId());
    }

    @Transactional
    public TemplateDetailVO update(Long id, TemplateSaveRequest req) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("content.prompt_template.manage");
        ArticlePromptTemplate template = requireTemplate(id);
        validateTemplate(req.channelGroupCode(), req.channelSubCode(), req.agentSiteModule(), req.status(), req.weight(),
                req.contactDisclosureMode(), req.questionSceneCode(), req.perspectiveCode());
        boolean hasVersionPayload = StringUtils.hasText(req.systemPrompt()) && StringUtils.hasText(req.userPromptTemplate());
        if (hasVersionPayload) {
            variableRegistry.validateTemplateVariables(req.systemPrompt(), req.userPromptTemplate(), req.variablesJson());
        }
        Map<String, Object> before = snapshot(template);
        fillTemplate(template, req);
        templateMapper.updateById(template);

        if (hasVersionPayload) {
            upsertCurrentVersion(template, req.systemPrompt(), req.userPromptTemplate(),
                    req.variablesJson(), req.qualityRulesJson(), operator.getId());
        }
        audit(operator, "article_prompt_template.update", id, Map.of("before", before, "after", snapshot(template)));
        return detail(id);
    }

    @Transactional
    public TemplateDetailVO createVersion(Long templateId, VersionCreateRequest req) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("content.prompt_template.manage");
        ArticlePromptTemplate template = requireTemplate(templateId);
        variableRegistry.validateTemplateVariables(req.systemPrompt(), req.userPromptTemplate(), req.variablesJson());
        ArticlePromptTemplateVersion version = upsertCurrentVersion(template, req.systemPrompt(),
                req.userPromptTemplate(), req.variablesJson(), req.qualityRulesJson(), operator.getId());
        audit(operator, "article_prompt_template.version.create", templateId,
                Map.of("versionNo", version.getVersionNo(), "published", true));
        return detail(templateId);
    }

    @Transactional
    public TemplateDetailVO publishVersion(Long templateId, Long versionId) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("content.prompt_template.manage");
        ArticlePromptTemplate template = requireTemplate(templateId);
        ArticlePromptTemplateVersion version = requireVersion(templateId, versionId);
        if (!version.getId().equals(template.getCurrentVersionId())) {
            throw new BizException(400, "Template version publishing is disabled; edit and save the template to take effect");
        }
        version.setStatus(VERSION_PUBLISHED);
        version.setPublishedAt(version.getPublishedAt() == null ? LocalDateTime.now() : version.getPublishedAt());
        versionMapper.updateById(version);
        audit(operator, "article_prompt_template.version.publish", templateId,
                Map.of("versionId", versionId, "directSaveMode", true));
        return detail(templateId);
    }

    @Transactional
    public TemplateVO updateWeight(Long id, WeightUpdateRequest req) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("content.prompt_template.manage");
        validateWeight(req.weight());
        ArticlePromptTemplate template = requireTemplate(id);
        Integer before = template.getWeight();
        template.setWeight(req.weight());
        templateMapper.updateById(template);
        audit(operator, "article_prompt_template.weight.update", id,
                Map.of("before", Map.of("weight", before), "after", Map.of("weight", req.weight())));
        return toVO(template);
    }

    public ArticlePromptTemplate requireTemplate(Long id) {
        ArticlePromptTemplate template = templateMapper.selectById(id);
        if (template == null) {
            throw new BizException(404, "Prompt template not found");
        }
        return template;
    }

    private ArticlePromptTemplateVersion requireVersion(Long templateId, Long versionId) {
        ArticlePromptTemplateVersion version = versionMapper.selectById(versionId);
        if (version == null || !templateId.equals(version.getTemplateId())) {
            throw new BizException(404, "Prompt template version not found");
        }
        return version;
    }

    private void fillTemplate(ArticlePromptTemplate template, TemplateSaveRequest req) {
        template.setName(trim(req.name()));
        template.setDescription(trimToNull(req.description()));
        template.setChannelGroupCode(trim(req.channelGroupCode()));
        template.setChannelSubCode(trimToNull(req.channelSubCode()));
        template.setAgentSiteModule(trimToNull(req.agentSiteModule()));
        template.setArticleTypeCode(trim(req.articleTypeCode()));
        template.setQuestionSceneCode(normalizeQuestionScene(req.questionSceneCode()));
        template.setPerspectiveCode(TemplatePerspectiveCodes.normalize(req.perspectiveCode()));
        template.setWeight(req.weight());
        template.setSortOrder(req.sortOrder() == null ? 0 : req.sortOrder());
        template.setStatus(trim(req.status()));
        template.setSampleOutputUrl(trimToNull(req.sampleOutputUrl()));
        template.setContactDisclosureMode(normalizeContactMode(req.contactDisclosureMode()));
    }

    private void validateTemplate(String groupCode, String subCode, String module, String status, Integer weight,
                                  String contactDisclosureMode, String questionSceneCode, String perspectiveCode) {
        String group = trim(groupCode);
        String sub = trimToNull(subCode);
        if (!ArticlePromptChannels.isValidCode(group)) {
            throw new BizException(400, "Invalid channel group");
        }
        if (sub != null && !ArticlePromptChannels.isValidCode(sub)) {
            throw new BizException(400, "Invalid channel sub code");
        }
        if (!List.of(STATUS_DRAFT, STATUS_ACTIVE, STATUS_DISABLED).contains(trim(status))) {
            throw new BizException(400, "Invalid template status");
        }
        validateWeight(weight);
        if (!List.of(CONTACT_FULL, CONTACT_SOFT_HINT, CONTACT_BRAND_ONLY, CONTACT_NONE)
                .contains(normalizeContactMode(contactDisclosureMode))) {
            throw new BizException(400, "Invalid contact disclosure mode");
        }
        String scene = normalizeQuestionScene(questionSceneCode);
        if (scene != null && !QUESTION_SCENE_LABELS.containsKey(scene)) {
            throw new BizException(400, "Invalid question scene");
        }
        perspectiveService.assertPerspectiveSelectable(TemplatePerspectiveCodes.normalize(perspectiveCode));
        if (ArticlePromptChannels.AGENT_SITE.equals(group)) {
            if (!ArticlePromptChannels.AGENT_SITE_MODULES.contains(trim(module))) {
                throw new BizException(400, "Agent site module is required");
            }
            if (StringUtils.hasText(subCode)) {
                throw new BizException(400, "Agent site template cannot have channel sub code");
            }
            return;
        }
        if (StringUtils.hasText(module)) {
            throw new BizException(400, "Only Agent site template can have module");
        }
        if (ArticlePromptChannels.SELF_MEDIA.equals(group)
                && !StringUtils.hasText(sub)) {
            throw new BizException(400, "Self media sub channel is required");
        }
        if (ArticlePromptChannels.AUTHORITY_MEDIA.equals(group)
                && !StringUtils.hasText(sub)) {
            throw new BizException(400, "Authority media sub channel is required");
        }
        if ((ArticlePromptChannels.INDUSTRY_SITE.equals(group) || ArticlePromptChannels.FORUM.equals(group))
                && StringUtils.hasText(subCode)) {
            throw new BizException(400, "This channel group cannot have channel sub code");
        }
    }

    private void validateWeight(Integer weight) {
        if (weight == null || weight < MIN_TEMPLATE_WEIGHT || weight > MAX_TEMPLATE_WEIGHT) {
            throw new BizException(400, "Template weight must be between 0 and 100");
        }
    }

    private ArticlePromptTemplateVersion createVersionRow(Long templateId,
                                                          int versionNo,
                                                          String systemPrompt,
                                                          String userPromptTemplate,
                                                          String variablesJson,
                                                          String qualityRulesJson,
                                                          boolean publish,
                                                          Long operatorId) {
        ArticlePromptTemplateVersion version = new ArticlePromptTemplateVersion();
        version.setTemplateId(templateId);
        version.setVersionNo(versionNo);
        version.setSystemPrompt(systemPrompt);
        version.setUserPromptTemplate(userPromptTemplate);
        version.setVariablesJson(trimToNull(variablesJson));
        version.setQualityRulesJson(trimToNull(qualityRulesJson));
        version.setStatus(publish ? VERSION_PUBLISHED : VERSION_DRAFT);
        version.setCreatedBy(operatorId);
        version.setPublishedAt(publish ? LocalDateTime.now() : null);
        versionMapper.insert(version);
        return version;
    }

    private ArticlePromptTemplateVersion upsertCurrentVersion(ArticlePromptTemplate template,
                                                              String systemPrompt,
                                                              String userPromptTemplate,
                                                              String variablesJson,
                                                              String qualityRulesJson,
                                                              Long operatorId) {
        ArticlePromptTemplateVersion current = template.getCurrentVersionId() == null
                ? null
                : versionMapper.selectById(template.getCurrentVersionId());
        if (current != null) {
            current.setSystemPrompt(systemPrompt);
            current.setUserPromptTemplate(userPromptTemplate);
            current.setVariablesJson(trimToNull(variablesJson));
            current.setQualityRulesJson(trimToNull(qualityRulesJson));
            current.setStatus(VERSION_PUBLISHED);
            current.setPublishedAt(LocalDateTime.now());
            versionMapper.updateById(current);
            return current;
        }

        int nextVersion = versionMapper.maxVersionNo(template.getId()) + 1;
        ArticlePromptTemplateVersion version = createVersionRow(template.getId(), nextVersion, systemPrompt,
                userPromptTemplate, variablesJson, qualityRulesJson, true, operatorId);
        template.setCurrentVersionId(version.getId());
        if (STATUS_DRAFT.equals(template.getStatus())) {
            template.setStatus(STATUS_ACTIVE);
        }
        templateMapper.updateById(template);
        return version;
    }

    private TemplateVO toVO(ArticlePromptTemplate template) {
        ArticlePromptTemplateVersion version = template.getCurrentVersionId() == null
                ? null
                : versionMapper.selectById(template.getCurrentVersionId());
        return new TemplateVO(
                template.getId(),
                template.getName(),
                template.getDescription(),
                template.getChannelGroupCode(),
                ArticlePromptChannels.GROUP_LABELS.getOrDefault(template.getChannelGroupCode(), template.getChannelGroupCode()),
                template.getChannelSubCode(),
                template.getChannelSubCode() == null ? null : ArticlePromptChannels.SUB_LABELS.getOrDefault(template.getChannelSubCode(), template.getChannelSubCode()),
                template.getAgentSiteModule(),
                template.getArticleTypeCode(),
                ArticlePromptChannels.ARTICLE_TYPE_LABELS.getOrDefault(template.getArticleTypeCode(), template.getArticleTypeCode()),
                template.getQuestionSceneCode(),
                questionSceneLabel(template.getQuestionSceneCode()),
                TemplatePerspectiveCodes.normalize(template.getPerspectiveCode()),
                template.getWeight(),
                template.getSortOrder(),
                template.getStatus(),
                template.getSampleOutputUrl(),
                normalizeContactMode(template.getContactDisclosureMode()),
                template.getCurrentVersionId(),
                version == null ? null : version.getVersionNo(),
                template.getCreatedAt(),
                template.getUpdatedAt()
        );
    }

    private VersionVO toVersionVO(ArticlePromptTemplateVersion version) {
        return new VersionVO(
                version.getId(),
                version.getTemplateId(),
                version.getVersionNo(),
                version.getSystemPrompt(),
                version.getUserPromptTemplate(),
                version.getVariablesJson(),
                version.getQualityRulesJson(),
                version.getStatus(),
                version.getCreatedAt(),
                version.getPublishedAt()
        );
    }

    private Map<String, Object> snapshot(ArticlePromptTemplate template) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", template.getName());
        map.put("channelGroupCode", template.getChannelGroupCode());
        map.put("channelSubCode", template.getChannelSubCode());
        map.put("agentSiteModule", template.getAgentSiteModule());
        map.put("articleTypeCode", template.getArticleTypeCode());
        map.put("questionSceneCode", template.getQuestionSceneCode());
        map.put("perspectiveCode", TemplatePerspectiveCodes.normalize(template.getPerspectiveCode()));
        map.put("weight", template.getWeight());
        map.put("status", template.getStatus());
        map.put("sampleOutputUrl", template.getSampleOutputUrl());
        map.put("contactDisclosureMode", normalizeContactMode(template.getContactDisclosureMode()));
        return map;
    }

    private void audit(SysUser operator, String eventType, Long templateId, Map<String, Object> detail) {
        AuditEvent event = new AuditEvent();
        event.setEventType(eventType);
        event.setActorType(ActorType.OPERATOR);
        event.setActorId(operator == null ? null : operator.getId());
        event.setTargetType("article_prompt_template");
        event.setTargetId(String.valueOf(templateId));
        event.setResult(AuditResult.SUCCESS);
        event.setDetail(detail);
        auditService.record(event);
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeContactMode(String value) {
        String mode = trimToNull(value);
        return mode == null ? CONTACT_NONE : mode;
    }

    private String normalizeQuestionScene(String value) {
        return trimToNull(value);
    }

    private String questionSceneLabel(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return QUESTION_SCENE_LABELS.getOrDefault(value, value);
    }
}
