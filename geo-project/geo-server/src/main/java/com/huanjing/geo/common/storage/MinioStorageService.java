package com.huanjing.geo.common.storage;

import com.huanjing.geo.common.exception.BizException;
import io.minio.*;
import io.minio.http.Method;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioStorageService {

    private final MinioClient minioClient;

    @Value("${geo.minio.endpoint}")
    private String endpoint;
    @Value("${geo.minio.public-endpoint:${geo.minio.endpoint}}")
    private String publicEndpoint;
    @Value("${geo.minio.bucket}")
    private String bucket;

    public String upload(MultipartFile file, String objectKey, String contentType) {
        try {
            ensureBucket();
            try (InputStream in = file.getInputStream()) {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucket)
                                .object(objectKey)
                                .stream(in, file.getSize(), -1)
                                .contentType(StringUtils.hasText(contentType) ? contentType : "application/octet-stream")
                                .build()
                );
            }
            return buildFileUrl(objectKey);
        } catch (Exception ex) {
            log.error("Upload file to minio failed, objectKey={}, err={}", objectKey, ex.getMessage(), ex);
            throw new BizException(500, "Upload file failed");
        }
    }

    public String uploadBytes(byte[] bytes, String objectKey, String contentType) {
        try {
            ensureBucket();
            try (InputStream in = new java.io.ByteArrayInputStream(bytes)) {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucket)
                                .object(objectKey)
                                .stream(in, bytes.length, -1)
                                .contentType(StringUtils.hasText(contentType) ? contentType : "application/octet-stream")
                                .build()
                );
            }
            return buildFileUrl(objectKey);
        } catch (Exception ex) {
            log.error("Upload bytes to minio failed, objectKey={}, err={}", objectKey, ex.getMessage(), ex);
            throw new BizException(500, "Upload file failed");
        }
    }

    public void remove(String objectKey) {
        try {
            removeStrict(objectKey);
        } catch (Exception ex) {
            log.warn("Remove minio object failed, objectKey={}, err={}", objectKey, ex.getMessage());
        }
    }

    public void removeStrict(String objectKey) throws Exception {
        minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
    }

    public void removePrefixStrict(String prefix) throws Exception {
        Iterable<Result<Item>> results = minioClient.listObjects(ListObjectsArgs.builder()
                .bucket(bucket)
                .prefix(prefix)
                .recursive(true)
                .build());
        for (Result<Item> result : results) {
            Item item = result.get();
            removeStrict(item.objectName());
        }
    }

    public byte[] getObjectBytes(String objectKey) {
        try (GetObjectResponse response = minioClient.getObject(
                GetObjectArgs.builder().bucket(bucket).object(objectKey).build()
        )) {
            return response.readAllBytes();
        } catch (Exception ex) {
            log.error("Read minio object failed, objectKey={}, err={}", objectKey, ex.getMessage(), ex);
            throw new BizException(500, "Read file failed");
        }
    }

    public String buildFileUrl(String objectKey) {
        String source = StringUtils.hasText(publicEndpoint) ? publicEndpoint : endpoint;
        String normalized = source.endsWith("/") ? source.substring(0, source.length() - 1) : source;
        return normalized + "/" + bucket + "/" + objectKey;
    }

    public String buildPresignedDownloadUrl(String objectKey, int expireSeconds) {
        try {
            ensureBucket();
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry(expireSeconds)
                            .build()
            );
        } catch (Exception ex) {
            throw new BizException(500, "Generate presigned url failed");
        }
    }

    public String resolveAccessibleUrl(String objectKey, String fallbackUrl, int expireSeconds) {
        if (StringUtils.hasText(objectKey)) {
            return buildPresignedDownloadUrl(objectKey, expireSeconds);
        }
        return fallbackUrl;
    }

    private void ensureBucket() throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }
}
