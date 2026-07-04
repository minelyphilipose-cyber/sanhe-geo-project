package com.huanjing.geo.module.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.security.JwtTokenProvider;
import com.huanjing.geo.common.storage.MinioStorageService;
import com.huanjing.geo.module.auth.dto.LoginRequest;
import com.huanjing.geo.module.auth.dto.LoginResponse;
import com.huanjing.geo.module.partner.entity.Partner;
import com.huanjing.geo.module.partner.mapper.PartnerMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.SysUserMapper;
import com.huanjing.geo.module.system.service.PermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final String REFRESH_KEY_PREFIX = "refresh:";
    private static final String LOGIN_SESSION_KEY_PREFIX = "login:session:";

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RedisTemplate<String, Object> redisTemplate;
    private final PermissionService permissionService;
    private final MinioStorageService minioStorageService;
    private final PartnerMapper partnerMapper;

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

        if (userMapper.incrementTokenVersionForLogin(user.getId()) != 1) {
            throw new BizException(401, "Invalid username or password");
        }
        user = userMapper.selectById(user.getId());
        if (user == null || Boolean.FALSE.equals(user.getIsActive())) {
            throw new BizException(401, "Invalid username or password");
        }

        Integer tokenVersion = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        String accessToken = jwtTokenProvider.createAccessToken(
                user.getId(), user.getUsername(), user.getRole(), tokenVersion);
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId(), tokenVersion);

        redisTemplate.opsForValue().set(
                refreshKey(user.getId(), tokenVersion),
                refreshToken,
                jwtTokenProvider.getRefreshTokenExpireSeconds(),
                TimeUnit.SECONDS
        );
        redisTemplate.opsForValue().set(
                loginSessionKey(user.getId()),
                String.valueOf(tokenVersion),
                jwtTokenProvider.getRefreshTokenExpireSeconds(),
                TimeUnit.SECONDS
        );

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(LoginResponse.UserVO.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .displayName(user.getDisplayName())
                        .role(user.getRole())
                        .partnerId(user.getPartnerId())
                        .partnerName(resolvePartnerName(user.getPartnerId()))
                        .phone(user.getPhone())
                        .email(user.getEmail())
                        .avatarUrl(resolveAvatarUrl(user))
                        .permissions(permissionService.listPermKeys(user))
                        .build())
                .build();
    }

    private String resolvePartnerName(Long partnerId) {
        if (partnerId == null) {
            return null;
        }
        Partner partner = partnerMapper.selectById(partnerId);
        return partner == null ? null : partner.getPartnerName();
    }

    private String resolveAvatarUrl(SysUser user) {
        try {
            return minioStorageService.resolveAccessibleUrl(
                    user.getAvatarObjectKey(),
                    user.getAvatarUrl(),
                    86400
            );
        } catch (BizException ex) {
            log.warn("Resolve login avatar url failed, fallback to stored url userId={}, objectKey={}, code={}, msg={}",
                    user.getId(), user.getAvatarObjectKey(), ex.getCode(), ex.getMessage());
            return user.getAvatarUrl();
        }
    }

    public String refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BizException(401, "Refresh token is required");
        }
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BizException(401, "Refresh token is invalid");
        }

        Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        SysUser user = userMapper.selectById(userId);
        if (user == null || Boolean.FALSE.equals(user.getIsActive())) {
            throw new BizException(401, "User not found or inactive");
        }

        Integer tokenVersion = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        Integer refreshTokenVersion = jwtTokenProvider.parseToken(refreshToken).get("tokenVersion", Integer.class);
        if (refreshTokenVersion == null) {
            refreshTokenVersion = 0;
        }
        String stored = (String) redisTemplate.opsForValue().get(refreshKey(userId, refreshTokenVersion));
        if (stored == null) {
            stored = (String) redisTemplate.opsForValue().get(REFRESH_KEY_PREFIX + userId);
        }
        if (stored == null || !stored.equals(refreshToken)) {
            throw new BizException(401, "Refresh token expired");
        }
        if (!tokenVersion.equals(refreshTokenVersion)) {
            throw new BizException(401, "Refresh token invalidated");
        }
        Object currentSession = redisTemplate.opsForValue().get(loginSessionKey(userId));
        if (currentSession == null || !String.valueOf(refreshTokenVersion).equals(String.valueOf(currentSession))) {
            throw new BizException(401, "Refresh token invalidated");
        }

        return jwtTokenProvider.createAccessToken(
                user.getId(), user.getUsername(), user.getRole(), tokenVersion);
    }

    public void logout(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user != null) {
            int current = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
            user.setTokenVersion(current + 1);
            userMapper.updateById(user);
            redisTemplate.delete(refreshKey(userId, current));
        }
        redisTemplate.delete(REFRESH_KEY_PREFIX + userId);
        redisTemplate.delete(loginSessionKey(userId));
    }

    private String refreshKey(Long userId, Integer tokenVersion) {
        return REFRESH_KEY_PREFIX + userId + ":" + (tokenVersion == null ? 0 : tokenVersion);
    }

    private String loginSessionKey(Long userId) {
        return LOGIN_SESSION_KEY_PREFIX + userId;
    }
}
