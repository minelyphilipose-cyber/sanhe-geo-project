package com.huanjing.geo.common.storage;

import com.huanjing.geo.common.exception.BizException;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MinioObjectStorageAdapter implements ObjectStorageService {

    private final MinioStorageService minioStorageService;
    private final MinioClient minioClient;

    @Value("${geo.minio.bucket}")
    private String bucket;

    @Override
    public void putBytes(String objectKey, byte[] bytes, String contentType) {
        minioStorageService.uploadBytes(bytes, objectKey, contentType);
    }

    @Override
    public byte[] readBytes(String objectKey) {
        return minioStorageService.getObjectBytes(objectKey);
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
            throw new BizException(500, "Object storage stat failed", ex);
        }
    }
}
