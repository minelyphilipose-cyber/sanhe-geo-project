package com.huanjing.geo.common.storage;

import com.huanjing.geo.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioStorageService {

    private final ObjectStorageService objectStorageService;
    private final StorageProperties storageProperties;

    @Value("${geo.minio.endpoint}")
    private String endpoint;
    @Value("${geo.minio.public-endpoint:${geo.minio.endpoint}}")
    private String publicEndpoint;
    @Value("${geo.minio.bucket}")
    private String bucket;

    public String upload(MultipartFile file, String objectKey, String contentType) {
        try {
            objectStorageService.putBytes(objectKey, file.getBytes(), contentType);
            return buildFileUrl(objectKey);
        } catch (IOException ex) {
            log.error("Read upload file failed, objectKey={}, err={}", objectKey, ex.getMessage(), ex);
            throw new BizException(500, "Upload file failed");
        }
    }

    public String uploadBytes(byte[] bytes, String objectKey, String contentType) {
        objectStorageService.putBytes(objectKey, bytes, contentType);
        return buildFileUrl(objectKey);
    }

    public void remove(String objectKey) {
        try {
            removeStrict(objectKey);
        } catch (Exception ex) {
            log.warn("Remove minio object failed, objectKey={}, err={}", objectKey, ex.getMessage());
        }
    }

    public void removeStrict(String objectKey) throws Exception {
        objectStorageService.delete(objectKey);
    }

    public void removePrefixStrict(String prefix) throws Exception {
        objectStorageService.deletePrefix(prefix);
    }

    public byte[] getObjectBytes(String objectKey) {
        return objectStorageService.readBytes(objectKey);
    }

    public InputStream openObjectStream(String objectKey) {
        return objectStorageService.openStream(objectKey);
    }

    public String buildFileUrl(String objectKey) {
        if (storageProperties.getProvider() == StorageProperties.Provider.COS) {
            return buildCosFileUrl(objectKey);
        }
        String source = StringUtils.hasText(publicEndpoint) ? publicEndpoint : endpoint;
        String normalized = source.endsWith("/") ? source.substring(0, source.length() - 1) : source;
        return normalized + "/" + bucket + "/" + objectKey;
    }

    public String buildPresignedDownloadUrl(String objectKey, int expireSeconds) {
        return objectStorageService.presignedGetUrl(objectKey, expireSeconds);
    }

    public String resolveAccessibleUrl(String objectKey, String fallbackUrl, int expireSeconds) {
        if (StringUtils.hasText(objectKey)) {
            return buildPresignedDownloadUrl(objectKey, expireSeconds);
        }
        return fallbackUrl;
    }

    private String buildCosFileUrl(String objectKey) {
        StorageProperties.Cos cos = storageProperties.getCos();
        String cosBucket = requireText(cos.getBucket(), "geo.storage.cos.bucket is required when COS provider is used");
        String host = StringUtils.hasText(cos.getEndpoint())
                ? cleanEndpointSuffix(cos.getEndpoint(), cosBucket)
                : "cos." + requireText(cos.getRegion(), "geo.storage.cos.region is required when COS provider is used")
                + ".myqcloud.com";
        return "https://" + cosBucket + "." + host + "/" + objectKey;
    }

    private String cleanEndpointSuffix(String value, String cosBucket) {
        String cleaned = value.trim()
                .replaceFirst("^https?://", "")
                .replaceAll("/+$", "");
        if (cleaned.startsWith(cosBucket + ".")) {
            return cleaned.substring(cosBucket.length() + 1);
        }
        return cleaned;
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(500, message);
        }
        return value.trim();
    }
}
