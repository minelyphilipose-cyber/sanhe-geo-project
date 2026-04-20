package com.huanjing.geo.module.system.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.storage.MinioStorageService;
import com.huanjing.geo.module.system.dto.CurrentUserPasswordChangeRequest;
import com.huanjing.geo.module.system.dto.CurrentUserProfileUpdateRequest;
import com.huanjing.geo.module.system.dto.CurrentUserProfileVO;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CurrentUserProfileService {

    private static final String REFRESH_KEY_PREFIX = "refresh:";
    private static final long MAX_UPLOAD_FILE_SIZE = 10L * 1024 * 1024;

    private final CurrentUserService currentUserService;
    private final PermissionService permissionService;
    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MinioStorageService minioStorageService;
    private final ActivityLogService activityLogService;

    public CurrentUserProfileVO me() {
        return toProfile(currentUserService.requireCurrentUser());
    }

    @Transactional
    public CurrentUserProfileVO updateProfile(CurrentUserProfileUpdateRequest req) {
        SysUser user = currentUserService.requireCurrentUser();
        CurrentUserProfileVO before = toProfile(user);

        user.setDisplayName(req.getDisplayName().trim());
        user.setPhone(normalizeText(req.getPhone(), 20));
        user.setEmail(normalizeText(req.getEmail(), 128));
        sysUserMapper.updateById(user);

        CurrentUserProfileVO after = toProfile(sysUserMapper.selectById(user.getId()));
        activityLogService.logAction(
                user.getId(),
                "user.profile.update",
                "sys_user",
                user.getId(),
                before,
                after,
                null
        );
        return after;
    }

    @Transactional
    public CurrentUserProfileVO uploadAvatar(MultipartFile file) {
        SysUser user = currentUserService.requireCurrentUser();
        validateAvatarFile(file);

        String originalName = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "avatar.png";
        String objectKey = buildAvatarObjectKey(user.getId(), originalName);
        String avatarUrl = minioStorageService.upload(file, objectKey, file.getContentType());
        String oldObjectKey = user.getAvatarObjectKey();

        user.setAvatarUrl(avatarUrl);
        user.setAvatarObjectKey(objectKey);
        sysUserMapper.updateById(user);

        if (StringUtils.hasText(oldObjectKey) && !oldObjectKey.equals(objectKey)) {
            minioStorageService.remove(oldObjectKey);
        }

        CurrentUserProfileVO after = toProfile(sysUserMapper.selectById(user.getId()));
        activityLogService.logAction(
                user.getId(),
                "user.avatar.update",
                "sys_user",
                user.getId(),
                null,
                after.getAvatarUrl(),
                null
        );
        return after;
    }

    @Transactional
    public void changePassword(CurrentUserPasswordChangeRequest req) {
        SysUser user = currentUserService.requireCurrentUser();
        if (!passwordEncoder.matches(req.getOldPassword(), user.getPasswordHash())) {
            throw new BizException(400, "Old password is incorrect");
        }
        if (req.getOldPassword().equals(req.getNewPassword())) {
            throw new BizException(400, "New password must be different from old password");
        }

        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        user.setTokenVersion(nextTokenVersion(user));
        sysUserMapper.updateById(user);
        redisTemplate.delete(REFRESH_KEY_PREFIX + user.getId());

        activityLogService.logAction(
                user.getId(),
                "user.password.change",
                "sys_user",
                user.getId(),
                null,
                null,
                null
        );
    }

    private CurrentUserProfileVO toProfile(SysUser user) {
        CurrentUserProfileVO vo = new CurrentUserProfileVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setDisplayName(user.getDisplayName());
        vo.setRole(user.getRole());
        vo.setPartnerId(user.getPartnerId());
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        vo.setAvatarUrl(minioStorageService.resolveAccessibleUrl(
                user.getAvatarObjectKey(),
                user.getAvatarUrl(),
                86400
        ));
        vo.setIsActive(user.getIsActive());
        vo.setPermissions(permissionService.listPermKeys(user));
        return vo;
    }

    private void validateAvatarFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(400, "Upload file is empty");
        }
        if (file.getSize() > MAX_UPLOAD_FILE_SIZE) {
            throw new BizException(400, "Upload file exceeds 10MB limit");
        }
        String contentType = file.getContentType();
        if (!StringUtils.hasText(contentType) || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new BizException(400, "Avatar must be an image file");
        }
    }

    private String buildAvatarObjectKey(Long userId, String originalName) {
        String date = LocalDate.now().toString().replace("-", "");
        String random = UUID.randomUUID().toString().replace("-", "");
        String ext = "";
        int dot = originalName.lastIndexOf('.');
        if (dot > -1 && dot < originalName.length() - 1) {
            ext = originalName.substring(dot);
        }
        return "user/avatar/" + userId + "/" + date + "/" + random + ext;
    }

    private String normalizeText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength);
    }

    private int nextTokenVersion(SysUser user) {
        int current = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        return current + 1;
    }
}
