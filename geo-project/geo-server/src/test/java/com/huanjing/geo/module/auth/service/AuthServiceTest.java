package com.huanjing.geo.module.auth.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.security.JwtTokenProvider;
import com.huanjing.geo.common.storage.MinioStorageService;
import com.huanjing.geo.module.auth.dto.LoginRequest;
import com.huanjing.geo.module.auth.dto.LoginResponse;
import com.huanjing.geo.module.partner.mapper.PartnerMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.SysUserMapper;
import com.huanjing.geo.module.system.service.PermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceTest {
    private SysUserMapper userMapper;
    private PasswordEncoder passwordEncoder;
    private RedisTemplate<String, Object> redisTemplate;
    private ValueOperations<String, Object> valueOperations;
    private AuthService authService;
    private final Map<String, Object> redis = new java.util.HashMap<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        userMapper = mock(SysUserMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);
        redisTemplate = mock(RedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        PermissionService permissionService = mock(PermissionService.class);
        MinioStorageService minioStorageService = mock(MinioStorageService.class);
        PartnerMapper partnerMapper = mock(PartnerMapper.class);
        JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(
                "0123456789abcdef0123456789abcdef",
                7200,
                604800
        );

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenAnswer(invocation -> redis.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            redis.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), any(), anyLong(), any(TimeUnit.class));
        when(permissionService.listPermKeys(any())).thenReturn(Set.of("project.read"));
        when(minioStorageService.resolveAccessibleUrl(any(), any(), anyInt())).thenReturn(null);

        authService = new AuthService(
                userMapper,
                passwordEncoder,
                jwtTokenProvider,
                redisTemplate,
                permissionService,
                minioStorageService,
                partnerMapper
        );
    }

    @Test
    void secondLoginInvalidatesPreviousRefreshTokenByRedisSession() {
        when(passwordEncoder.matches(eq("password"), anyString())).thenReturn(true);
        when(userMapper.selectOne(any())).thenReturn(user(0), user(1));
        when(userMapper.incrementTokenVersionForLogin(1L)).thenReturn(1);
        when(userMapper.selectById(1L)).thenReturn(user(1), user(2), user(2), user(2));

        LoginResponse first = authService.login(loginRequest());
        LoginResponse second = authService.login(loginRequest());

        BizException oldRefreshRejected = assertThrows(
                BizException.class,
                () -> authService.refresh(first.getRefreshToken())
        );

        assertEquals(401, oldRefreshRejected.getCode());
        assertEquals("Refresh token invalidated", oldRefreshRejected.getMessage());
        assertDoesNotThrow(() -> authService.refresh(second.getRefreshToken()));
        assertEquals("2", redis.get("login:session:1"));
    }

    private LoginRequest loginRequest() {
        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("password");
        return request;
    }

    private SysUser user(int tokenVersion) {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("alice");
        user.setPasswordHash("hash");
        user.setDisplayName("Alice");
        user.setRole("operator");
        user.setIsActive(true);
        user.setTokenVersion(tokenVersion);
        return user;
    }
}
