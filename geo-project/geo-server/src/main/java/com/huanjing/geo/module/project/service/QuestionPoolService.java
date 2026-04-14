package com.huanjing.geo.module.project.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.dispatch.entity.DispatchTask;
import com.huanjing.geo.module.dispatch.service.QuestionStrategyDispatchService;
import com.huanjing.geo.module.project.dto.QuestionPoolItemRequest;
import com.huanjing.geo.module.project.dto.QuestionPoolItemVO;
import com.huanjing.geo.module.project.dto.QuestionPoolManageItemVO;
import com.huanjing.geo.module.project.dto.QuestionStrategyUpdateRequest;
import com.huanjing.geo.module.project.dto.QuestionPoolVersionVO;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.entity.QuestionPoolItem;
import com.huanjing.geo.module.project.entity.QuestionPoolVersion;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.project.mapper.QuestionPoolItemMapper;
import com.huanjing.geo.module.project.mapper.QuestionPoolVersionMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QuestionPoolService {

    private static final Set<String> QUESTION_TYPES = Set.of(
            "brand", "location", "industry", "decision", "transaction", "qa", "comparison", "competitor"
    );
    private static final Set<String> PRIORITY_VALUES = Set.of("A", "B", "C");

    private final QuestionPoolVersionMapper questionPoolVersionMapper;
    private final QuestionPoolItemMapper questionPoolItemMapper;
    private final ProjectMapper projectMapper;
    private final CurrentUserService currentUserService;
    private final QuestionStrategyDispatchService questionStrategyDispatchService;

    @Transactional
    public QuestionPoolVersion createVersion(
            Long projectId,
            Long operatorUserId,
            String changeReason,
            List<QuestionPoolItemRequest> requestItems
    ) {
        List<QuestionPoolItemRequest> items = requestItems == null ? List.of() : requestItems;
        validateItems(items);

        QuestionPoolVersion latest = questionPoolVersionMapper.selectOne(
                new LambdaQueryWrapper<QuestionPoolVersion>()
                        .eq(QuestionPoolVersion::getProjectId, projectId)
                        .orderByDesc(QuestionPoolVersion::getVersionNo)
                        .last("LIMIT 1")
        );
        int maxVersion = latest == null || latest.getVersionNo() == null ? 0 : latest.getVersionNo();

        QuestionPoolVersion version = new QuestionPoolVersion();
        version.setProjectId(projectId);
        version.setVersionNo(maxVersion + 1);
        version.setCreatedBy(operatorUserId);
        version.setChangeReason(StringUtils.hasText(changeReason) ? changeReason.trim() : null);
        questionPoolVersionMapper.insert(version);

        if (!items.isEmpty()) {
            for (QuestionPoolItemRequest req : items) {
                QuestionPoolItem item = new QuestionPoolItem();
                item.setVersionId(version.getId());
                item.setProjectId(projectId);
                item.setQuestionText(req.getQuestionText().trim());
                item.setQuestionType(req.getQuestionType().trim());
                item.setPriority(req.getPriority().trim());
                item.setIsCore(req.getIsCore());
                item.setStrategyStatus("none");
                questionPoolItemMapper.insert(item);
            }
        }
        questionStrategyDispatchService.enqueueBatchForProject(projectId, "question_pool_confirm", false);
        return version;
    }

    @Transactional
    public DispatchTask triggerBatchStrategyGeneration(Long projectId, String triggerSource) {
        Project project = requireProjectForWrite(projectId);
        SysUser user = currentUserService.requireCurrentUser();
        currentUserService.ensurePartnerResourceAccess(user, project.getPartnerId(), "project");
        return questionStrategyDispatchService.enqueueBatchForProject(projectId, triggerSource, false);
    }

    @Transactional
    public DispatchTask triggerSingleStrategyGeneration(Long questionId, String triggerSource) {
        currentUserService.ensurePermission("project.write");
        SysUser user = currentUserService.requireCurrentUser();
        QuestionPoolItem item = questionPoolItemMapper.selectById(questionId);
        if (item == null) {
            throw new BizException(404, "Question not found");
        }
        Project project = requireProjectForWrite(item.getProjectId());
        currentUserService.ensurePartnerResourceAccess(user, project.getPartnerId(), "project");
        return questionStrategyDispatchService.enqueueSingleQuestion(item.getProjectId(), item.getId(), triggerSource);
    }

    @Transactional
    public void updateQuestionStrategy(Long questionId, QuestionStrategyUpdateRequest req) {
        currentUserService.ensurePermission("project.write");
        SysUser user = currentUserService.requireCurrentUser();
        QuestionPoolItem item = questionPoolItemMapper.selectById(questionId);
        if (item == null) {
            throw new BizException(404, "Question not found");
        }
        Project project = requireProjectForWrite(item.getProjectId());
        currentUserService.ensurePartnerResourceAccess(user, project.getPartnerId(), "project");

        String suggestedType = normalizeSuggestedType(req.getStrategySuggestedType());
        QuestionPoolItem update = new QuestionPoolItem();
        update.setId(item.getId());
        update.setContentStrategy(req.getContentStrategy().trim());
        update.setStrategyKeywords(JSONUtil.toJsonStr(normalizeKeywords(req.getStrategyKeywords())));
        update.setStrategySuggestedType(suggestedType);
        update.setStrategyStatus("edited");
        update.setStrategyGeneratedAt(LocalDateTime.now());
        questionPoolItemMapper.updateById(update);
    }

    public QuestionPoolVersionVO currentVersion(Long projectId) {
        Project project = requireProjectForRead(projectId);
        currentUserService.ensurePermission("project.read");
        SysUser user = currentUserService.requireCurrentUser();
        currentUserService.ensurePartnerResourceAccess(user, project.getPartnerId(), "project");

        QuestionPoolVersion latest = questionPoolVersionMapper.selectOne(
                new LambdaQueryWrapper<QuestionPoolVersion>()
                        .eq(QuestionPoolVersion::getProjectId, projectId)
                        .orderByDesc(QuestionPoolVersion::getVersionNo)
                        .last("LIMIT 1")
        );
        if (latest == null) {
            return null;
        }
        return toVersionVO(latest, true);
    }

    public Page<QuestionPoolVersionVO> pageVersions(Long projectId, long current, long size) {
        Project project = requireProjectForRead(projectId);
        currentUserService.ensurePermission("project.read");
        SysUser user = currentUserService.requireCurrentUser();
        currentUserService.ensurePartnerResourceAccess(user, project.getPartnerId(), "project");

        Page<QuestionPoolVersion> page = questionPoolVersionMapper.selectPage(
                new Page<>(current, size),
                new LambdaQueryWrapper<QuestionPoolVersion>()
                        .eq(QuestionPoolVersion::getProjectId, projectId)
                        .orderByDesc(QuestionPoolVersion::getVersionNo)
        );
        Page<QuestionPoolVersionVO> voPage = new Page<>(current, size, page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(v -> toVersionVO(v, false)).collect(Collectors.toList()));
        return voPage;
    }

    public QuestionPoolVersionVO versionDetail(Long projectId, Integer versionNo) {
        Project project = requireProjectForRead(projectId);
        currentUserService.ensurePermission("project.read");
        SysUser user = currentUserService.requireCurrentUser();
        currentUserService.ensurePartnerResourceAccess(user, project.getPartnerId(), "project");
        QuestionPoolVersion version = questionPoolVersionMapper.selectOne(
                new LambdaQueryWrapper<QuestionPoolVersion>()
                        .eq(QuestionPoolVersion::getProjectId, projectId)
                        .eq(QuestionPoolVersion::getVersionNo, versionNo)
        );
        if (version == null) {
            throw new BizException(404, "Question pool version not found");
        }
        return toVersionVO(version, true);
    }

    public Page<QuestionPoolManageItemVO> pageManage(long current, long size, String keyword, Long projectId) {
        currentUserService.ensurePermission("project.read");
        SysUser user = currentUserService.requireCurrentUser();

        LambdaQueryWrapper<QuestionPoolVersion> wrapper = new LambdaQueryWrapper<QuestionPoolVersion>()
                .orderByDesc(QuestionPoolVersion::getCreatedAt, QuestionPoolVersion::getId);
        if (projectId != null) {
            wrapper.eq(QuestionPoolVersion::getProjectId, projectId);
        }

        Page<QuestionPoolVersion> page = questionPoolVersionMapper.selectPage(new Page<>(current, size), wrapper);
        List<QuestionPoolVersion> versionRecords = page.getRecords();
        if (versionRecords.isEmpty()) {
            return new Page<>(current, size, page.getTotal());
        }

        List<Long> projectIds = versionRecords.stream().map(QuestionPoolVersion::getProjectId).distinct().collect(Collectors.toList());
        List<Project> projects = projectMapper.selectList(
                new LambdaQueryWrapper<Project>().in(Project::getId, projectIds)
        );
        Map<Long, Project> projectMap = new HashMap<>();
        for (Project project : projects) {
            projectMap.put(project.getId(), project);
        }

        List<QuestionPoolManageItemVO> records = new ArrayList<>();
        for (QuestionPoolVersion version : versionRecords) {
            Project project = projectMap.get(version.getProjectId());
            if (project == null) {
                continue;
            }
            currentUserService.ensurePartnerResourceAccess(user, project.getPartnerId(), "project");
            if (StringUtils.hasText(keyword) && !project.getProjectName().contains(keyword.trim())) {
                continue;
            }

            int total = questionPoolItemMapper.selectCount(
                    new LambdaQueryWrapper<QuestionPoolItem>().eq(QuestionPoolItem::getVersionId, version.getId())
            ).intValue();
            int core = questionPoolItemMapper.selectCount(
                    new LambdaQueryWrapper<QuestionPoolItem>()
                            .eq(QuestionPoolItem::getVersionId, version.getId())
                            .eq(QuestionPoolItem::getIsCore, true)
            ).intValue();

            QuestionPoolManageItemVO vo = new QuestionPoolManageItemVO();
            vo.setProjectId(version.getProjectId());
            vo.setProjectName(project.getProjectName());
            vo.setVersionNo(version.getVersionNo());
            vo.setTotalQuestions(total);
            vo.setCoreQuestions(core);
            vo.setChangeReason(version.getChangeReason());
            vo.setCreatedBy(version.getCreatedBy());
            vo.setCreatedAt(version.getCreatedAt());
            records.add(vo);
        }

        Page<QuestionPoolManageItemVO> result = new Page<>(current, size, page.getTotal());
        result.setRecords(records);
        return result;
    }

    public Set<String> latestCoreQuestionTextSet(Long projectId) {
        QuestionPoolVersion latest = questionPoolVersionMapper.selectOne(
                new LambdaQueryWrapper<QuestionPoolVersion>()
                        .eq(QuestionPoolVersion::getProjectId, projectId)
                        .orderByDesc(QuestionPoolVersion::getVersionNo)
                        .last("LIMIT 1")
        );
        if (latest == null) {
            return Collections.emptySet();
        }
        List<QuestionPoolItem> coreItems = questionPoolItemMapper.selectList(
                new LambdaQueryWrapper<QuestionPoolItem>()
                        .eq(QuestionPoolItem::getVersionId, latest.getId())
                        .eq(QuestionPoolItem::getIsCore, true)
        );
        Set<String> result = new HashSet<>();
        for (QuestionPoolItem item : coreItems) {
            if (item != null && StringUtils.hasText(item.getQuestionText())) {
                result.add(item.getQuestionText().trim());
            }
        }
        return result;
    }

    private QuestionPoolVersionVO toVersionVO(QuestionPoolVersion version, boolean withItems) {
        QuestionPoolVersionVO vo = new QuestionPoolVersionVO();
        vo.setId(version.getId());
        vo.setProjectId(version.getProjectId());
        vo.setVersionNo(version.getVersionNo());
        vo.setCreatedBy(version.getCreatedBy());
        vo.setCreatedAt(version.getCreatedAt());
        vo.setChangeReason(version.getChangeReason());

        List<QuestionPoolItem> items = questionPoolItemMapper.selectList(
                new LambdaQueryWrapper<QuestionPoolItem>()
                        .eq(QuestionPoolItem::getVersionId, version.getId())
                        .orderByDesc(QuestionPoolItem::getIsCore)
                        .orderByDesc(QuestionPoolItem::getCreatedAt, QuestionPoolItem::getId)
        );
        vo.setTotalQuestions(items.size());
        vo.setCoreQuestions((int) items.stream().filter(i -> Boolean.TRUE.equals(i.getIsCore())).count());
        if (withItems) {
            List<QuestionPoolItemVO> itemVOs = new ArrayList<>();
            for (QuestionPoolItem item : items) {
                QuestionPoolItemVO itemVO = new QuestionPoolItemVO();
                itemVO.setId(item.getId());
                itemVO.setProjectId(item.getProjectId());
                itemVO.setVersionId(item.getVersionId());
                itemVO.setQuestionText(item.getQuestionText());
                itemVO.setQuestionType(item.getQuestionType());
                itemVO.setPriority(item.getPriority());
                itemVO.setIsCore(item.getIsCore());
                itemVO.setContentStrategy(item.getContentStrategy());
                itemVO.setStrategyKeywords(item.getStrategyKeywords());
                itemVO.setStrategySuggestedType(item.getStrategySuggestedType());
                itemVO.setStrategyGeneratedAt(item.getStrategyGeneratedAt());
                itemVO.setStrategyStatus(item.getStrategyStatus());
                itemVOs.add(itemVO);
            }
            vo.setItems(itemVOs);
        }
        return vo;
    }

    private void validateItems(List<QuestionPoolItemRequest> items) {
        for (QuestionPoolItemRequest item : items) {
            if (item == null || !StringUtils.hasText(item.getQuestionText())) {
                throw new BizException(400, "question_text is required");
            }
            if (!StringUtils.hasText(item.getQuestionType()) || !QUESTION_TYPES.contains(item.getQuestionType().trim())) {
                throw new BizException(400, "Invalid question_type");
            }
            if (!StringUtils.hasText(item.getPriority()) || !PRIORITY_VALUES.contains(item.getPriority().trim())) {
                throw new BizException(400, "Invalid priority, must be A/B/C");
            }
            if (item.getIsCore() == null) {
                throw new BizException(400, "is_core is required");
            }
        }
    }

    private Project requireProjectForRead(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BizException(404, "Project not found");
        }
        return project;
    }

    private Project requireProjectForWrite(Long projectId) {
        currentUserService.ensurePermission("project.write");
        return requireProjectForRead(projectId);
    }

    private List<String> normalizeKeywords(List<String> keywords) {
        if (keywords == null) {
            return List.of();
        }
        return keywords.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .limit(5)
                .collect(Collectors.toList());
    }

    private String normalizeSuggestedType(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new BizException(400, "strategy_suggested_type is required");
        }
        String value = raw.trim().toLowerCase();
        if (!Set.of("faq", "scenario_content", "industry_article").contains(value)) {
            throw new BizException(400, "strategy_suggested_type must be faq/scenario_content/industry_article");
        }
        return value;
    }
}
