package com.huanjing.geo.module.system.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.system.dto.UserCreateRequest;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.SysRoleMapper;
import com.huanjing.geo.module.system.mapper.SysUserMapper;
import com.huanjing.geo.module.system.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAdminServiceTest {

    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private SysRoleMapper sysRoleMapper;
    @Mock
    private SysUserRoleMapper sysUserRoleMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @InjectMocks
    private UserAdminService userAdminService;

    @Test
    void createRejectsPartnerViewerRole() {
        UserCreateRequest req = new UserCreateRequest();
        req.setUsername("viewer");
        req.setPassword("secret123");
        req.setDisplayName("Partner Viewer");
        req.setRoleKey("partner_viewer");
        req.setPartnerId(100L);

        when(sysUserMapper.selectOne(any())).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> userAdminService.create(req));

        assertEquals(400, ex.getCode());
        assertEquals("role not found or inactive", ex.getMessage());
        verify(sysRoleMapper, never()).selectOne(any());
        verify(sysUserMapper, never()).insert(any());
    }

    @Test
    void bindRoleRejectsPartnerViewerRole() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setRole("operator");
        user.setTokenVersion(0);

        when(sysUserMapper.selectById(1L)).thenReturn(user);

        BizException ex = assertThrows(BizException.class, () -> userAdminService.bindRole(1L, "partner_viewer"));

        assertEquals(400, ex.getCode());
        assertEquals("role not found or inactive", ex.getMessage());
        verify(sysRoleMapper, never()).selectOne(any());
        verify(sysUserMapper, never()).updateById(any());
        verify(sysUserRoleMapper, never()).insert(any());
    }

    @Test
    void updateStatusDeactivatesPartnerStaffWhenPartnerOwnerDisabled() {
        SysUser owner = new SysUser();
        owner.setId(10L);
        owner.setRole("partner");
        owner.setPartnerId(100L);
        owner.setIsActive(true);
        owner.setTokenVersion(1);
        SysUser staff = new SysUser();
        staff.setId(20L);
        staff.setRole("partner_staff");
        staff.setPartnerId(100L);
        staff.setIsActive(true);
        staff.setTokenVersion(3);
        when(sysUserMapper.selectById(10L)).thenReturn(owner);
        when(sysUserMapper.selectList(any())).thenReturn(List.of(staff));

        userAdminService.updateStatus(10L, false);

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper, times(2)).updateById(captor.capture());
        assertEquals(false, captor.getAllValues().get(0).getIsActive());
        assertEquals(2, captor.getAllValues().get(0).getTokenVersion());
        assertEquals(false, captor.getAllValues().get(1).getIsActive());
        assertEquals(4, captor.getAllValues().get(1).getTokenVersion());
        verify(redisTemplate).delete("refresh:10");
        verify(redisTemplate).delete("refresh:20");
    }
}
