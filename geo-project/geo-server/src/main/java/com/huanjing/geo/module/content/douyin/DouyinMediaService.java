package com.huanjing.geo.module.content.douyin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.storage.MinioStorageService;
import com.huanjing.geo.module.content.douyin.client.DouyinClient;
import com.huanjing.geo.module.content.douyin.client.dto.DouyinImageUploadRequest;
import com.huanjing.geo.module.content.douyin.client.dto.DouyinImageUploadResponse;
import com.huanjing.geo.module.content.douyin.client.exception.DouyinAuthException;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.entity.SelfMediaMaterialMapping;
import com.huanjing.geo.module.content.mapper.SelfMediaMaterialMappingMapper;
import com.huanjing.geo.module.customer.entity.BrandMaterial;
import com.huanjing.geo.module.customer.mapper.BrandMaterialMapper;
import com.huanjing.geo.module.customer.service.BrandImageFolderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DouyinMediaService {
    private static final String PLATFORM = "douyin";
    private static final String MEDIA_TYPE_IMAGE = "douyin_image";
    private static final String IMAGE_LOCK_PREFIX = "douyin:material_lock:image:";
    private static final Duration IMAGE_LOCK_TTL = Duration.ofSeconds(60);
    private static final long MAX_IMAGE_BYTES = 20L * 1024 * 1024;
    private static final Set<String> SUPPORTED_IMAGE_TYPES = Set.of("jpg", "jpeg", "png");

    private final BrandMaterialMapper brandMaterialMapper;
    private final BrandImageFolderService brandImageFolderService;
    private final SelfMediaMaterialMappingMapper mappingMapper;
    private final MinioStorageService minioStorageService;
    private final DouyinTokenService douyinTokenService;
    private final DouyinClient douyinClient;
    private final StringRedisTemplate redisTemplate;

    public String ensureUploadedImageId(SelfMediaAccount account, Long brandId, Long brandMaterialId) {
        requireDouyinAccount(account);
        BrandMaterial material = requireImageMaterial(brandId, brandMaterialId);
        byte[] bytes = minioStorageService.getObjectBytes(material.getObjectKey());
        validateImageSize(bytes);
        String hash = sha256(bytes);
        SelfMediaMaterialMapping existed = findMapping(account.getId(), material.getId(), hash);
        if (existed != null && StringUtils.hasText(existed.getPlatformMediaId())) {
            return existed.getPlatformMediaId();
        }
        return uploadWithLock(account, material, bytes, hash);
    }

    private String uploadWithLock(SelfMediaAccount account, BrandMaterial material, byte[] bytes, String hash) {
        String lockKey = IMAGE_LOCK_PREFIX + account.getId() + ":" + hash;
        String lockValue = UUID.randomUUID().toString();
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, IMAGE_LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            sleepBriefly();
            SelfMediaMaterialMapping existed = findMapping(account.getId(), material.getId(), hash);
            if (existed != null && StringUtils.hasText(existed.getPlatformMediaId())) {
                return existed.getPlatformMediaId();
            }
            throw new BizException(429, "douyin image uploading");
        }
        try {
            SelfMediaMaterialMapping existed = findMapping(account.getId(), material.getId(), hash);
            if (existed != null && StringUtils.hasText(existed.getPlatformMediaId())) {
                return existed.getPlatformMediaId();
            }
            DouyinImageUploadResponse response = uploadWithTokenRetry(
                    account,
                    bytes,
                    material.getFileName(),
                    contentType(material.getFileType())
            );
            String imageId = require(response.getImageId(), "douyin image_id missing");
            SelfMediaMaterialMapping row = new SelfMediaMaterialMapping();
            row.setSelfMediaAccountId(account.getId());
            row.setBrandMaterialId(material.getId());
            row.setContentHash(hash);
            row.setMediaType(MEDIA_TYPE_IMAGE);
            row.setPlatformMediaId(imageId);
            row.setExtraJson(extraJson(response));
            mappingMapper.insert(row);
            return imageId;
        } finally {
            String current = redisTemplate.opsForValue().get(lockKey);
            if (lockValue.equals(current)) {
                redisTemplate.delete(lockKey);
            }
        }
    }

    private DouyinImageUploadResponse uploadWithTokenRetry(SelfMediaAccount account,
                                                           byte[] bytes,
                                                           String filename,
                                                           String contentType) {
        String accessToken = douyinTokenService.getAccessToken(account);
        DouyinImageUploadRequest request = uploadRequest(account, accessToken, bytes, filename, contentType);
        try {
            return douyinClient.uploadImage(request);
        } catch (DouyinAuthException ex) {
            if (!isAccessTokenInvalid(ex)) {
                throw ex;
            }
            douyinTokenService.evictAccessToken(account);
            String freshToken = douyinTokenService.getAccessToken(account);
            return douyinClient.uploadImage(uploadRequest(account, freshToken, bytes, filename, contentType));
        }
    }

    private DouyinImageUploadRequest uploadRequest(SelfMediaAccount account,
                                                   String accessToken,
                                                   byte[] bytes,
                                                   String filename,
                                                   String contentType) {
        return DouyinImageUploadRequest.builder()
                .accessToken(accessToken)
                .openId(account.getPlatformAccountId())
                .imageBytes(bytes)
                .filename(StringUtils.hasText(filename) ? filename : "image.png")
                .contentType(contentType)
                .build();
    }

    private boolean isAccessTokenInvalid(DouyinAuthException ex) {
        Long code = ex.getErrorCode();
        return Long.valueOf(28001003L).equals(code) || Long.valueOf(28001008L).equals(code);
    }

    private BrandMaterial requireImageMaterial(Long brandId, Long materialId) {
        BrandMaterial material = brandMaterialMapper.selectById(materialId);
        if (material == null || !brandId.equals(material.getBrandId())) {
            throw new BizException(404, "brand material not found");
        }
        brandImageFolderService.requireActiveFolderForSelection(brandId, material.getFolderId());
        String type = normalizeType(material.getFileType());
        if (!SUPPORTED_IMAGE_TYPES.contains(type)) {
            throw new BizException(400, "douyin_image_type_invalid");
        }
        if (!StringUtils.hasText(material.getObjectKey())) {
            throw new BizException(400, "brand material missing object key");
        }
        return material;
    }

    private void validateImageSize(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new BizException(400, "image_empty");
        }
        if (bytes.length > MAX_IMAGE_BYTES) {
            throw new BizException(400, "douyin_image_too_large");
        }
    }

    private SelfMediaMaterialMapping findMapping(Long selfMediaAccountId, Long brandMaterialId, String hash) {
        return mappingMapper.selectOne(new LambdaQueryWrapper<SelfMediaMaterialMapping>()
                .eq(SelfMediaMaterialMapping::getSelfMediaAccountId, selfMediaAccountId)
                .eq(SelfMediaMaterialMapping::getBrandMaterialId, brandMaterialId)
                .eq(SelfMediaMaterialMapping::getContentHash, hash)
                .eq(SelfMediaMaterialMapping::getMediaType, MEDIA_TYPE_IMAGE)
                .last("LIMIT 1"));
    }

    private void requireDouyinAccount(SelfMediaAccount account) {
        if (account == null) {
            throw new BizException(404, "self media account not found");
        }
        if (!PLATFORM.equals(account.getPlatform())) {
            throw new BizException(400, "not douyin account");
        }
        require(account.getPlatformAccountId(), "douyin open_id missing");
    }

    private String require(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(500, message);
        }
        return value;
    }

    private String normalizeType(String fileType) {
        return fileType == null ? "" : fileType.trim().toLowerCase(Locale.ROOT);
    }

    private String contentType(String fileType) {
        String type = normalizeType(fileType);
        if ("jpg".equals(type)) {
            return "image/jpeg";
        }
        if ("jpeg".equals(type) || "png".equals(type)) {
            return "image/" + type;
        }
        return "application/octet-stream";
    }

    private String extraJson(DouyinImageUploadResponse response) {
        return "{\"width\":" + jsonNumber(response.getWidth())
                + ",\"height\":" + jsonNumber(response.getHeight()) + "}";
    }

    private String jsonNumber(Number value) {
        return value == null ? "null" : value.toString();
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private void sleepBriefly() {
        try {
            Thread.sleep(200);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
