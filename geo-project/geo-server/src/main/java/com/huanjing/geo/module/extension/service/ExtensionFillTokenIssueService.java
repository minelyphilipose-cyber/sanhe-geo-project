package com.huanjing.geo.module.extension.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.entity.DistributionTask;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.extension.dto.FillTokenIssueRequest;
import com.huanjing.geo.module.extension.dto.FillTokenIssueResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static com.huanjing.geo.module.extension.ExtensionErrorCodes.FILL_TOKEN_INVALID;
import static com.huanjing.geo.module.extension.ExtensionErrorCodes.TASK_NOT_FOUND;
import static com.huanjing.geo.module.extension.ExtensionErrorCodes.EXTENSION_BAD_REQUEST;

@Service
@RequiredArgsConstructor
public class ExtensionFillTokenIssueService {

    private final SelfMediaAccountMapper accountMapper;
    private final FillTokenService fillTokenService;
    private final SemiAutoTaskAccessService semiAutoTaskAccessService;

    public FillTokenIssueResponse issue(FillTokenIssueRequest request,
                                        Long operatorId,
                                        String platform,
                                        String extensionVersion) {
        if (request.taskTargetId() == null) {
            throw new BizException(EXTENSION_BAD_REQUEST, "taskTargetId is required for fill token issue");
        }
        SemiAutoTaskAccessService.SemiAutoTaskContext context =
                semiAutoTaskAccessService.requireTaskForFillTokenIssue(request.taskTargetId(), operatorId);
        DistributionTask task = context.task();
        SelfMediaAccount account = requireAccount(task.getSelfMediaAccountId());
        Long brandId = context.brandId();
        if (!brandId.equals(account.getBrandId())) {
            throw new BizException(FILL_TOKEN_INVALID, "fill token account brand mismatch");
        }
        if ((request.accountId() != null && !request.accountId().equals(account.getId()))
                || (request.brandId() != null && !request.brandId().equals(brandId))) {
            throw new BizException(FILL_TOKEN_INVALID, "fill token request context mismatch");
        }
        return fillTokenService.issue(account.getId(), brandId, operatorId, task.getId(), platform, extensionVersion);
    }

    private SelfMediaAccount requireAccount(Long accountId) {
        SelfMediaAccount account = accountMapper.selectById(accountId);
        if (account == null || account.getDeletedAt() != null) {
            throw new BizException(TASK_NOT_FOUND, "task account not found");
        }
        return account;
    }

}
