package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.constant.ArticlePromptChannels;
import com.huanjing.geo.module.content.constant.TemplatePerspectiveCodes;
import com.huanjing.geo.module.content.dto.TemplatePerspectiveDtos.BrandChannelPerspectiveSaveRequest;
import com.huanjing.geo.module.content.dto.TemplatePerspectiveDtos.BrandChannelPerspectiveVO;
import com.huanjing.geo.module.content.dto.TemplatePerspectiveDtos.ConfigListResponse;
import com.huanjing.geo.module.content.dto.TemplatePerspectiveDtos.PerspectiveVO;
import com.huanjing.geo.module.content.dto.TemplatePerspectiveDtos.ResolveResponse;
import com.huanjing.geo.module.content.entity.BatchArticleGenerationTask;
import com.huanjing.geo.module.content.entity.ArticlePromptTemplate;
import com.huanjing.geo.module.content.entity.BrandChannelTemplatePerspective;
import com.huanjing.geo.module.content.entity.ContentTemplatePerspective;
import com.huanjing.geo.module.content.mapper.ArticlePromptTemplateMapper;
import com.huanjing.geo.module.content.mapper.BatchArticleGenerationTaskMapper;
import com.huanjing.geo.module.content.mapper.BrandChannelTemplatePerspectiveMapper;
import com.huanjing.geo.module.content.mapper.ContentTemplatePerspectiveMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TemplatePerspectiveService {

    public static final String MATCH_SCOPE_EXACT = "exact";
    public static final String MATCH_SCOPE_CHANNEL_ALL = "channel_all";
    public static final String MATCH_SCOPE_DEFAULT = "default";

    private final ContentTemplatePerspectiveMapper perspectiveMapper;
    private final BrandChannelTemplatePerspectiveMapper configMapper;
    private final ArticlePromptTemplateMapper templateMapper;
    private final BatchArticleGenerationTaskMapper taskMapper;
    private final CurrentUserService currentUserService;

    public List<PerspectiveVO> perspectives(boolean includeDisabled) {
        currentUserService.ensurePermission("project.read");
        return listPerspectiveRows(includeDisabled).stream().map(this::toPerspectiveVO).toList();
    }

    public ConfigListResponse brandConfigs(Long brandId) {
        currentUserService.ensurePermission("project.read");
        Map<String, ContentTemplatePerspective> perspectiveMap = perspectiveMap(true);
        List<BrandChannelPerspectiveVO> configs = configMapper.selectList(
                new LambdaQueryWrapper<BrandChannelTemplatePerspective>()
                        .eq(BrandChannelTemplatePerspective::getBrandId, brandId)
                        .orderByAsc(BrandChannelTemplatePerspective::getChannelGroupCode,
                                BrandChannelTemplatePerspective::getChannelSubCode)
        ).stream().map(row -> toConfigVO(row, perspectiveMap)).toList();
        return new ConfigListResponse(
                listPerspectiveRows(true).stream().map(this::toPerspectiveVO).toList(),
                configs
        );
    }

    public ResolveResponse resolvePreview(Long brandId, String channelGroupCode, String channelSubCode) {
        currentUserService.ensurePermission("project.read");
        ResolvedPerspective resolved = resolve(brandId, channelGroupCode, channelSubCode);
        ContentTemplatePerspective perspective = perspectiveMapper.selectById(resolved.perspectiveCode());
        return new ResolveResponse(
                resolved.perspectiveCode(),
                perspective == null ? resolved.perspectiveCode() : perspective.getName(),
                resolved.matchedScope(),
                resolved.matchedConfigId()
        );
    }

    public ResolvedPerspective resolve(Long brandId, String channelGroupCode, String channelSubCode) {
        if (brandId == null) {
            return ResolvedPerspective.customer();
        }
        String group = normalizeGroup(channelGroupCode);
        String sub = normalizeSub(group, channelSubCode);
        BrandChannelTemplatePerspective exact = sub == null ? null : findEnabledConfig(brandId, group, sub);
        if (exact != null) {
            assertPerspectiveCodeExists(exact.getPerspectiveCode());
            return new ResolvedPerspective(exact.getPerspectiveCode(), MATCH_SCOPE_EXACT, exact.getId());
        }
        BrandChannelTemplatePerspective channelAll = findEnabledConfig(brandId, group, TemplatePerspectiveCodes.CHANNEL_SUB_ALL);
        if (channelAll != null) {
            assertPerspectiveCodeExists(channelAll.getPerspectiveCode());
            return new ResolvedPerspective(channelAll.getPerspectiveCode(), MATCH_SCOPE_CHANNEL_ALL, channelAll.getId());
        }
        return ResolvedPerspective.customer();
    }

    @Transactional
    public BrandChannelPerspectiveVO saveBrandConfig(BrandChannelPerspectiveSaveRequest req) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("content.prompt_template.manage");
        String group = normalizeGroup(req.channelGroupCode());
        String sub = normalizeConfigSub(group, req.channelSubCode());
        assertPerspectiveSelectable(req.perspectiveCode());
        BrandChannelTemplatePerspective existing = configMapper.selectOne(
                new LambdaQueryWrapper<BrandChannelTemplatePerspective>()
                        .eq(BrandChannelTemplatePerspective::getBrandId, req.brandId())
                        .eq(BrandChannelTemplatePerspective::getChannelGroupCode, group)
                        .eq(BrandChannelTemplatePerspective::getChannelSubCode, sub)
                        .last("LIMIT 1")
        );
        BrandChannelTemplatePerspective row = existing == null ? new BrandChannelTemplatePerspective() : existing;
        row.setBrandId(req.brandId());
        row.setChannelGroupCode(group);
        row.setChannelSubCode(sub);
        row.setPerspectiveCode(TemplatePerspectiveCodes.normalize(req.perspectiveCode()));
        row.setEnabled(req.enabled() == null || req.enabled());
        row.setUpdatedBy(operator.getId());
        if (existing == null) {
            row.setCreatedBy(operator.getId());
            configMapper.insert(row);
        } else {
            configMapper.updateById(row);
        }
        return toConfigVO(row, perspectiveMap(true));
    }

    @Transactional
    public void deleteBrandConfig(Long id) {
        currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("content.prompt_template.manage");
        Long taskCount = taskMapper.selectCount(
                new LambdaQueryWrapper<BatchArticleGenerationTask>()
                        .eq(BatchArticleGenerationTask::getPerspectiveMatchedConfigId, id)
        );
        if (taskCount != null && taskCount > 0) {
            throw new BizException(400, "Perspective config is referenced by generated tasks, disable it instead");
        }
        configMapper.deleteById(id);
    }

    @Transactional
    public PerspectiveVO updatePerspectiveEnabled(String code, boolean enabled) {
        currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("content.prompt_template.manage");
        ContentTemplatePerspective perspective = perspectiveMapper.selectById(TemplatePerspectiveCodes.normalize(code));
        if (perspective == null) {
            throw new BizException(400, "Template perspective not found");
        }
        if (!enabled) {
            assertPerspectiveCanBeDisabled(perspective.getCode());
        }
        perspective.setEnabled(enabled);
        perspectiveMapper.updateById(perspective);
        return toPerspectiveVO(perspective);
    }

    public void assertPerspectiveSelectable(String code) {
        ContentTemplatePerspective perspective = perspectiveMapper.selectById(TemplatePerspectiveCodes.normalize(code));
        if (perspective == null) {
            throw new BizException(400, "Invalid template perspective");
        }
        if (!Boolean.TRUE.equals(perspective.getEnabled())) {
            throw new BizException(400, "Template perspective is disabled");
        }
    }

    private void assertPerspectiveCodeExists(String code) {
        if (perspectiveMapper.selectById(TemplatePerspectiveCodes.normalize(code)) == null) {
            throw new BizException(400, "Invalid template perspective configuration");
        }
    }

    private void assertPerspectiveCanBeDisabled(String code) {
        Long configCount = configMapper.selectCount(
                new LambdaQueryWrapper<BrandChannelTemplatePerspective>()
                        .eq(BrandChannelTemplatePerspective::getPerspectiveCode, code)
                        .eq(BrandChannelTemplatePerspective::getEnabled, true)
        );
        if (configCount != null && configCount > 0) {
            throw new BizException(400, "Perspective is referenced by enabled brand configs");
        }
        Long templateCount = templateMapper.selectCount(
                new LambdaQueryWrapper<ArticlePromptTemplate>()
                        .eq(ArticlePromptTemplate::getPerspectiveCode, code)
                        .in(ArticlePromptTemplate::getStatus,
                                ArticlePromptTemplateService.STATUS_DRAFT,
                                ArticlePromptTemplateService.STATUS_ACTIVE)
        );
        if (templateCount != null && templateCount > 0) {
            throw new BizException(400, "Perspective is referenced by active or draft prompt templates");
        }
    }

    private BrandChannelTemplatePerspective findEnabledConfig(Long brandId, String group, String sub) {
        return configMapper.selectOne(
                new LambdaQueryWrapper<BrandChannelTemplatePerspective>()
                        .eq(BrandChannelTemplatePerspective::getBrandId, brandId)
                        .eq(BrandChannelTemplatePerspective::getChannelGroupCode, group)
                        .eq(BrandChannelTemplatePerspective::getChannelSubCode, sub)
                        .eq(BrandChannelTemplatePerspective::getEnabled, true)
                        .last("LIMIT 1")
        );
    }

    private List<ContentTemplatePerspective> listPerspectiveRows(boolean includeDisabled) {
        return perspectiveMapper.selectList(
                new LambdaQueryWrapper<ContentTemplatePerspective>()
                        .eq(!includeDisabled, ContentTemplatePerspective::getEnabled, true)
                        .orderByAsc(ContentTemplatePerspective::getSortOrder, ContentTemplatePerspective::getCode)
        );
    }

    private Map<String, ContentTemplatePerspective> perspectiveMap(boolean includeDisabled) {
        return listPerspectiveRows(includeDisabled).stream()
                .collect(Collectors.toMap(ContentTemplatePerspective::getCode, Function.identity(), (a, b) -> a));
    }

    private PerspectiveVO toPerspectiveVO(ContentTemplatePerspective row) {
        return new PerspectiveVO(row.getCode(), row.getName(), row.getDescription(), row.getEnabled(), row.getSortOrder());
    }

    private BrandChannelPerspectiveVO toConfigVO(BrandChannelTemplatePerspective row,
                                                 Map<String, ContentTemplatePerspective> perspectiveMap) {
        ContentTemplatePerspective perspective = perspectiveMap.get(row.getPerspectiveCode());
        return new BrandChannelPerspectiveVO(
                row.getId(),
                row.getBrandId(),
                row.getChannelGroupCode(),
                row.getChannelSubCode(),
                row.getPerspectiveCode(),
                perspective == null ? row.getPerspectiveCode() : perspective.getName(),
                row.getEnabled(),
                row.getCreatedAt(),
                row.getUpdatedAt()
        );
    }

    private String normalizeGroup(String value) {
        String group = trim(value);
        if (!ArticlePromptChannels.isValidCode(group)) {
            throw new BizException(400, "Invalid channel group");
        }
        return group;
    }

    private String normalizeConfigSub(String group, String value) {
        String sub = trimToNull(value);
        if (!StringUtils.hasText(sub) || TemplatePerspectiveCodes.CHANNEL_SUB_ALL.equals(sub)) {
            return TemplatePerspectiveCodes.CHANNEL_SUB_ALL;
        }
        return normalizeSub(group, sub);
    }

    private String normalizeSub(String group, String value) {
        String sub = trimToNull(value);
        if (sub == null) {
            return null;
        }
        if (!ArticlePromptChannels.isValidCode(sub)) {
            throw new BizException(400, "Invalid channel sub code");
        }
        return ArticlePromptChannels.canonicalSubCode(group, sub);
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public record ResolvedPerspective(String perspectiveCode,
                                      String matchedScope,
                                      Long matchedConfigId) {
        public static ResolvedPerspective customer() {
            return new ResolvedPerspective(TemplatePerspectiveCodes.CUSTOMER, MATCH_SCOPE_DEFAULT, null);
        }
    }
}
