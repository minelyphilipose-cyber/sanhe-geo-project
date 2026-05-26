package com.huanjing.geo.module.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.system.dto.SystemAlertTodoVO;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.entity.SystemAlert;
import com.huanjing.geo.module.system.mapper.SystemAlertMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemAlertServiceTest {

    private SystemAlertMapper systemAlertMapper;
    private CurrentUserService currentUserService;
    private SystemAlertService systemAlertService;

    @BeforeEach
    void setUp() {
        systemAlertMapper = mock(SystemAlertMapper.class);
        currentUserService = mock(CurrentUserService.class);
        systemAlertService = new SystemAlertService(systemAlertMapper, currentUserService);
        when(currentUserService.requireCurrentUser()).thenReturn(user());
    }

    @Test
    void myTodosMapsVisibleUnresolvedAlerts() {
        SystemAlert alert = new SystemAlert();
        alert.setId(9L);
        alert.setAlertType("publish_credential_expired");
        alert.setSeverity("warn");
        alert.setSource("content_auto_distribution");
        alert.setMessage("xx论坛登录信息已过期，请更新");
        alert.setContextJson("{\"route\":\"/admin/content/publish-platforms?siteId=7\"}");
        alert.setCreatedAt(LocalDateTime.of(2026, 5, 26, 1, 0));
        Page<SystemAlert> page = new Page<>(1, 20, 1);
        page.setRecords(List.of(alert));
        when(systemAlertMapper.selectPage(any(Page.class), any())).thenReturn(page);

        Page<SystemAlertTodoVO> todos = systemAlertService.myTodos(1, 20);

        assertEquals(1, todos.getTotal());
        assertEquals(1, todos.getRecords().size());
        SystemAlertTodoVO todo = todos.getRecords().get(0);
        assertEquals(9L, todo.getId());
        assertEquals("xx论坛登录信息已过期，请更新", todo.getMessage());
        assertEquals("{\"route\":\"/admin/content/publish-platforms?siteId=7\"}", todo.getContextJson());
    }

    @Test
    void resolveMarksVisibleAlertResolvedByCurrentUser() {
        SystemAlert alert = new SystemAlert();
        alert.setId(9L);
        alert.setIsResolved(false);
        when(systemAlertMapper.selectOne(any())).thenReturn(alert);

        systemAlertService.resolve(9L);

        ArgumentCaptor<SystemAlert> captor = ArgumentCaptor.forClass(SystemAlert.class);
        verify(systemAlertMapper).updateById(captor.capture());
        SystemAlert updated = captor.getValue();
        assertEquals(9L, updated.getId());
        assertEquals(true, updated.getIsResolved());
        assertEquals(7L, updated.getResolvedBy());
        assertNotNull(updated.getResolvedAt());
    }

    @Test
    void resolveRejectsMissingOrResolvedAlert() {
        when(systemAlertMapper.selectOne(any())).thenReturn(null);

        assertThrows(BizException.class, () -> systemAlertService.resolve(9L));
    }

    private SysUser user() {
        SysUser user = new SysUser();
        user.setId(7L);
        user.setRole("super_admin");
        return user;
    }
}
