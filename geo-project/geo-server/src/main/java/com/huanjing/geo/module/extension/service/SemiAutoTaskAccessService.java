package com.huanjing.geo.module.extension.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.entity.DistributionTask;
import com.huanjing.geo.module.content.mapper.DistributionTaskMapper;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

import static com.huanjing.geo.module.extension.ExtensionErrorCodes.FILL_TOKEN_BINDING_MISMATCH;
import static com.huanjing.geo.module.extension.ExtensionErrorCodes.FILL_TOKEN_OPERATOR_MISMATCH;
import static com.huanjing.geo.module.extension.ExtensionErrorCodes.TASK_NOT_FOUND;
import static com.huanjing.geo.module.extension.ExtensionErrorCodes.TASK_STATE_CONFLICT;

@Service
@RequiredArgsConstructor
public class SemiAutoTaskAccessService {

    public static final String DISPATCH_MODE_SEMI_AUTO = "SEMI_AUTO";
    public static final String STATUS_TOKEN_ISSUED = "token_issued";
    public static final String STATUS_FILLING = "filling";
    public static final String STATUS_FILLED = "filled";

    private final DistributionTaskMapper taskMapper;
    private final ProjectMapper projectMapper;

    public SemiAutoTaskContext requireTaskForFillTokenIssue(Long taskId, Long operatorId) {
        SemiAutoTaskContext context = requireOwnedSemiAutoTask(taskId, operatorId);
        // DB lifecycle gate only; FillTokenService owns the task-level active-token guard.
        if (!STATUS_TOKEN_ISSUED.equals(context.task().getStatus())) {
            throw new BizException(TASK_STATE_CONFLICT, "task is not ready for fill token issue");
        }
        return context;
    }

    public SemiAutoTaskContext requireOperableTask(Long taskId, Long operatorId) {
        SemiAutoTaskContext context = requireOwnedSemiAutoTask(taskId, operatorId);
        if (!STATUS_TOKEN_ISSUED.equals(context.task().getStatus())
                && !STATUS_FILLING.equals(context.task().getStatus())
                && !STATUS_FILLED.equals(context.task().getStatus())) {
            throw new BizException(TASK_STATE_CONFLICT, "task state conflict");
        }
        return context;
    }

    public SemiAutoTaskContext validateFillTokenTask(FillTokenPayload payload, Long expectedOperatorId) {
        if (payload == null) {
            throw new BizException(FILL_TOKEN_BINDING_MISMATCH, "fill token payload missing");
        }
        if (expectedOperatorId != null && payload.op() != expectedOperatorId) {
            throw new BizException(FILL_TOKEN_OPERATOR_MISMATCH, "fill token operator mismatch");
        }
        SemiAutoTaskContext context = requireOwnedSemiAutoTask(payload.tid(), payload.op());
        if (!Objects.equals(context.brandId(), payload.bid())
                || !Objects.equals(context.task().getSelfMediaAccountId(), payload.aid())) {
            throw new BizException(FILL_TOKEN_BINDING_MISMATCH, "fill token binding mismatch");
        }
        if (!STATUS_TOKEN_ISSUED.equals(context.task().getStatus())) {
            throw new BizException(TASK_STATE_CONFLICT, "task state conflict");
        }
        if (context.task().getArticleId() == null) {
            throw new BizException(FILL_TOKEN_BINDING_MISMATCH, "task article binding missing");
        }
        return context;
    }

    public SemiAutoTaskContext requireDistributionRetryTask(Long taskId, Long operatorId) {
        return requireOwnedTask(taskId, operatorId);
    }

    private SemiAutoTaskContext requireOwnedSemiAutoTask(Long taskId, Long operatorId) {
        SemiAutoTaskContext context = requireOwnedTask(taskId, operatorId);
        if (!DISPATCH_MODE_SEMI_AUTO.equals(context.task().getDispatchMode())) {
            throw new BizException(TASK_STATE_CONFLICT, "task state conflict");
        }
        return context;
    }

    private SemiAutoTaskContext requireOwnedTask(Long taskId, Long operatorId) {
        if (taskId == null) {
            throw new BizException(TASK_NOT_FOUND, "task not found");
        }
        DistributionTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(TASK_NOT_FOUND, "task not found");
        }
        if (!Objects.equals(task.getOperatorId(), operatorId)) {
            throw new BizException(FILL_TOKEN_OPERATOR_MISMATCH, "task operator mismatch");
        }
        Long brandId = resolveBrandId(task);
        return new SemiAutoTaskContext(task, brandId);
    }

    private Long resolveBrandId(DistributionTask task) {
        Project project = task.getProjectId() == null ? null : projectMapper.selectById(task.getProjectId());
        if (project == null || project.getBrandId() == null) {
            throw new BizException(TASK_NOT_FOUND, "task brand not found");
        }
        return project.getBrandId();
    }

    public record SemiAutoTaskContext(DistributionTask task, Long brandId) {
    }
}
