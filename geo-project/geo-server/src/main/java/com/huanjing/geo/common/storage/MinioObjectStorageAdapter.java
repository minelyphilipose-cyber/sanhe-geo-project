package com.huanjing.geo.common.storage;

import com.huanjing.geo.common.exception.BizException;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service("minioObjectStorageBackend")
@RequiredArgsConstructor
public class MinioObjectStorageAdapter implements ObjectStorageService {

    private final MinioClient minioClient;

    @Value("${geo.minio.endpoint}")
    private String endpoint;
    @Value("${geo.minio.public-endpoint:${geo.minio.endpoint}}")
    private String publicEndpoint;
    @Value("${geo.minio.access-key}")
    private String accessKey;
    @Value("${geo.minio.secret-key}")
    private String secretKey;
    @Value("${geo.minio.bucket}")
    private String bucket;
    @Value("${geo.minio.region:us-east-1}")
    private String region;
    private volatile MinioClient publicMinioClient;

    @Override
    public void putBytes(String objectKey, byte[] bytes, String contentType) {
        try {
            ensureBucket();
            try (InputStream inputStream = new ByteArrayInputStream(bytes)) {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucket)
                                .object(objectKey)
                                .stream(inputStream, bytes.length, -1)
                                .contentType(StringUtils.hasText(contentType) ? contentType : "application/octet-stream")
                                .build()
                );
            }
        } catch (Exception ex) {
            throw wrap("Object storage put failed", ex);
        }
    }

    @Override
    public byte[] readBytes(String objectKey) {
        try (InputStream inputStream = openStream(objectKey)) {
            return inputStream.readAllBytes();
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw wrap("Object storage read failed", ex);
        }
    }

    @Override
    public InputStream openStream(String objectKey) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build()
            );
        } catch (Exception ex) {
            throw wrap("Object storage stream failed", ex);
        }
    }

    @Override
    public ObjectStat stat(String objectKey) {
        try {
            StatObjectResponse response = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build()
            );
            return new ObjectStat(objectKey, response.size(), response.etag());
        } catch (Exception ex) {
            throw wrap("Object storage stat failed", ex);
        }
    }

    @Override
    public List<ObjectItem> listObjects(String prefix, int limit) {
        int max = Math.max(1, limit);
        List<ObjectItem> items = new ArrayList<>();
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucket)
                            .prefix(prefix)
                            .recursive(true)
                            .build()
            );
            for (Result<Item> result : results) {
                Item item = result.get();
                if (item.isDir()) {
                    continue;
                }
                items.add(new ObjectItem(item.objectName(), item.size(),
                        item.lastModified() == null ? null : item.lastModified().toOffsetDateTime()));
                if (items.size() >= max) {
                    break;
                }
            }
            return items;
        } catch (Exception ex) {
            throw wrap("Object storage list failed", ex);
        }
    }

    @Override
    public String presignedGetUrl(String objectKey, int ttlSeconds) {
        try {
            ensureBucket();
            String presignedUrl = presignClient().getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .region(region)
                            .object(objectKey)
                            .expiry(ttlSeconds)
                            .build()
            );
            return applyPublicEndpointPathPrefix(presignedUrl);
        } catch (Exception ex) {
            throw wrap("Object storage presign failed", ex);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception ex) {
            throw wrap("Object storage delete failed", ex);
        }
    }

    @Override
    public void deletePrefix(String prefix) {
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucket)
                            .prefix(prefix)
                            .recursive(true)
                            .build()
            );
            for (Result<Item> result : results) {
                Item item = result.get();
                if (!item.isDir()) {
                    delete(item.objectName());
                }
            }
        } catch (Exception ex) {
            throw wrap("Object storage delete prefix failed", ex);
        }
    }

    private void ensureBucket() throws Exception {
        boolean exists = minioClient.bucketExists(io.minio.BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }

    private MinioClient presignClient() {
        String source = endpointSource();
        String sourceOrigin = endpointOrigin(source);
        if (Objects.equals(sourceOrigin, endpointOrigin(cleanEndpoint(endpoint)))) {
            return minioClient;
        }
        MinioClient existing = publicMinioClient;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (publicMinioClient == null) {
                publicMinioClient = MinioClient.builder()
                        .endpoint(sourceOrigin)
                        .region(region)
                        .credentials(accessKey, secretKey)
                        .build();
            }
            return publicMinioClient;
        }
    }

    private String applyPublicEndpointPathPrefix(String presignedUrl) {
        String prefix = endpointPathPrefix(endpointSource());
        if (!StringUtils.hasText(prefix)) {
            return presignedUrl;
        }

        URI uri = URI.create(presignedUrl);
        String path = uri.getRawPath();
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        String query = StringUtils.hasText(uri.getRawQuery()) ? "?" + uri.getRawQuery() : "";
        String fragment = StringUtils.hasText(uri.getRawFragment()) ? "#" + uri.getRawFragment() : "";
        return uri.getScheme() + "://" + uri.getRawAuthority() + prefix + normalizedPath + query + fragment;
    }

    private String endpointSource() {
        return StringUtils.hasText(publicEndpoint) ? cleanEndpoint(publicEndpoint) : cleanEndpoint(endpoint);
    }

    private String endpointOrigin(String source) {
        URI uri = URI.create(cleanEndpoint(source));
        if (!StringUtils.hasText(uri.getScheme()) || !StringUtils.hasText(uri.getRawAuthority())) {
            return cleanEndpoint(source);
        }
        return uri.getScheme() + "://" + uri.getRawAuthority();
    }

    private String endpointPathPrefix(String source) {
        URI uri = URI.create(cleanEndpoint(source));
        String path = uri.getRawPath();
        if (!StringUtils.hasText(path) || "/".equals(path)) {
            return "";
        }
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    private String cleanEndpoint(String source) {
        return source == null ? "" : source.trim();
    }

    private BizException wrap(String message, Exception ex) {
        if (ex instanceof ErrorResponseException minioEx
                && "NoSuchKey".equals(minioEx.errorResponse().code())) {
            return new BizException(404, message + ": object not found", ex);
        }
        return new BizException(500, message, ex);
    }
}
