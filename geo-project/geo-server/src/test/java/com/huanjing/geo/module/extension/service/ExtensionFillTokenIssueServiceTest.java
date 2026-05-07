package com.huanjing.geo.module.extension.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.entity.DistributionTask;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.mapper.DistributionTaskMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.customer.access.BrandAccessAction;
import com.huanjing.geo.module.customer.access.BrandAccessService;
import com.huanjing.geo.module.extension.ExtensionErrorCodes;
import com.huanjing.geo.module.extension.dto.FillTokenIssueRequest;
import com.huanjing.geo.module.extension.dto.FillTokenIssueResponse;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExtensionFillTokenIssueServiceTest {

    private DistributionTaskMapper taskMapper;
    private ProjectMapper projectMapper;
    private SelfMediaAccountMapper accountMapper;
    private BrandAccessService brandAccessService;
    private FillTokenService fillTokenService;
    private ExtensionFillTokenIssueService service;

    @BeforeEach
    void setUp() {
        taskMapper = mock(DistributionTaskMapper.class);
        projectMapper = mock(ProjectMapper.class);
        accountMapper = mock(SelfMediaAccountMapper.class);
        brandAccessService = mock(BrandAccessService.class);
        fillTokenService = mock(FillTokenService.class);
        service = new ExtensionFillTokenIssueService(
                taskMapper, projectMapper, accountMapper, brandAccessService, fillTokenService);
    }

    @Test
    void issueByTaskTargetResolvesAccountAndBrandContext() {
        mockTaskContext(99L, 10L);
        when(fillTokenService.issue(20L, 10L, 99L, 30L, "toutiao", "0.1.0"))
                .thenReturn(new FillTokenIssueResponse("fill-token", 200L, "nonce-1"));

        FillTokenIssueResponse response = service.issue(request(null, null), 99L, "toutiao", "0.1.0");

        assertEquals("fill-token", response.fillToken());
        verify(brandAccessService).requireBrandAccess(10L, 99L, BrandAccessAction.OPERATE);
    }

    @Test
    void issueWithLegacyAccountBrandPathDelegatesToFillTokenService() {
        when(fillTokenService.issue(20L, 10L, 99L, null, "toutiao", "0.1.0"))
                .thenReturn(new FillTokenIssueResponse("fill-token", 200L, "nonce-1"));

        FillTokenIssueResponse response = service.issue(request(10L, 20L, null), 99L, "toutiao", "0.1.0");

        assertEquals("fill-token", response.fillToken());
    }

    @Test
    void issueByTaskTargetRejectsMismatchedOperator() {
        when(taskMapper.selectExtensionFillContext(30L)).thenReturn(task("token_issued", 88L));

        BizException ex = assertThrows(BizException.class, () -> service.issue(
                request(null, null), 99L, "toutiao", "0.1.0"
        ));

        assertEquals(ExtensionErrorCodes.TASK_STATE_CONFLICT, ex.getCode());
    }

    @Test
    void issueRejectsEmptyRequest() {
        BizException ex = assertThrows(BizException.class, () -> service.issue(
                request(null, null, null), 99L, "toutiao", "0.1.0"
        ));

        assertEquals(ExtensionErrorCodes.EXTENSION_BAD_REQUEST, ex.getCode());
    }

    @Test
    void issueByTaskTargetRejectsRequestContextMismatch() {
        mockTaskContext(99L, 10L);

        BizException ex = assertThrows(BizException.class, () -> service.issue(
                request(11L, 20L, 30L), 99L, "toutiao", "0.1.0"
        ));

        assertEquals(ExtensionErrorCodes.FILL_TOKEN_INVALID, ex.getCode());
    }

    private void mockTaskContext(Long operatorId, Long brandId) {
        when(taskMapper.selectExtensionFillContext(30L)).thenReturn(task("token_issued", operatorId));
        SelfMediaAccount account = new SelfMediaAccount();
        account.setId(20L);
        account.setBrandId(brandId);
        when(accountMapper.selectById(20L)).thenReturn(account);
        Project project = new Project();
        project.setId(40L);
        project.setBrandId(brandId);
        when(projectMapper.selectById(40L)).thenReturn(project);
    }

    private FillTokenIssueRequest request(Long brandId, Long accountId) {
        return request(brandId, accountId, 30L);
    }

    private FillTokenIssueRequest request(Long brandId, Long accountId, Long taskTargetId) {
        return new FillTokenIssueRequest(brandId, accountId, taskTargetId, "0.1.0", "toutiao");
    }

    private DistributionTask task(String status, Long operatorId) {
        DistributionTask task = new DistributionTask();
        task.setId(30L);
        task.setProjectId(40L);
        task.setSelfMediaAccountId(20L);
        task.setStatus(status);
        task.setDispatchMode("SEMI_AUTO");
        task.setOperatorId(operatorId);
        return task;
    }
}
