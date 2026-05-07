package com.huanjing.geo.module.extension.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.entity.DistributionTask;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.mapper.DistributionTaskMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.customer.access.BrandAccessAction;
import com.huanjing.geo.module.customer.access.BrandAccessService;
import com.huanjing.geo.module.extension.dto.FillTokenIssueRequest;
import com.huanjing.geo.module.extension.dto.FillTokenIssueResponse;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static com.huanjing.geo.module.extension.ExtensionErrorCodes.FILL_TOKEN_INVALID;
import static com.huanjing.geo.module.extension.ExtensionErrorCodes.TASK_NOT_FOUND;
import static com.huanjing.geo.module.extension.ExtensionErrorCodes.TASK_STATE_CONFLICT;
import static com.huanjing.geo.module.extension.ExtensionErrorCodes.EXTENSION_BAD_REQUEST;

@Service
@RequiredArgsConstructor
public class ExtensionFillTokenIssueService {

    private static final String DISPATCH_MODE_SEMI_AUTO = "SEMI_AUTO";
    private static final String STATUS_TOKEN_ISSUED = "token_issued";

    private final DistributionTaskMapper taskMapper;
    private final ProjectMapper projectMapper;
    private final SelfMediaAccountMapper accountMapper;
    private final BrandAccessService brandAccessService;
    private final FillTokenService fillTokenService;

    public FillTokenIssueResponse issue(FillTokenIssueRequest request,
                                        Long operatorId,
                                        String platform,
                                        String extensionVersion) {
        if (request.taskTargetId() == null && (request.accountId() == null || request.brandId() == null)) {
            throw new BizException(EXTENSION_BAD_REQUEST, "must provide taskTargetId or (accountId+brandId)");
        }
        if (request.taskTargetId() == null) {
            return fillTokenService.issue(
                    request.accountId(), request.brandId(), operatorId, null, platform, extensionVersion);
        }
        DistributionTask task = requireTask(request.taskTargetId(), operatorId);
        SelfMediaAccount account = requireAccount(task.getSelfMediaAccountId());
        Long brandId = resolveBrandId(task);
        if (!brandId.equals(account.getBrandId())) {
            throw new BizException(FILL_TOKEN_INVALID, "fill token account brand mismatch");
        }
        if ((request.accountId() != null && !request.accountId().equals(account.getId()))
                || (request.brandId() != null && !request.brandId().equals(brandId))) {
            throw new BizException(FILL_TOKEN_INVALID, "fill token request context mismatch");
        }
        brandAccessService.requireBrandAccess(brandId, operatorId, BrandAccessAction.OPERATE);
        return fillTokenService.issue(account.getId(), brandId, operatorId, task.getId(), platform, extensionVersion);
    }

    private DistributionTask requireTask(Long taskId, Long operatorId) {
        DistributionTask task = taskMapper.selectExtensionFillContext(taskId);
        if (task == null) {
            throw new BizException(TASK_NOT_FOUND, "task not found");
        }
        if (!operatorId.equals(task.getOperatorId())
                || !DISPATCH_MODE_SEMI_AUTO.equals(task.getDispatchMode())
                || !STATUS_TOKEN_ISSUED.equals(task.getStatus())) {
            throw new BizException(TASK_STATE_CONFLICT, "task state conflict");
        }
        return task;
    }

    private SelfMediaAccount requireAccount(Long accountId) {
        SelfMediaAccount account = accountMapper.selectById(accountId);
        if (account == null || account.getDeletedAt() != null) {
            throw new BizException(TASK_NOT_FOUND, "task account not found");
        }
        return account;
    }

    private Long resolveBrandId(DistributionTask task) {
        Project project = projectMapper.selectById(task.getProjectId());
        if (project == null || project.getBrandId() == null) {
            throw new BizException(TASK_NOT_FOUND, "task brand not found");
        }
        return project.getBrandId();
    }
}
