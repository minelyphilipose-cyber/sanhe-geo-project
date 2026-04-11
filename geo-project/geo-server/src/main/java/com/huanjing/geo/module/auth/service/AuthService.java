package com.huanjing.geo.module.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.security.JwtTokenProvider;
import com.huanjing.geo.module.auth.dto.LoginRequest;
import com.huanjing.geo.module.auth.dto.LoginResponse;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String REFRESH_KEY_PREFIX = "refresh:";

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RedisTemplate<String, Object> redisTemplate;

    public LoginResponse login(LoginRequest req) {
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, req.getUsername())
        );
        if (user == null || Boolean.FALSE.equals(user.getIsActive())) {
            throw new BizException(401, "Invalid username or password");
        }
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new BizException(401, "Invalid username or password");
        }

        String accessToken = jwtTokenProvider.createAccessToken(
                user.getId(), user.getUsername(), user.getRole());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());

        redisTemplate.opsForValue().set(
                REFRESH_KEY_PREFIX + user.getId(),
                refreshToken,
                jwtTokenProvider.getRefreshTokenExpireSeconds(),
                TimeUnit.SECONDS
        );

        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(LoginResponse.UserVO.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .displayName(user.getDisplayName())
                        .role(user.getRole())
                        .partnerId(user.getPartnerId())
                        .build())
                .build();
    }

    public String refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BizException(401, "Refresh token is required");
        }
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BizException(401, "Refresh token is invalid");
        }

        Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        String stored = (String) redisTemplate.opsForValue().get(REFRESH_KEY_PREFIX + userId);
        if (stored == null || !stored.equals(refreshToken)) {
            throw new BizException(401, "Refresh token expired");
        }

        SysUser user = userMapper.selectById(userId);
        if (user == null || Boolean.FALSE.equals(user.getIsActive())) {
            throw new BizException(401, "User not found or inactive");
        }

        return jwtTokenProvider.createAccessToken(
                user.getId(), user.getUsername(), user.getRole());
    }

    public void logout(Long userId) {
        redisTemplate.delete(REFRESH_KEY_PREFIX + userId);
    }
}
