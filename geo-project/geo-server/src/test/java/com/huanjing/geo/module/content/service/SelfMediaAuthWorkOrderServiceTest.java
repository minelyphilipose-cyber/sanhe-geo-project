package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.module.content.entity.SelfMediaAuthWorkOrder;
import com.huanjing.geo.module.content.entity.SelfMediaAuthWorkOrderEvent;
import com.huanjing.geo.module.content.entity.BrowserEnvironmentAccount;
import com.huanjing.geo.module.content.dto.SelfMediaAuthWorkOrderUpdateRequest;
import com.huanjing.geo.module.content.mapper.BrowserEnvironmentMapper;
import com.huanjing.geo.module.content.mapper.BrowserEnvironmentAccountMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaAuthWorkOrderEventMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaAuthWorkOrderMapper;
import com.huanjing.geo.module.customer.access.BrandAccessService;
import com.huanjing.geo.module.system.service.CurrentUserService;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.SysUserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SelfMediaAuthWorkOrderServiceTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                SelfMediaAuthWorkOrder.class);
    }

    @Test
    void newRiskReopensResolvedOrderAndReleasesPreviousOwner() {
        SelfMediaAuthWorkOrderMapper workOrderMapper = mock(SelfMediaAuthWorkOrderMapper.class);
        SelfMediaAuthWorkOrderEventMapper eventMapper = mock(SelfMediaAuthWorkOrderEventMapper.class);
        SelfMediaAuthWorkOrder row = new SelfMediaAuthWorkOrder();
        row.setId(23L);
        row.setBrandId(10L);
        row.setStatus("resolved");
        row.setOwnerUserId(7L);
        row.setResolvedAt(LocalDateTime.now().minusDays(2));
        when(workOrderMapper.selectOne(any())).thenReturn(row);
        SelfMediaAuthWorkOrderService service = new SelfMediaAuthWorkOrderService(
                workOrderMapper,
                eventMapper,
                mock(BrowserEnvironmentMapper.class),
                mock(BrowserEnvironmentAccountMapper.class),
                mock(SelfMediaAccountMapper.class),
                mock(BrandAccessService.class),
                mock(CurrentUserService.class),
                mock(SysUserMapper.class));

        assertTrue(service.reopenResolvedForBrand(10L, "再次发现登录风险"));

        assertEquals("system_diagnosis", row.getStatus());
        assertNull(row.getOwnerUserId());
        assertNull(row.getResolvedAt());
        verify(workOrderMapper).updateById(row);
        verify(workOrderMapper).update(any(), any(LambdaUpdateWrapper.class));
        ArgumentCaptor<SelfMediaAuthWorkOrderEvent> event = ArgumentCaptor.forClass(SelfMediaAuthWorkOrderEvent.class);
        verify(eventMapper).insert(event.capture());
        assertEquals("reopened", event.getValue().getAction());
        assertNull(event.getValue().getOperatorId());
    }

    @Test
    void pageReturnsServerSideRecordsAndStageCountsWithinAccessibleBrands() {
        SelfMediaAuthWorkOrderMapper workOrderMapper = mock(SelfMediaAuthWorkOrderMapper.class);
        SelfMediaAuthWorkOrderEventMapper eventMapper = mock(SelfMediaAuthWorkOrderEventMapper.class);
        BrandAccessService brandAccessService = mock(BrandAccessService.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        SysUser operator = new SysUser();
        operator.setId(7L);
        SelfMediaAuthWorkOrder row = order(null);
        row.setBrandId(10L);
        when(currentUserService.requireCurrentUser()).thenReturn(operator);
        when(currentUserService.hasPermission("self-media.auth-health.read")).thenReturn(true);
        when(brandAccessService.listAccessibleBrandIds(eq(7L), any())).thenReturn(java.util.List.of(10L, 20L));
        when(workOrderMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<SelfMediaAuthWorkOrder> page = invocation.getArgument(0);
            page.setRecords(java.util.List.of(row));
            page.setTotal(21L);
            return page;
        });
        when(workOrderMapper.selectCount(any())).thenReturn(3L, 4L, 5L);
        SelfMediaAuthWorkOrderService service = new SelfMediaAuthWorkOrderService(
                workOrderMapper, eventMapper, mock(BrowserEnvironmentMapper.class),
                mock(BrowserEnvironmentAccountMapper.class), mock(SelfMediaAccountMapper.class),
                brandAccessService, currentUserService, mock(SysUserMapper.class));

        var result = service.page("unclaimed", null, 2, 20);

        assertEquals(1, result.records().size());
        assertEquals(21L, result.total());
        assertEquals(2L, result.current());
        assertEquals(20L, result.size());
        assertEquals(3L, result.unclaimedCount());
        assertEquals(4L, result.processingCount());
        assertEquals(5L, result.resolvedCount());
    }

    @Test
    void claimUsesAtomicUnownedUpdate() {
        SelfMediaAuthWorkOrderMapper workOrderMapper = mock(SelfMediaAuthWorkOrderMapper.class);
        SelfMediaAuthWorkOrderEventMapper eventMapper = mock(SelfMediaAuthWorkOrderEventMapper.class);
        BrandAccessService brandAccessService = mock(BrandAccessService.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        SysUser operator = new SysUser();
        operator.setId(7L);
        SelfMediaAuthWorkOrder row = new SelfMediaAuthWorkOrder();
        row.setId(23L);
        row.setBrandId(10L);
        row.setStatus("system_diagnosis");
        when(currentUserService.requireCurrentUser()).thenReturn(operator);
        when(currentUserService.hasPermission("self-media.auth-health.verify")).thenReturn(true);
        when(workOrderMapper.selectById(23L)).thenReturn(row);
        when(workOrderMapper.claimIfUnowned(eq(23L), eq(7L), any(LocalDateTime.class))).thenReturn(1);
        SelfMediaAuthWorkOrderService service = new SelfMediaAuthWorkOrderService(
                workOrderMapper,
                eventMapper,
                mock(BrowserEnvironmentMapper.class),
                mock(BrowserEnvironmentAccountMapper.class),
                mock(SelfMediaAccountMapper.class),
                brandAccessService,
                currentUserService,
                mock(SysUserMapper.class));

        var claimed = service.claim(23L);
        assertEquals(7L, claimed.ownerUserId());
        assertEquals("operator_verification", claimed.status());

        verify(workOrderMapper).claimIfUnowned(eq(23L), eq(7L), any(LocalDateTime.class));
        verify(eventMapper).insert(any(SelfMediaAuthWorkOrderEvent.class));
    }

    @Test
    void unclaimedOrderCannotBeUpdated() {
        SelfMediaAuthWorkOrderMapper workOrderMapper = mock(SelfMediaAuthWorkOrderMapper.class);
        SelfMediaAuthWorkOrderEventMapper eventMapper = mock(SelfMediaAuthWorkOrderEventMapper.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        SysUser operator = new SysUser();
        operator.setId(7L);
        SelfMediaAuthWorkOrder row = order(null);
        when(currentUserService.requireCurrentUser()).thenReturn(operator);
        when(currentUserService.hasPermission("self-media.auth-health.verify")).thenReturn(true);
        when(workOrderMapper.selectById(23L)).thenReturn(row);
        SelfMediaAuthWorkOrderService service = service(workOrderMapper, eventMapper, currentUserService);

        assertEquals(409, assertThrows(com.huanjing.geo.common.exception.BizException.class,
                () -> service.update(23L, request("operator_verification", null))).getCode());
        verify(workOrderMapper, never()).updateById(any());
        verify(eventMapper, never()).insert(any());
    }

    @Test
    void newWorkOrderRequiresExplicitLoginEvidence() {
        SelfMediaAuthWorkOrderMapper workOrderMapper = mock(SelfMediaAuthWorkOrderMapper.class);
        SelfMediaAuthWorkOrderEventMapper eventMapper = mock(SelfMediaAuthWorkOrderEventMapper.class);
        BrowserEnvironmentAccountMapper environmentAccountMapper = mock(BrowserEnvironmentAccountMapper.class);
        SelfMediaAccountMapper accountMapper = mock(SelfMediaAccountMapper.class);
        CurrentUserService currentUserService = currentUser(7L);
        when(workOrderMapper.selectOne(any())).thenReturn(null);
        when(environmentAccountMapper.selectList(any())).thenReturn(java.util.List.of());
        when(accountMapper.selectList(any())).thenReturn(java.util.List.of());
        SelfMediaAuthWorkOrderService service = new SelfMediaAuthWorkOrderService(
                workOrderMapper, eventMapper, mock(BrowserEnvironmentMapper.class),
                environmentAccountMapper, accountMapper, mock(BrandAccessService.class),
                currentUserService, mock(SysUserMapper.class));

        assertEquals(409, assertThrows(com.huanjing.geo.common.exception.BizException.class,
                () -> service.getOrCreate(10L, null, "仅复验超期")).getCode());
        verify(workOrderMapper, never()).insert(any());
    }

    @Test
    void explicitLoginRequiredBindingAllowsNewWorkOrder() {
        SelfMediaAuthWorkOrderMapper workOrderMapper = mock(SelfMediaAuthWorkOrderMapper.class);
        SelfMediaAuthWorkOrderEventMapper eventMapper = mock(SelfMediaAuthWorkOrderEventMapper.class);
        BrowserEnvironmentAccountMapper environmentAccountMapper = mock(BrowserEnvironmentAccountMapper.class);
        CurrentUserService currentUserService = currentUser(7L);
        BrowserEnvironmentAccount binding = new BrowserEnvironmentAccount();
        binding.setBrandId(10L);
        binding.setSelfMediaAccountId(31L);
        binding.setLoginStatus("login_required");
        SelfMediaAccountMapper accountMapper = mock(SelfMediaAccountMapper.class);
        when(accountMapper.selectList(any())).thenReturn(java.util.List.of(activeAccount(31L)));
        when(workOrderMapper.selectOne(any())).thenReturn(null);
        when(environmentAccountMapper.selectList(any())).thenReturn(java.util.List.of(binding));
        SelfMediaAuthWorkOrderService service = new SelfMediaAuthWorkOrderService(
                workOrderMapper, eventMapper, mock(BrowserEnvironmentMapper.class),
                environmentAccountMapper, accountMapper, mock(BrandAccessService.class),
                currentUserService, mock(SysUserMapper.class));

        assertEquals("system_diagnosis", service.getOrCreate(10L, null, "明确进入登录页").status());
        verify(workOrderMapper).insert(any(SelfMediaAuthWorkOrder.class));
    }

    @Test
    void anotherOperatorsOrderCannotBeUpdated() {
        SelfMediaAuthWorkOrderMapper workOrderMapper = mock(SelfMediaAuthWorkOrderMapper.class);
        SelfMediaAuthWorkOrderEventMapper eventMapper = mock(SelfMediaAuthWorkOrderEventMapper.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        SysUser operator = new SysUser();
        operator.setId(7L);
        when(currentUserService.requireCurrentUser()).thenReturn(operator);
        when(currentUserService.hasPermission("self-media.auth-health.verify")).thenReturn(true);
        when(workOrderMapper.selectById(23L)).thenReturn(order(8L));
        SelfMediaAuthWorkOrderService service = service(workOrderMapper, eventMapper, currentUserService);

        assertEquals(409, assertThrows(com.huanjing.geo.common.exception.BizException.class,
                () -> service.update(23L, request("operator_verification", "核对中"))).getCode());
        verify(workOrderMapper, never()).updateById(any());
    }

    @Test
    void unchangedUpdateWithoutNewNoteDoesNotAppendDuplicateEvent() {
        SelfMediaAuthWorkOrderMapper workOrderMapper = mock(SelfMediaAuthWorkOrderMapper.class);
        SelfMediaAuthWorkOrderEventMapper eventMapper = mock(SelfMediaAuthWorkOrderEventMapper.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        SysUser operator = new SysUser();
        operator.setId(7L);
        SelfMediaAuthWorkOrder row = order(7L);
        when(currentUserService.requireCurrentUser()).thenReturn(operator);
        when(currentUserService.hasPermission("self-media.auth-health.verify")).thenReturn(true);
        when(workOrderMapper.selectById(23L)).thenReturn(row);
        SelfMediaAuthWorkOrderService service = service(workOrderMapper, eventMapper, currentUserService);

        service.update(23L, request("operator_verification", "   "));

        verify(workOrderMapper).updateById(row);
        verify(eventMapper, never()).insert(any());
        assertNull(row.getLatestNote());
    }

    @Test
    void intermediateStatusIsNoLongerAccepted() {
        SelfMediaAuthWorkOrderMapper workOrderMapper = mock(SelfMediaAuthWorkOrderMapper.class);
        SelfMediaAuthWorkOrderEventMapper eventMapper = mock(SelfMediaAuthWorkOrderEventMapper.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        SysUser operator = new SysUser();
        operator.setId(7L);
        when(currentUserService.requireCurrentUser()).thenReturn(operator);
        when(currentUserService.hasPermission("self-media.auth-health.verify")).thenReturn(true);
        when(workOrderMapper.selectById(23L)).thenReturn(order(7L));
        SelfMediaAuthWorkOrderService service = service(workOrderMapper, eventMapper, currentUserService);

        assertEquals(400, assertThrows(com.huanjing.geo.common.exception.BizException.class,
                () -> service.update(23L, new SelfMediaAuthWorkOrderUpdateRequest(
                        "technical_hold", "normal", "扩展暂时无法连接", null))).getCode());
        verify(workOrderMapper, never()).updateById(any());
    }

    @Test
    void historicalOpenStatusCanContinueAsOperatorProcessing() {
        SelfMediaAuthWorkOrderMapper workOrderMapper = mock(SelfMediaAuthWorkOrderMapper.class);
        SelfMediaAuthWorkOrderEventMapper eventMapper = mock(SelfMediaAuthWorkOrderEventMapper.class);
        CurrentUserService currentUserService = currentUser(7L);
        SelfMediaAuthWorkOrder row = order(7L);
        row.setStatus("waiting_customer");
        row.setCustomerActionRequired(true);
        when(workOrderMapper.selectById(23L)).thenReturn(row);
        SelfMediaAuthWorkOrderService service = service(workOrderMapper, eventMapper, currentUserService);

        service.update(23L, new SelfMediaAuthWorkOrderUpdateRequest(
                "operator_verification", "normal", "运营继续处理", LocalDateTime.now().plusHours(2)));

        assertEquals("operator_verification", row.getStatus());
        assertEquals(false, row.getCustomerActionRequired());
        verify(workOrderMapper).updateById(row);
    }

    @Test
    void resolvedStatusRequiresResultNote() {
        SelfMediaAuthWorkOrderMapper workOrderMapper = mock(SelfMediaAuthWorkOrderMapper.class);
        SelfMediaAuthWorkOrderEventMapper eventMapper = mock(SelfMediaAuthWorkOrderEventMapper.class);
        CurrentUserService currentUserService = currentUser(7L);
        when(workOrderMapper.selectById(23L)).thenReturn(order(7L));
        SelfMediaAuthWorkOrderService service = service(workOrderMapper, eventMapper, currentUserService);

        assertEquals(400, assertThrows(com.huanjing.geo.common.exception.BizException.class,
                () -> service.update(23L, new SelfMediaAuthWorkOrderUpdateRequest(
                        "resolved", "normal", " ", null))).getCode());
        verify(workOrderMapper, never()).updateById(any());
    }

    @Test
    void normalResolutionIsRejectedWhileExplicitLoginFailureRemains() {
        SelfMediaAuthWorkOrderMapper workOrderMapper = mock(SelfMediaAuthWorkOrderMapper.class);
        SelfMediaAuthWorkOrderEventMapper eventMapper = mock(SelfMediaAuthWorkOrderEventMapper.class);
        BrowserEnvironmentAccountMapper environmentAccountMapper = mock(BrowserEnvironmentAccountMapper.class);
        CurrentUserService currentUserService = currentUser(7L);
        BrowserEnvironmentAccount binding = new BrowserEnvironmentAccount();
        binding.setBrandId(10L);
        binding.setSelfMediaAccountId(31L);
        binding.setLoginStatus("login_required");
        com.huanjing.geo.module.content.entity.SelfMediaAccount account = activeAccount(31L);
        when(workOrderMapper.selectById(23L)).thenReturn(order(7L));
        SelfMediaAccountMapper accountMapper = mock(SelfMediaAccountMapper.class);
        when(environmentAccountMapper.selectList(any())).thenReturn(java.util.List.of(binding));
        when(accountMapper.selectList(any())).thenReturn(java.util.List.of(account));
        SelfMediaAuthWorkOrderService service = new SelfMediaAuthWorkOrderService(
                workOrderMapper, eventMapper, mock(BrowserEnvironmentMapper.class),
                environmentAccountMapper, accountMapper, mock(BrandAccessService.class),
                currentUserService, mock(SysUserMapper.class));

        assertEquals(409, assertThrows(com.huanjing.geo.common.exception.BizException.class,
                () -> service.update(23L, new SelfMediaAuthWorkOrderUpdateRequest(
                        "resolved", "normal", "已完成登录", null, "login_restored"))).getCode());
        verify(workOrderMapper, never()).updateById(any());
    }

    @Test
    void specialResolutionAllowsExplicitLoginFailureWithClearReason() {
        SelfMediaAuthWorkOrderMapper workOrderMapper = mock(SelfMediaAuthWorkOrderMapper.class);
        SelfMediaAuthWorkOrderEventMapper eventMapper = mock(SelfMediaAuthWorkOrderEventMapper.class);
        BrowserEnvironmentAccountMapper environmentAccountMapper = mock(BrowserEnvironmentAccountMapper.class);
        CurrentUserService currentUserService = currentUser(7L);
        BrowserEnvironmentAccount binding = new BrowserEnvironmentAccount();
        binding.setBrandId(10L);
        binding.setSelfMediaAccountId(31L);
        binding.setLoginStatus("login_required");
        com.huanjing.geo.module.content.entity.SelfMediaAccount account = activeAccount(31L);
        SelfMediaAuthWorkOrder row = order(7L);
        when(workOrderMapper.selectById(23L)).thenReturn(row);
        SelfMediaAccountMapper accountMapper = mock(SelfMediaAccountMapper.class);
        when(environmentAccountMapper.selectList(any())).thenAnswer(invocation ->
                "active".equals(account.getStatus()) ? java.util.List.of(binding) : java.util.List.of());
        when(accountMapper.selectList(any())).thenAnswer(invocation ->
                "active".equals(account.getStatus()) ? java.util.List.of(account) : java.util.List.of());
        SelfMediaAuthWorkOrderService service = new SelfMediaAuthWorkOrderService(
                workOrderMapper, eventMapper, mock(BrowserEnvironmentMapper.class),
                environmentAccountMapper, accountMapper, mock(BrandAccessService.class),
                currentUserService, mock(SysUserMapper.class));

        service.update(23L, new SelfMediaAuthWorkOrderUpdateRequest(
                "resolved", "normal", "特殊结束：账号已停用", null, "special",
                "账号已停用", java.util.List.of(31L)));

        assertEquals("resolved", row.getStatus());
        assertEquals("特殊结束：账号已停用", row.getLatestNote());
        assertEquals("disabled", account.getStatus());
        assertEquals("special", row.getResolutionMode());
        assertEquals("账号已停用", row.getResolutionReason());
        verify(accountMapper).updateById(account);
        verify(workOrderMapper).updateById(row);
    }

    @Test
    void specialResolutionRejectsFreeFormReason() {
        SelfMediaAuthWorkOrderMapper workOrderMapper = mock(SelfMediaAuthWorkOrderMapper.class);
        SelfMediaAuthWorkOrderEventMapper eventMapper = mock(SelfMediaAuthWorkOrderEventMapper.class);
        CurrentUserService currentUserService = currentUser(7L);
        when(workOrderMapper.selectById(23L)).thenReturn(order(7L));
        SelfMediaAuthWorkOrderService service = service(workOrderMapper, eventMapper, currentUserService);

        assertEquals(409, assertThrows(com.huanjing.geo.common.exception.BizException.class,
                () -> service.update(23L, new SelfMediaAuthWorkOrderUpdateRequest(
                        "resolved", "normal", "特殊结束：系统误报", null, "special",
                        "系统误报", java.util.List.of(31L)))).getCode());
        verify(workOrderMapper, never()).updateById(any());
    }

    @Test
    void workOrderNoteRejectsCredentialsAndVerificationCodes() {
        SelfMediaAuthWorkOrderMapper workOrderMapper = mock(SelfMediaAuthWorkOrderMapper.class);
        SelfMediaAuthWorkOrderEventMapper eventMapper = mock(SelfMediaAuthWorkOrderEventMapper.class);
        CurrentUserService currentUserService = currentUser(7L);
        when(workOrderMapper.selectById(23L)).thenReturn(order(7L));
        SelfMediaAuthWorkOrderService service = service(workOrderMapper, eventMapper, currentUserService);

        assertEquals(400, assertThrows(com.huanjing.geo.common.exception.BizException.class,
                () -> service.update(23L, new SelfMediaAuthWorkOrderUpdateRequest(
                        "operator_verification", "normal", "客户验证码：123456", null))).getCode());
        verify(workOrderMapper, never()).updateById(any());
    }

    @Test
    void invalidStatusJumpIsRejected() {
        SelfMediaAuthWorkOrderMapper workOrderMapper = mock(SelfMediaAuthWorkOrderMapper.class);
        SelfMediaAuthWorkOrderEventMapper eventMapper = mock(SelfMediaAuthWorkOrderEventMapper.class);
        CurrentUserService currentUserService = currentUser(7L);
        SelfMediaAuthWorkOrder row = order(7L);
        row.setStatus("system_diagnosis");
        when(workOrderMapper.selectById(23L)).thenReturn(row);
        SelfMediaAuthWorkOrderService service = service(workOrderMapper, eventMapper, currentUserService);

        assertEquals(400, assertThrows(com.huanjing.geo.common.exception.BizException.class,
                () -> service.update(23L, new SelfMediaAuthWorkOrderUpdateRequest(
                        "waiting_customer", "normal", "需要客户远程扫码",
                        LocalDateTime.now().plusHours(1)))).getCode());
    }

    private SelfMediaAuthWorkOrderService service(SelfMediaAuthWorkOrderMapper workOrderMapper,
                                                   SelfMediaAuthWorkOrderEventMapper eventMapper,
                                                   CurrentUserService currentUserService) {
        return new SelfMediaAuthWorkOrderService(workOrderMapper, eventMapper,
                mock(BrowserEnvironmentMapper.class), mock(BrowserEnvironmentAccountMapper.class),
                mock(SelfMediaAccountMapper.class), mock(BrandAccessService.class), currentUserService,
                mock(SysUserMapper.class));
    }

    private CurrentUserService currentUser(Long id) {
        CurrentUserService service = mock(CurrentUserService.class);
        SysUser operator = new SysUser();
        operator.setId(id);
        when(service.requireCurrentUser()).thenReturn(operator);
        when(service.hasPermission("self-media.auth-health.verify")).thenReturn(true);
        return service;
    }

    private SelfMediaAuthWorkOrder order(Long ownerUserId) {
        SelfMediaAuthWorkOrder row = new SelfMediaAuthWorkOrder();
        row.setId(23L);
        row.setBrandId(10L);
        row.setOwnerUserId(ownerUserId);
        row.setStatus("operator_verification");
        row.setPriority("normal");
        row.setCustomerActionRequired(false);
        return row;
    }

    private com.huanjing.geo.module.content.entity.SelfMediaAccount activeAccount(Long id) {
        com.huanjing.geo.module.content.entity.SelfMediaAccount account =
                new com.huanjing.geo.module.content.entity.SelfMediaAccount();
        account.setId(id);
        account.setBrandId(10L);
        account.setStatus("active");
        account.setPlatform("douyin");
        return account;
    }

    private SelfMediaAuthWorkOrderUpdateRequest request(String status, String note) {
        return new SelfMediaAuthWorkOrderUpdateRequest(status, "normal", note, null);
    }
}
