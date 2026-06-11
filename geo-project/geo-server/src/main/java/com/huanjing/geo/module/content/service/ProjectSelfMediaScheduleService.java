package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.constant.SelfMediaPublishScheduleConstants;
import com.huanjing.geo.module.content.dto.ProjectSelfMediaAutoScheduleRequest;
import com.huanjing.geo.module.content.dto.ProjectSelfMediaScheduleConfigRequest;
import com.huanjing.geo.module.content.dto.SelfMediaPublishAutoScheduleRequest;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.entity.ProjectSelfMediaScheduleBatch;
import com.huanjing.geo.module.content.entity.ProjectSelfMediaScheduleConfig;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.mapper.ArticleDraftMapper;
import com.huanjing.geo.module.content.mapper.ProjectSelfMediaScheduleBatchMapper;
import com.huanjing.geo.module.content.mapper.ProjectSelfMediaScheduleConfigMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.content.vo.ProjectSelfMediaScheduleBatchVO;
import com.huanjing.geo.module.content.vo.ProjectSelfMediaScheduleConfigVO;
import com.huanjing.geo.module.content.vo.SelfMediaPublishAutoScheduleResponse;
import com.huanjing.geo.module.customer.access.BrandAccessAction;
import com.huanjing.geo.module.customer.access.BrandAccessService;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectSelfMediaScheduleService {
    private static final int ERROR_CODE = 70043;
    public static final String TRIGGER_MANUAL = "manual";
    public static final String TRIGGER_JOB = "job";

    private final ProjectMapper projectMapper;
    private final ProjectSelfMediaScheduleConfigMapper configMapper;
    private final ProjectSelfMediaScheduleBatchMapper batchMapper;
    private final ArticleDraftMapper articleDraftMapper;
    private final SelfMediaAccountMapper selfMediaAccountMapper;
    private final SelfMediaPublishAutoScheduleService autoScheduleService;
    private final BrandAccessService brandAccessService;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;

    public ProjectSelfMediaScheduleConfigVO getConfig(Long projectId) {
        Project project = requireProject(projectId);
        requireProjectOperate(project);
        ProjectSelfMediaScheduleConfig row = configMapper.selectByProjectId(projectId);
        if (row == null) {
            row = defaultConfig(project);
        }
        return ProjectSelfMediaScheduleConfigVO.from(row);
    }

    @Transactional
    public ProjectSelfMediaScheduleConfigVO updateConfig(Long projectId, ProjectSelfMediaScheduleConfigRequest request) {
        Project project = requireProject(projectId);
        SysUser operator = requireProjectOperate(project);
        ProjectSelfMediaScheduleConfig row = configMapper.selectByProjectId(projectId);
        boolean create = row == null;
        if (create) {
            row = defaultConfig(project);
            row.setCreatedBy(operator.getId());
        }
        if (request != null) {
            if (request.getAutoScheduleEnabled() != null) {
                row.setAutoScheduleEnabled(request.getAutoScheduleEnabled());
            }
            if (StringUtils.hasText(request.getDefaultScheduleStrategy())) {
                row.setDefaultScheduleStrategy(request.getDefaultScheduleStrategy().trim());
            }
            if (request.getIncludeAdjustedWorkdays() != null) {
                row.setIncludeAdjustedWorkdays(request.getIncludeAdjustedWorkdays());
            }
            row.setRemark(trimToNull(request.getRemark()));
        }
        row.setBrandId(project.getBrandId());
        row.setCompanyId(project.getCompanyId());
        row.setUpdatedBy(operator.getId());
        if (create) {
            configMapper.insert(row);
        } else {
            configMapper.updateById(row);
        }
        return ProjectSelfMediaScheduleConfigVO.from(row);
    }

    public ProjectSelfMediaScheduleBatchVO getBatch(Long projectId, String targetMonth) {
        Project project = requireProject(projectId);
        requireProjectOperate(project);
        return ProjectSelfMediaScheduleBatchVO.from(batchMapper.selectByProjectAndMonth(projectId, targetMonth));
    }

    public SelfMediaPublishAutoScheduleResponse previewForProject(Long projectId,
                                                                  ProjectSelfMediaAutoScheduleRequest request) {
        Project project = requireProject(projectId);
        requireProjectOperate(project);
        SelfMediaPublishAutoScheduleRequest autoRequest = toAutoRequest(project, configMapper.selectByProjectId(projectId), request);
        return autoScheduleService.preview(autoRequest);
    }

    @Transactional
    public SelfMediaPublishAutoScheduleResponse createForProject(Long projectId,
                                                                 ProjectSelfMediaAutoScheduleRequest request,
                                                                 String triggerMode) {
        Project project = requireProject(projectId);
        SysUser operator = requireProjectOperate(project);
        ProjectSelfMediaScheduleConfig config = configMapper.selectByProjectId(projectId);
        if (config == null || !Boolean.TRUE.equals(config.getAutoScheduleEnabled())) {
            throw new BizException(ERROR_CODE, "项目未开启自媒体自动排期开关");
        }
        SelfMediaPublishAutoScheduleRequest autoRequest = toAutoRequest(project, config, request);
        ProjectSelfMediaScheduleBatch existed = batchMapper.selectByProjectAndMonth(projectId, autoRequest.getTargetMonth());
        if (existed != null && "created".equals(existed.getStatus())) {
            throw new BizException(ERROR_CODE, "该项目本月已创建过自动化排期，不能重复创建");
        }
        ProjectSelfMediaScheduleBatch batch = existed == null
                ? newBatch(project, autoRequest, triggerMode, operator.getId())
                : existed;
        batch.setStatus("created");
        batch.setTriggerMode(StringUtils.hasText(triggerMode) ? triggerMode : TRIGGER_MANUAL);
        batch.setScheduleStrategy(autoRequest.getScheduleStrategy());
        batch.setArticleCount(autoRequest.getArticleIds().size());
        batch.setAccountCount(autoRequest.getSelfMediaAccountIds().size());
        batch.setRequestPayload(toJson(autoRequest));
        batch.setUpdatedBy(operator.getId());
        try {
            if (batch.getId() == null) {
                batchMapper.insert(batch);
            } else {
                batchMapper.updateById(batch);
            }
        } catch (DuplicateKeyException ex) {
            throw new BizException(ERROR_CODE, "该项目本月已存在自动化排期批次");
        }

        try {
            SelfMediaPublishAutoScheduleResponse response = autoScheduleService.create(autoRequest);
            batch.setPlannedCount(response.getPlannedCount());
            batch.setCreatedCount(response.getCreatedSchedules().size() + response.getExistingSchedules().size());
            batch.setRejectedCount(response.getRejectedItems().size() + response.getRejectedCount());
            batch.setResultSnapshot(toJson(response));
            batch.setFailureMessage(null);
            batchMapper.updateById(batch);
            return response;
        } catch (RuntimeException ex) {
            batch.setStatus("failed");
            batch.setFailureMessage(trimMessage(ex.getMessage()));
            batchMapper.updateById(batch);
            throw ex;
        }
    }

    public int createDueEnabledProjects(String targetMonth, int limit) {
        int processed = 0;
        for (ProjectSelfMediaScheduleConfig config : configMapper.selectEnabled(Math.max(1, limit))) {
            if (processed >= limit) {
                break;
            }
            if (batchMapper.selectByProjectAndMonth(config.getProjectId(), targetMonth) != null) {
                continue;
            }
            Project project = projectMapper.selectById(config.getProjectId());
            if (project == null || project.getDeletedAt() != null) {
                continue;
            }
            List<Long> articleIds = selectProjectArticleIds(project.getId());
            List<Long> accountIds = selectBrandAccountIds(project.getBrandId());
            if (articleIds.isEmpty() || accountIds.isEmpty()) {
                continue;
            }
            ProjectSelfMediaAutoScheduleRequest request = new ProjectSelfMediaAutoScheduleRequest();
            request.setArticleIds(articleIds);
            request.setSelfMediaAccountIds(accountIds);
            request.setTargetMonth(targetMonth);
            request.setScheduleStrategy(config.getDefaultScheduleStrategy());
            request.setIncludeAdjustedWorkdays(config.getIncludeAdjustedWorkdays());
            createForProjectSystem(project, config, request);
            processed++;
        }
        return processed;
    }

    private SelfMediaPublishAutoScheduleResponse createForProjectSystem(Project project,
                                                                        ProjectSelfMediaScheduleConfig config,
                                                                        ProjectSelfMediaAutoScheduleRequest request) {
        Long operatorId = config.getUpdatedBy() != null && config.getUpdatedBy() > 0
                ? config.getUpdatedBy()
                : config.getCreatedBy();
        if (operatorId == null || operatorId <= 0) {
            operatorId = project.getCreatedBy();
        }
        if (operatorId == null || operatorId <= 0) {
            throw new BizException(ERROR_CODE, "项目自动排期缺少系统操作人");
        }
        SelfMediaPublishAutoScheduleRequest autoRequest = toAutoRequest(project, config, request);
        ProjectSelfMediaScheduleBatch batch = newBatch(project, autoRequest, TRIGGER_JOB, operatorId);
        batch.setRequestPayload(toJson(autoRequest));
        try {
            batchMapper.insert(batch);
        } catch (DuplicateKeyException ex) {
            throw new BizException(ERROR_CODE, "该项目本月已存在自动化排期批次");
        }
        try {
            SelfMediaPublishAutoScheduleResponse response = autoScheduleService.createSystem(autoRequest, operatorId);
            batch.setPlannedCount(response.getPlannedCount());
            batch.setCreatedCount(response.getCreatedSchedules().size() + response.getExistingSchedules().size());
            batch.setRejectedCount(response.getRejectedItems().size() + response.getRejectedCount());
            batch.setResultSnapshot(toJson(response));
            batchMapper.updateById(batch);
            return response;
        } catch (RuntimeException ex) {
            batch.setStatus("failed");
            batch.setFailureMessage(trimMessage(ex.getMessage()));
            batchMapper.updateById(batch);
            throw ex;
        }
    }

    private List<Long> selectProjectArticleIds(Long projectId) {
        return articleDraftMapper.selectList(new LambdaQueryWrapper<ArticleDraft>()
                        .eq(ArticleDraft::getProjectId, projectId)
                        .in(ArticleDraft::getStatus, List.of("approved", "unpublished"))
                        .orderByAsc(ArticleDraft::getId)
                        .last("LIMIT 100"))
                .stream()
                .map(ArticleDraft::getId)
                .toList();
    }

    private List<Long> selectBrandAccountIds(Long brandId) {
        if (brandId == null || brandId <= 0) {
            return List.of();
        }
        return selfMediaAccountMapper.selectList(new LambdaQueryWrapper<SelfMediaAccount>()
                        .eq(SelfMediaAccount::getBrandId, brandId)
                        .eq(SelfMediaAccount::getStatus, "active")
                        .isNull(SelfMediaAccount::getDeletedAt)
                        .orderByAsc(SelfMediaAccount::getId)
                        .last("LIMIT 50"))
                .stream()
                .map(SelfMediaAccount::getId)
                .toList();
    }

    private SelfMediaPublishAutoScheduleRequest toAutoRequest(Project project,
                                                              ProjectSelfMediaScheduleConfig config,
                                                              ProjectSelfMediaAutoScheduleRequest request) {
        if (request == null) {
            throw new BizException(ERROR_CODE, "request is required");
        }
        if (project.getBrandId() == null || project.getBrandId() <= 0) {
            throw new BizException(ERROR_CODE, "项目未绑定品牌，不能创建自媒体排期");
        }
        SelfMediaPublishAutoScheduleRequest autoRequest = new SelfMediaPublishAutoScheduleRequest();
        autoRequest.setBrandId(project.getBrandId());
        autoRequest.setArticleIds(request.getArticleIds());
        autoRequest.setSelfMediaAccountIds(request.getSelfMediaAccountIds());
        autoRequest.setTargetMonth(request.getTargetMonth());
        autoRequest.setScheduleStrategy(firstText(
                request.getScheduleStrategy(),
                config == null ? null : config.getDefaultScheduleStrategy(),
                SelfMediaPublishScheduleConstants.STRATEGY_PLATFORM_SCHEDULE
        ));
        autoRequest.setIncludeAdjustedWorkdays(request.getIncludeAdjustedWorkdays() != null
                ? request.getIncludeAdjustedWorkdays()
                : config != null && Boolean.TRUE.equals(config.getIncludeAdjustedWorkdays()));
        return autoRequest;
    }

    private ProjectSelfMediaScheduleBatch newBatch(Project project,
                                                   SelfMediaPublishAutoScheduleRequest request,
                                                   String triggerMode,
                                                   Long operatorId) {
        ProjectSelfMediaScheduleBatch batch = new ProjectSelfMediaScheduleBatch();
        batch.setProjectId(project.getId());
        batch.setBrandId(project.getBrandId());
        batch.setCompanyId(project.getCompanyId());
        batch.setTargetMonth(request.getTargetMonth());
        batch.setTriggerMode(StringUtils.hasText(triggerMode) ? triggerMode : TRIGGER_MANUAL);
        batch.setStatus("created");
        batch.setScheduleStrategy(request.getScheduleStrategy());
        batch.setArticleCount(request.getArticleIds().size());
        batch.setAccountCount(request.getSelfMediaAccountIds().size());
        batch.setPlannedCount(0);
        batch.setCreatedCount(0);
        batch.setRejectedCount(0);
        batch.setCreatedBy(operatorId);
        batch.setUpdatedBy(operatorId);
        return batch;
    }

    private ProjectSelfMediaScheduleConfig defaultConfig(Project project) {
        ProjectSelfMediaScheduleConfig row = new ProjectSelfMediaScheduleConfig();
        row.setProjectId(project.getId());
        row.setBrandId(project.getBrandId());
        row.setCompanyId(project.getCompanyId());
        row.setAutoScheduleEnabled(false);
        row.setDefaultScheduleStrategy(SelfMediaPublishScheduleConstants.STRATEGY_PLATFORM_SCHEDULE);
        row.setIncludeAdjustedWorkdays(false);
        return row;
    }

    private Project requireProject(Long projectId) {
        if (projectId == null || projectId <= 0) {
            throw new BizException(ERROR_CODE, "projectId is required");
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null || project.getDeletedAt() != null) {
            throw new BizException(ERROR_CODE, "项目不存在");
        }
        if (project.getCompanyId() == null || project.getCompanyId() <= 0) {
            throw new BizException(ERROR_CODE, "项目未关联有效客户");
        }
        return project;
    }

    private SysUser requireProjectOperate(Project project) {
        SysUser operator = currentUserService.requireCurrentUser();
        if (project.getBrandId() != null && project.getBrandId() > 0) {
            brandAccessService.requireBrandAccess(project.getBrandId(), operator.getId(), BrandAccessAction.OPERATE);
        }
        return operator;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String trimMessage(String value) {
        String text = trimToNull(value);
        if (text == null) {
            return null;
        }
        return text.length() <= 512 ? text : text.substring(0, 512);
    }
}
