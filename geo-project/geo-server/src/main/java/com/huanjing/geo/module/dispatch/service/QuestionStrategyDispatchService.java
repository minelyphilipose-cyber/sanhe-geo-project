package com.huanjing.geo.module.dispatch.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.dispatch.entity.DispatchTask;
import com.huanjing.geo.module.dispatch.enums.DispatchTaskStatus;
import com.huanjing.geo.module.dispatch.enums.DispatchTaskType;
import com.huanjing.geo.module.dispatch.mapper.DispatchTaskMapper;
import com.huanjing.geo.module.project.entity.QuestionPoolItem;
import com.huanjing.geo.module.project.entity.QuestionPoolVersion;
import com.huanjing.geo.module.project.mapper.QuestionPoolItemMapper;
import com.huanjing.geo.module.project.mapper.QuestionPoolVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class QuestionStrategyDispatchService {

    private static final List<String> UNFINISHED_STATUS = List.of(
            DispatchTaskStatus.PENDING.value(),
            DispatchTaskStatus.RUNNING.value(),
            DispatchTaskStatus.RETRY_PENDING.value()
    );

    private final QuestionPoolVersionMapper questionPoolVersionMapper;
    private final QuestionPoolItemMapper questionPoolItemMapper;
    private final DispatchTaskMapper dispatchTaskMapper;
    private final DispatchTaskService dispatchTaskService;

    @Transactional
    public DispatchTask enqueueBatchForProject(Long projectId, String triggerSource, boolean force) {
        QuestionPoolVersion latest = latestVersion(projectId);
        if (latest == null) {
            return null;
        }
        long pendingCount = questionPoolItemMapper.selectCount(
                new LambdaQueryWrapper<QuestionPoolItem>()
                        .eq(QuestionPoolItem::getProjectId, projectId)
                        .eq(QuestionPoolItem::getVersionId, latest.getId())
                        .eq(QuestionPoolItem::getPriority, "A")
                        .and(!force, w -> w.isNull(QuestionPoolItem::getStrategyStatus).or().eq(QuestionPoolItem::getStrategyStatus, "none"))
        );
        if (pendingCount <= 0) {
            return null;
        }
        DispatchTask existing = findUnfinishedTask(projectId, "batch", null);
        if (existing != null) {
            return existing;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("mode", "batch");
        payload.put("triggerSource", StringUtils.hasText(triggerSource) ? triggerSource : "manual");
        payload.put("force", force);
        payload.put("questionVersionId", latest.getId());
        return dispatchTaskService.createTaskAndEnqueue(
                projectId,
                DispatchTaskType.QUESTION_STRATEGY_GENERATION,
                LocalDate.now(),
                LocalDate.now(),
                LocalDateTime.now(),
                payload
        );
    }

    @Transactional
    public DispatchTask enqueueSingleQuestion(Long projectId, Long questionId, String triggerSource) {
        QuestionPoolItem item = questionPoolItemMapper.selectById(questionId);
        if (item == null || !projectId.equals(item.getProjectId())) {
            throw new BizException(404, "Question not found");
        }
        DispatchTask existing = findUnfinishedTask(projectId, "single", questionId);
        if (existing != null) {
            return existing;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("mode", "single");
        payload.put("questionId", questionId);
        payload.put("force", true);
        payload.put("triggerSource", StringUtils.hasText(triggerSource) ? triggerSource : "manual");
        return dispatchTaskService.createTaskAndEnqueue(
                projectId,
                DispatchTaskType.QUESTION_STRATEGY_GENERATION,
                LocalDate.now(),
                LocalDate.now(),
                LocalDateTime.now(),
                payload
        );
    }

    private DispatchTask findUnfinishedTask(Long projectId, String mode, Long questionId) {
        List<DispatchTask> tasks = dispatchTaskMapper.selectList(
                new LambdaQueryWrapper<DispatchTask>()
                        .eq(DispatchTask::getProjectId, projectId)
                        .eq(DispatchTask::getTaskType, DispatchTaskType.QUESTION_STRATEGY_GENERATION.name())
                        .in(DispatchTask::getStatus, UNFINISHED_STATUS)
                        .orderByDesc(DispatchTask::getId)
        );
        for (DispatchTask task : tasks) {
            if (!StringUtils.hasText(task.getPayloadJson())) {
                continue;
            }
            try {
                Map<String, Object> payload = JSONUtil.toBean(task.getPayloadJson(), Map.class);
                String payloadMode = payload.get("mode") == null ? null : String.valueOf(payload.get("mode"));
                if (!mode.equals(payloadMode)) {
                    continue;
                }
                if (!"single".equals(mode)) {
                    return task;
                }
                Object rawQuestionId = payload.get("questionId");
                if (rawQuestionId != null && String.valueOf(questionId).equals(String.valueOf(rawQuestionId))) {
                    return task;
                }
            } catch (Exception ignore) {
                // ignore invalid payload
            }
        }
        return null;
    }

    private QuestionPoolVersion latestVersion(Long projectId) {
        return questionPoolVersionMapper.selectOne(
                new LambdaQueryWrapper<QuestionPoolVersion>()
                        .eq(QuestionPoolVersion::getProjectId, projectId)
                        .orderByDesc(QuestionPoolVersion::getVersionNo)
                        .last("LIMIT 1")
        );
    }
}
