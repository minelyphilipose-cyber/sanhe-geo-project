package com.huanjing.geo.module.content.wechat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.storage.MinioStorageService;
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

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WechatMediaService {
    private static final String TYPE_THUMB = "thumb";
    private static final String TYPE_IMAGE = "image";
    private static final String CONTENT_IMAGE_LOCK_PREFIX = "wechat:material_lock:content_image:";
    private static final Duration CONTENT_IMAGE_LOCK_TTL = Duration.ofSeconds(60);
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final Set<String> IMAGE_TYPES = Set.of("jpg", "jpeg", "png", "gif", "bmp");

    private final BrandMaterialMapper brandMaterialMapper;
    private final BrandImageFolderService brandImageFolderService;
    private final SelfMediaMaterialMappingMapper mappingMapper;
    private final MinioStorageService minioStorageService;
    private final WechatTokenAwareExecutor tokenAwareExecutor;
    private final WechatMpClient wechatMpClient;
    private final StringRedisTemplate redisTemplate;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public String ensureThumbMediaId(SelfMediaAccount account, Long brandId, Long brandMaterialId) {
        BrandMaterial material = requireImageMaterial(brandId, brandMaterialId);
        byte[] bytes = readMaterialBytes(material);
        validateImageSize(bytes, "cover_too_large");
        String hash = sha256(bytes);
        SelfMediaMaterialMapping existed = findMapping(account.getId(), brandMaterialId, hash, TYPE_THUMB);
        if (existed != null && StringUtils.hasText(existed.getPlatformMediaId())) {
            return existed.getPlatformMediaId();
        }

        WechatMpClient.MaterialResult result =
                tokenAwareExecutor.execute(account, accessToken ->
                        wechatMpClient.addThumbMaterial(accessToken, bytes, material.getFileName()));
        SelfMediaMaterialMapping row = new SelfMediaMaterialMapping();
        row.setSelfMediaAccountId(account.getId());
        row.setBrandMaterialId(material.getId());
        row.setContentHash(hash);
        row.setMediaType(TYPE_THUMB);
        row.setPlatformMediaId(result.mediaId());
        mappingMapper.insert(row);
        return result.mediaId();
    }

    public String ensureContentImageUrl(SelfMediaAccount account, String sourceUrl) {
        if (!StringUtils.hasText(sourceUrl)) {
            return null;
        }
        String normalized = sourceUrl.trim();
        if (normalized.startsWith("https://mmbiz.qpic.cn/") || normalized.startsWith("http://mmbiz.qpic.cn/")) {
            return normalized;
        }
        byte[] bytes = downloadImage(normalized);
        validateImageSize(bytes, "content_image_too_large");
        String hash = sha256(bytes);
        SelfMediaMaterialMapping existed = findMappingByHash(account.getId(), hash, TYPE_IMAGE);
        if (existed != null && StringUtils.hasText(existed.getPlatformUrl())) {
            return existed.getPlatformUrl();
        }

        return uploadContentImageWithLock(account, normalized, bytes, hash);
    }

    private BrandMaterial requireImageMaterial(Long brandId, Long materialId) {
        BrandMaterial material = brandMaterialMapper.selectById(materialId);
        if (material == null || !brandId.equals(material.getBrandId())) {
            throw new BizException(404, "cover material not found");
        }
        brandImageFolderService.requireActiveFolderForSelection(brandId, material.getFolderId());
        String type = material.getFileType() == null ? "" : material.getFileType().trim().toLowerCase(Locale.ROOT);
        if (!IMAGE_TYPES.contains(type)) {
            throw new BizException(400, "cover_type_invalid");
        }
        return material;
    }

    private byte[] readMaterialBytes(BrandMaterial material) {
        if (StringUtils.hasText(material.getObjectKey())) {
            return minioStorageService.getObjectBytes(material.getObjectKey());
        }
        if (StringUtils.hasText(material.getFileUrl())) {
            return downloadImage(material.getFileUrl());
        }
        throw new BizException(400, "cover_file_missing");
    }

    private byte[] downloadImage(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new BizException(400, "content_image_url_invalid");
            }
            assertPublicHost(uri);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BizException(400, "content_image_download_failed");
            }
            return response.body();
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(400, "content_image_download_failed");
        }
    }

    private void assertPublicHost(URI uri) throws Exception {
        String host = uri.getHost();
        if (!StringUtils.hasText(host)) {
            throw new BizException(400, "content_image_url_invalid");
        }
        InetAddress[] addresses = InetAddress.getAllByName(host);
        for (InetAddress address : addresses) {
            if (isBlockedAddress(address)) {
                throw new BizException(400, "content_image_url_blocked");
            }
        }
    }

    private boolean isBlockedAddress(InetAddress address) {
        String hostAddress = address.getHostAddress();
        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || hostAddress.startsWith("169.254.")
                || "0:0:0:0:0:0:0:1".equals(hostAddress)
                || "::1".equals(hostAddress);
    }

    private String uploadContentImageWithLock(SelfMediaAccount account, String normalizedUrl, byte[] bytes, String hash) {
        String lockKey = CONTENT_IMAGE_LOCK_PREFIX + account.getId() + ":" + hash;
        String lockValue = UUID.randomUUID().toString();
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, CONTENT_IMAGE_LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            sleepBriefly();
            SelfMediaMaterialMapping existed = findMappingByHash(account.getId(), hash, TYPE_IMAGE);
            if (existed != null && StringUtils.hasText(existed.getPlatformUrl())) {
                return existed.getPlatformUrl();
            }
            throw new BizException(429, "wechat content image uploading");
        }
        try {
            SelfMediaMaterialMapping existed = findMappingByHash(account.getId(), hash, TYPE_IMAGE);
            if (existed != null && StringUtils.hasText(existed.getPlatformUrl())) {
                return existed.getPlatformUrl();
            }
            String filename = filenameFromUrl(normalizedUrl);
            WechatMpClient.UploadImageResult result =
                    tokenAwareExecutor.execute(account, accessToken ->
                            wechatMpClient.uploadContentImage(accessToken, bytes, filename));
            SelfMediaMaterialMapping row = new SelfMediaMaterialMapping();
            row.setSelfMediaAccountId(account.getId());
            // MySQL unique indexes treat NULL brand_material_id values as distinct, so content
            // images rely on this Redis single-flight lock plus the double-check above.
            row.setBrandMaterialId(null);
            row.setContentHash(hash);
            row.setMediaType(TYPE_IMAGE);
            row.setPlatformUrl(result.url());
            mappingMapper.insert(row);
            return result.url();
        } finally {
            String current = redisTemplate.opsForValue().get(lockKey);
            if (lockValue.equals(current)) {
                redisTemplate.delete(lockKey);
            }
        }
    }

    private void validateImageSize(byte[] bytes, String code) {
        if (bytes == null || bytes.length == 0) {
            throw new BizException(400, "image_empty");
        }
        if (bytes.length > MAX_IMAGE_BYTES) {
            throw new BizException(400, code);
        }
    }

    private SelfMediaMaterialMapping findMapping(Long selfMediaAccountId, Long brandMaterialId, String hash, String mediaType) {
        return mappingMapper.selectOne(new LambdaQueryWrapper<SelfMediaMaterialMapping>()
                .eq(SelfMediaMaterialMapping::getSelfMediaAccountId, selfMediaAccountId)
                .eq(SelfMediaMaterialMapping::getBrandMaterialId, brandMaterialId)
                .eq(SelfMediaMaterialMapping::getContentHash, hash)
                .eq(SelfMediaMaterialMapping::getMediaType, mediaType)
                .last("LIMIT 1"));
    }

    private SelfMediaMaterialMapping findMappingByHash(Long selfMediaAccountId, String hash, String mediaType) {
        return mappingMapper.selectOne(new LambdaQueryWrapper<SelfMediaMaterialMapping>()
                .eq(SelfMediaMaterialMapping::getSelfMediaAccountId, selfMediaAccountId)
                .eq(SelfMediaMaterialMapping::getContentHash, hash)
                .eq(SelfMediaMaterialMapping::getMediaType, mediaType)
                .last("LIMIT 1"));
    }

    private String filenameFromUrl(String url) {
        try {
            String path = URI.create(url).getPath();
            int slash = path == null ? -1 : path.lastIndexOf('/');
            String name = slash >= 0 ? path.substring(slash + 1) : path;
            return StringUtils.hasText(name) ? name : "image.png";
        } catch (Exception ex) {
            return "image.png";
        }
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception ex) {
            return sha256Fallback(bytes);
        }
    }

    private String sha256Fallback(byte[] bytes) {
        return Integer.toHexString(new String(bytes, StandardCharsets.ISO_8859_1).hashCode());
    }

    private void sleepBriefly() {
        try {
            Thread.sleep(200);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
