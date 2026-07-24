package com.huanjing.geo.module.project.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.project.dto.BaselineObservationCollectRequest;
import com.huanjing.geo.module.project.dto.BaselineObservationCollectVO;
import com.huanjing.geo.module.project.entity.BaselineCollectionTask;
import com.huanjing.geo.module.project.entity.BaselineObservation;
import com.huanjing.geo.module.project.entity.BaselineQuestionSnapshot;
import com.huanjing.geo.module.project.entity.BaselineSnapshot;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.BaselineCollectionTaskMapper;
import com.huanjing.geo.module.project.mapper.BaselineObservationMapper;
import com.huanjing.geo.module.project.mapper.BaselineQuestionSnapshotMapper;
import com.huanjing.geo.module.project.mapper.BaselineSnapshotMapper;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Service
public class BaselineObservationCollectionService {
    static final int SAMPLE_PER_CELL = 3;
    static final String TASK_STATUS_PENDING = "PENDING";
    static final String TASK_STATUS_RUNNING = "RUNNING";
    static final String TASK_STATUS_COMPLETED = "COMPLETED";
    static final String TASK_STATUS_PARTIAL_FAILED = "PARTIAL_FAILED";
    static final String TASK_STATUS_FAILED = "FAILED";
    static final String TASK_STATUS_CANCELED = "CANCELED";

    private final CurrentUserService currentUserService;
    private final CompanyMapper companyMapper;
    private final ProjectMapper projectMapper;
    private final AiPlatformConfigMapper aiPlatformConfigMapper;
    private final BaselineSnapshotMapper baselineSnapshotMapper;
    private final BaselineQuestionSnapshotMapper baselineQuestionSnapshotMapper;
    private final BaselineObservationMapper baselineObservationMapper;
    private final BaselineCollectionTaskMapper baselineCollectionTaskMapper;
    private final BaselineObservationCollectionWorker collectionWorker;
    private final Executor taskExecutor;

    @Value("${baseline.collection.max-concurrent-baselines:1}")
    private int maxConcurrentBaselines;

    public BaselineObservationCollectionService(CurrentUserService currentUserService,
                                                CompanyMapper companyMapper,
                                                ProjectMapper projectMapper,
                                                AiPlatformConfigMapper aiPlatformConfigMapper,
                                                BaselineSnapshotMapper baselineSnapshotMapper,
                                                BaselineQuestionSnapshotMapper baselineQuestionSnapshotMapper,
                                                BaselineObservationMapper baselineObservationMapper,
                                                BaselineCollectionTaskMapper baselineCollectionTaskMapper,
                                                BaselineObservationCollectionWorker collectionWorker,
                                                @Qualifier("taskExecutor") Executor taskExecutor) {
        this.currentUserService = currentUserService;
        this.companyMapper = companyMapper;
        this.projectMapper = projectMapper;
        this.aiPlatformConfigMapper = aiPlatformConfigMapper;
        this.baselineSnapshotMapper = baselineSnapshotMapper;
        this.baselineQuestionSnapshotMapper = baselineQuestionSnapshotMapper;
        this.baselineObservationMapper = baselineObservationMapper;
        this.baselineCollectionTaskMapper = baselineCollectionTaskMapper;
        this.collectionWorker = collectionWorker;
        this.taskExecutor = taskExecutor;
    }

    @Transactional
    public BaselineObservationCollectVO collect(Long projectId, Long baselineId, BaselineObservationCollectRequest request) {
        SysUser operator = currentUserService.requireCurrentUser();
        requireReadableActiveProject(projectId);
        BaselineSnapshot snapshot = loadSealedSnapshot(projectId, baselineId);
        BaselineCollectionTask activeTask = latestTask(snapshot.getId());
        if (activeTask != null) {
            if (TASK_STATUS_COMPLETED.equals(activeTask.getStatus())) {
                throw new BizException(400, "该基线快照已完成观测采集，封板观测不可重测");
            }
            if (TASK_STATUS_CANCELED.equals(activeTask.getStatus())) {
                throw new BizException(400, "该基线快照采集任务已取消，请新建 A 类 DRAFT 快照后重新采集");
            }
            enqueueAfterCommit(activeTask.getId());
            return toVO(activeTask);
        }
        long existingObservations = baselineObservationMapper.selectCount(new LambdaQueryWrapper<BaselineObservation>()
                .eq(BaselineObservation::getBaselineId, snapshot.getId()));
        if (existingObservations > 0) {
            throw new BizException(400, "该基线快照已有观测记录但缺少采集任务，请先人工核查后再恢复");
        }
        List<BaselineQuestionSnapshot> questions = loadQuestions(snapshot.getId());
        if (questions.isEmpty()) {
            throw new BizException(400, "当前基线快照没有冻结问题");
        }
        List<AiPlatformConfig> platforms = resolvePlatforms(request == null ? null : request.getPlatformCodes());
        if (platforms.isEmpty()) {
            throw new BizException(400, "没有可用于基线采集的平台");
        }

        BaselineCollectionTask task = new BaselineCollectionTask();
        task.setBaselineId(snapshot.getId());
        task.setProjectId(projectId);
        task.setStatus(TASK_STATUS_PENDING);
        task.setSelectedPlatformCodesJson(JSONUtil.toJsonStr(platforms.stream().map(AiPlatformConfig::getPlatformCode).toList()));
        task.setSamplePerCell(SAMPLE_PER_CELL);
        task.setQuestionCount(questions.size());
        task.setPlatformCount(platforms.size());
        task.setTotalObservationCount(questions.size() * platforms.size() * SAMPLE_PER_CELL);
        task.setSuccessObservationCount(0);
        task.setFailedObservationCount(0);
        task.setScoreCount(0);
        task.setCompetitorMentionCount(0);
        task.setCreatedBy(operator.getId());
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(task.getCreatedAt());
        baselineCollectionTaskMapper.insert(task);
        enqueueAfterCommit(task.getId());
        return toVO(task);
    }

    @Transactional
    public BaselineObservationCollectVO cancel(Long projectId, Long baselineId, Long taskId) {
        requireReadableActiveProject(projectId);
        BaselineSnapshot snapshot = loadSealedSnapshot(projectId, baselineId);
        BaselineCollectionTask task = taskId == null ? latestTask(snapshot.getId()) : baselineCollectionTaskMapper.selectById(taskId);
        if (task == null || !snapshot.getId().equals(task.getBaselineId())) {
            throw new BizException(404, "Baseline collection task not found");
        }
        if (TASK_STATUS_COMPLETED.equals(task.getStatus()) || TASK_STATUS_CANCELED.equals(task.getStatus())) {
            return toVO(task);
        }
        task.setStatus(TASK_STATUS_CANCELED);
        task.setErrorMessage("Canceled by user");
        task.setFinishedAt(LocalDateTime.now());
        task.setUpdatedAt(task.getFinishedAt());
        baselineCollectionTaskMapper.updateById(task);
        enqueuePendingAfterCommit();
        return toVO(task);
    }

    public BaselineObservationCollectVO status(Long projectId, Long baselineId, Long taskId) {
        requireReadableActiveProject(projectId);
        BaselineSnapshot snapshot = loadSealedSnapshot(projectId, baselineId);
        BaselineCollectionTask task = taskId == null ? latestTask(snapshot.getId()) : baselineCollectionTaskMapper.selectById(taskId);
        if (task == null || !snapshot.getId().equals(task.getBaselineId())) {
            throw new BizException(404, "Baseline collection task not found");
        }
        return toVO(task);
    }

    private void enqueueAfterCommit(Long taskId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            taskExecutor.execute(() -> collectionWorker.runTask(taskId));
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                taskExecutor.execute(() -> collectionWorker.runTask(taskId));
            }
        });
    }

    private void enqueuePendingAfterCommit() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            taskExecutor.execute(collectionWorker::dispatchPending);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                taskExecutor.execute(collectionWorker::dispatchPending);
            }
        });
    }

    private Project requireReadableActiveProject(Long projectId) {
        SysUser user = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("project.read");
        Project project = projectMapper.selectById(projectId);
        if (project == null || project.getDeletedAt() != null) {
            throw new BizException(404, "Project not found");
        }
        currentUserService.ensurePartnerResourceAccess(user, project.getPartnerId(), "project");
        ensureSalesProjectAccess(user, project);
        if (!"active".equals(project.getStatus())) {
            throw new BizException(400, "仅已启动项目可以采集基线报告");
        }
        return project;
    }

    private void ensureSalesProjectAccess(SysUser user, Project project) {
        if (!"sales".equals(user.getRole())) {
            return;
        }
        Company company = companyMapper.selectById(project.getCompanyId());
        if (company == null || company.getDeletedAt() != null
                || company.getSalesOwnerId() == null || !company.getSalesOwnerId().equals(user.getId())) {
            throw new BizException(403, "No permission to access this project");
        }
        if (!"signed".equals(company.getStatus())) {
            throw new BizException(403, "Sales can only access projects of signed companies");
        }
    }

    private BaselineSnapshot loadSealedSnapshot(Long projectId, Long baselineId) {
        BaselineSnapshot snapshot = baselineSnapshotMapper.selectById(baselineId);
        if (snapshot == null || !projectId.equals(snapshot.getProjectId())) {
            throw new BizException(404, "Baseline snapshot not found");
        }
        if (!"SEALED".equals(snapshot.getStatus())) {
            throw new BizException(400, "仅 SEALED 状态的基线快照允许采集观测");
        }
        return snapshot;
    }

    private BaselineCollectionTask latestTask(Long baselineId) {
        return baselineCollectionTaskMapper.selectOne(new LambdaQueryWrapper<BaselineCollectionTask>()
                .eq(BaselineCollectionTask::getBaselineId, baselineId)
                .orderByDesc(BaselineCollectionTask::getId)
                .last("LIMIT 1"));
    }

    private List<BaselineQuestionSnapshot> loadQuestions(Long baselineId) {
        return baselineQuestionSnapshotMapper.selectList(new LambdaQueryWrapper<BaselineQuestionSnapshot>()
                .eq(BaselineQuestionSnapshot::getBaselineId, baselineId)
                .orderByAsc(BaselineQuestionSnapshot::getSortOrder, BaselineQuestionSnapshot::getId));
    }

    private List<AiPlatformConfig> resolvePlatforms(List<String> selectedCodes) {
        List<AiPlatformConfig> platforms = aiPlatformConfigMapper.selectList(new LambdaQueryWrapper<AiPlatformConfig>()
                        .eq(AiPlatformConfig::getEnabled, true)
                        .eq(AiPlatformConfig::getEnabledForQuestionPoll, true)
                        .orderByAsc(AiPlatformConfig::getPriorityLevel, AiPlatformConfig::getId))
                .stream()
                .filter(platform -> StringUtils.hasText(platform.getApiUrl()))
                .filter(platform -> StringUtils.hasText(platform.getLowModelId()))
                .peek(platform -> platform.setModelId(platform.getLowModelId().trim()))
                .toList();
        if (selectedCodes == null || selectedCodes.isEmpty()) {
            return platforms;
        }
        Set<String> normalized = selectedCodes.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        Map<String, AiPlatformConfig> map = platforms.stream().collect(Collectors.toMap(
                AiPlatformConfig::getPlatformCode,
                item -> item,
                (first, ignored) -> first,
                LinkedHashMap::new
        ));
        List<AiPlatformConfig> selected = new ArrayList<>();
        for (String code : normalized) {
            AiPlatformConfig platform = map.get(code);
            if (platform == null) {
                throw new BizException(400, "平台不可用于基线采集: " + code);
            }
            selected.add(platform);
        }
        return selected;
    }

    private BaselineObservationCollectVO toVO(BaselineCollectionTask task) {
        BaselineObservationCollectVO vo = new BaselineObservationCollectVO();
        vo.setTaskId(task.getId());
        vo.setBaselineId(task.getBaselineId());
        vo.setProjectId(task.getProjectId());
        vo.setStatus(task.getStatus());
        vo.setQuestionCount(task.getQuestionCount());
        vo.setPlatformCount(task.getPlatformCount());
        vo.setSamplePerCell(task.getSamplePerCell());
        vo.setTotalObservationCount(task.getTotalObservationCount());
        vo.setSuccessObservationCount(task.getSuccessObservationCount());
        vo.setFailedObservationCount(task.getFailedObservationCount());
        vo.setScoreCount(task.getScoreCount());
        vo.setCompetitorMentionCount(task.getCompetitorMentionCount());
        vo.setMaxConcurrentBaselines(Math.max(1, maxConcurrentBaselines));
        if (TASK_STATUS_PENDING.equals(task.getStatus())) {
            vo.setQueuePosition(baselineCollectionTaskMapper.queuePosition(task.getId()));
        } else {
            vo.setQueuePosition(0);
        }
        vo.setErrorMessage(task.getErrorMessage());
        return vo;
    }
}
