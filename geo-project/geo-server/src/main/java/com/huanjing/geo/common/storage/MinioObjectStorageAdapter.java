package com.huanjing.geo.common.storage;

import com.huanjing.geo.common.exception.BizException;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

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
            throw new BizException(500, "Object storage list failed", ex);
        }
    }
}
