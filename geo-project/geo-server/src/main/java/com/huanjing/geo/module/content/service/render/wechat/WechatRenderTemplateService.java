package com.huanjing.geo.module.content.service.render.wechat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.dto.render.WechatRenderDtos.BodyStyle;
import com.huanjing.geo.module.content.dto.render.WechatRenderDtos.RoleSchema;
import com.huanjing.geo.module.content.dto.render.WechatRenderDtos.TemplateSaveRequest;
import com.huanjing.geo.module.content.dto.render.WechatRenderDtos.TemplateUpdateRequest;
import com.huanjing.geo.module.content.dto.render.WechatRenderDtos.TemplateVersionSaveRequest;
import com.huanjing.geo.module.content.entity.ArticlePlatformRender;
import com.huanjing.geo.module.content.entity.PlatformRenderTemplate;
import com.huanjing.geo.module.content.entity.PlatformRenderTemplateVersion;
import com.huanjing.geo.module.content.mapper.ArticlePlatformRenderMapper;
import com.huanjing.geo.module.content.mapper.PlatformRenderTemplateMapper;
import com.huanjing.geo.module.content.mapper.PlatformRenderTemplateVersionMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.SysUserMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WechatRenderTemplateService {
    public static final String PLATFORM = "wechat_mp";

    private final PlatformRenderTemplateMapper templateMapper;
    private final PlatformRenderTemplateVersionMapper versionMapper;
    private final ArticlePlatformRenderMapper articleRenderMapper;
    private final SysUserMapper sysUserMapper;
    private final WechatTemplateImportService importService;
    private final WechatHtmlSanitizer htmlSanitizer;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;

    public Page<PlatformRenderTemplate> page(long current, long size) {
        currentUserService.ensurePermission("project.read");
        Page<PlatformRenderTemplate> page = templateMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<PlatformRenderTemplate>()
                        .eq(PlatformRenderTemplate::getPlatformCode, PLATFORM)
                        .orderByDesc(PlatformRenderTemplate::getUpdatedAt));
        fillCreatorNames(page.getRecords());
        return page;
    }

    public PlatformRenderTemplate get(Long templateId) {
        currentUserService.ensurePermission("project.read");
        PlatformRenderTemplate template = requireTemplate(templateId);
        fillCreatorNames(List.of(template));
        return template;
    }

    public PlatformRenderTemplateVersion currentVersion(Long templateId) {
        return versionMapper.selectOne(new LambdaQueryWrapper<PlatformRenderTemplateVersion>()
                .eq(PlatformRenderTemplateVersion::getTemplateId, templateId)
                .orderByDesc(PlatformRenderTemplateVersion::getVersionNo)
                .last("LIMIT 1"));
    }

    @Transactional
    public PlatformRenderTemplate create(TemplateSaveRequest request) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("project.update");
        PlatformRenderTemplate template = new PlatformRenderTemplate();
        template.setPlatformCode(PLATFORM);
        template.setName(request.getName().trim());
        template.setDescription(trimToNull(request.getDescription()));
        template.setStatus("enabled");
        template.setCreatedBy(operator.getId());
        templateMapper.insert(template);
        createVersion(template.getId(), request.getSourceType(), request.getSourceHtml(), request.getRoles(), request.getBodyStyle(), operator.getId());
        return template;
    }

    @Transactional
    public PlatformRenderTemplate update(Long templateId, TemplateUpdateRequest request) {
        currentUserService.ensurePermission("project.update");
        PlatformRenderTemplate template = requireTemplate(templateId);
        template.setName(request.getName().trim());
        template.setDescription(trimToNull(request.getDescription()));
        if (StringUtils.hasText(request.getStatus())) {
            template.setStatus(normalizeStatus(request.getStatus()));
        }
        templateMapper.updateById(template);
        fillCreatorNames(List.of(template));
        return template;
    }

    @Transactional
    public PlatformRenderTemplateVersion createVersion(Long templateId, TemplateVersionSaveRequest request) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("project.update");
        requireTemplate(templateId);
        return createVersion(templateId, request.getSourceType(), request.getSourceHtml(), request.getRoles(), request.getBodyStyle(), operator.getId());
    }

    @Transactional
    public void updateStatus(Long templateId, String status) {
        currentUserService.ensurePermission("project.update");
        PlatformRenderTemplate template = requireTemplate(templateId);
        template.setStatus(normalizeStatus(status));
        templateMapper.updateById(template);
    }

    @Transactional
    public void delete(Long templateId) {
        currentUserService.ensurePermission("project.update");
        requireTemplate(templateId);
        if (isTemplateReferenced(templateId)) {
            throw new BizException(400, "该模板已被文章使用，不能删除；如不再使用，请先停用模板");
        }
        versionMapper.delete(new LambdaQueryWrapper<PlatformRenderTemplateVersion>()
                .eq(PlatformRenderTemplateVersion::getTemplateId, templateId));
        templateMapper.deleteById(templateId);
    }

    public PlatformRenderTemplate requireTemplate(Long templateId) {
        PlatformRenderTemplate template = templateMapper.selectById(templateId);
        if (template == null || !PLATFORM.equals(template.getPlatformCode())) {
            throw new BizException(404, "公众号模板不存在");
        }
        return template;
    }

    public PlatformRenderTemplateVersion requireVersion(Long versionId) {
        PlatformRenderTemplateVersion version = versionMapper.selectById(versionId);
        if (version == null) {
            throw new BizException(404, "公众号模板版本不存在");
        }
        PlatformRenderTemplate template = requireTemplate(version.getTemplateId());
        if (!"enabled".equalsIgnoreCase(template.getStatus())) {
            throw new BizException(400, "公众号模板已停用");
        }
        return version;
    }

    public Map<String, RoleSchema> readRoles(PlatformRenderTemplateVersion version) {
        TemplateSchema schema = readSchema(version);
        return schema.roles == null ? Map.of() : schema.roles;
    }

    public TemplateSchema readSchema(PlatformRenderTemplateVersion version) {
        try {
            TemplateSchema schema = objectMapper.readValue(version.getTemplateSchemaJson(), TemplateSchema.class);
            if (schema.roles == null) {
                schema.roles = Map.of();
            }
            return schema;
        } catch (Exception ex) {
            throw new BizException(500, "公众号模板结构解析失败");
        }
    }

    public BodyStyle resolveBodyStyle(PlatformRenderTemplateVersion version, TemplateSchema schema) {
        BodyStyle bodyStyle = schema == null ? null : schema.bodyStyle;
        if (bodyStyle != null) {
            return importService.normalizeBodyStyle(bodyStyle, version.getSourceType());
        }
        return importService.defaultBodyStyle(version.getSourceType());
    }

    public boolean isVersionReferenced(Long versionId) {
        Long count = articleRenderMapper.selectCount(new LambdaQueryWrapper<ArticlePlatformRender>()
                .eq(ArticlePlatformRender::getTemplateVersionId, versionId));
        return count != null && count > 0;
    }

    private boolean isTemplateReferenced(Long templateId) {
        Long count = articleRenderMapper.selectCount(new LambdaQueryWrapper<ArticlePlatformRender>()
                .eq(ArticlePlatformRender::getTemplateId, templateId));
        return count != null && count > 0;
    }

    private PlatformRenderTemplateVersion createVersion(Long templateId,
                                                        String sourceType,
                                                        String sourceHtml,
                                                        Map<String, RoleSchema> roles,
                                                        BodyStyle bodyStyle,
                                                        Long operatorId) {
        String normalizedSourceType = StringUtils.hasText(sourceType) ? sourceType.trim() : "generic";
        Map<String, RoleSchema> normalizedRoles = importService.normalizeRoles(roles);
        BodyStyle normalizedBodyStyle = importService.normalizeBodyStyle(bodyStyle, normalizedSourceType);
        if (normalizedRoles.isEmpty()) {
            throw new BizException(400, "请至少绑定一个模板角色");
        }
        int nextVersion = nextVersionNo(templateId);
        PlatformRenderTemplateVersion version = new PlatformRenderTemplateVersion();
        version.setTemplateId(templateId);
        version.setVersionNo(nextVersion);
        version.setSourceType(normalizedSourceType);
        version.setSourceHtml(sourceHtml);
        version.setTemplateSchemaJson(toSchemaJson(normalizedRoles, normalizedBodyStyle));
        version.setSanitizedPreviewHtml(buildPreviewHtml(sourceHtml, normalizedRoles));
        version.setCreatedBy(operatorId);
        versionMapper.insert(version);
        return version;
    }

    private int nextVersionNo(Long templateId) {
        PlatformRenderTemplateVersion latest = versionMapper.selectOne(new LambdaQueryWrapper<PlatformRenderTemplateVersion>()
                .eq(PlatformRenderTemplateVersion::getTemplateId, templateId)
                .orderByDesc(PlatformRenderTemplateVersion::getVersionNo)
                .last("LIMIT 1"));
        return latest == null ? 1 : latest.getVersionNo() + 1;
    }

    private String toSchemaJson(Map<String, RoleSchema> roles, BodyStyle bodyStyle) {
        try {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("platformCode", PLATFORM);
            root.put("roles", roles);
            if (bodyStyle != null) {
                root.put("bodyStyle", bodyStyle);
            }
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException ex) {
            throw new BizException(500, "公众号模板保存失败");
        }
    }

    private String buildPreviewHtml(String sourceHtml, Map<String, RoleSchema> roles) {
        if (StringUtils.hasText(sourceHtml)) {
            return htmlSanitizer.sanitizeFinalHtml(sourceHtml);
        }
        StringBuilder html = new StringBuilder();
        for (Map.Entry<String, RoleSchema> entry : roles.entrySet()) {
            html.append(entry.getValue().getWrapperHtml()
                    .replace("{{content}}", entry.getKey())
                    .replace("{{text}}", entry.getKey())
                    .replace("{{index}}", "01")
                    .replace("{{imageUrl}}", "")
                    .replace("{{imageAlt}}", ""));
        }
        return htmlSanitizer.sanitizeFinalHtml(html.toString());
    }

    private void fillCreatorNames(List<PlatformRenderTemplate> templates) {
        Map<Long, String> nameMap = buildUserNameMap(templates);
        for (PlatformRenderTemplate template : templates) {
            if (template.getCreatedBy() != null) {
                template.setCreatedByName(nameMap.getOrDefault(template.getCreatedBy(), String.valueOf(template.getCreatedBy())));
            }
        }
    }

    private Map<Long, String> buildUserNameMap(List<PlatformRenderTemplate> templates) {
        Set<Long> userIds = templates.stream()
                .map(PlatformRenderTemplate::getCreatedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> map = new HashMap<>();
        for (SysUser user : sysUserMapper.selectBatchIds(userIds)) {
            String name = StringUtils.hasText(user.getDisplayName()) ? user.getDisplayName() : user.getUsername();
            map.put(user.getId(), StringUtils.hasText(name) ? name : String.valueOf(user.getId()));
        }
        return map;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeStatus(String status) {
        return "disabled".equalsIgnoreCase(status) ? "disabled" : "enabled";
    }

    public static class TemplateSchema {
        public String platformCode;
        public Map<String, RoleSchema> roles;
        public BodyStyle bodyStyle;
    }
}
