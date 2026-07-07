package com.huanjing.geo.common.storage;

import com.huanjing.geo.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;

@Primary
@Service
@Slf4j
public class RoutingObjectStorageService implements ObjectStorageService {

    private final ObjectStorageService minioBackend;
    private final ObjectStorageService cosBackend;
    private final StorageProperties storageProperties;

    public RoutingObjectStorageService(
            @Qualifier("minioObjectStorageBackend") ObjectStorageService minioBackend,
            @Qualifier("cosObjectStorageBackend") ObjectStorageService cosBackend,
            StorageProperties storageProperties) {
        this.minioBackend = minioBackend;
        this.cosBackend = cosBackend;
        this.storageProperties = storageProperties;
    }

    @Override
    public void putBytes(String objectKey, byte[] bytes, String contentType) {
        currentBackend().putBytes(objectKey, bytes, contentType);
    }

    @Override
    public byte[] readBytes(String objectKey) {
        return readWithFallback("readBytes", objectKey, ObjectStorageService::readBytes);
    }

    @Override
    public InputStream openStream(String objectKey) {
        return readWithFallback("openStream", objectKey, ObjectStorageService::openStream);
    }

    @Override
    public ObjectStat stat(String objectKey) {
        return readWithFallback("stat", objectKey, ObjectStorageService::stat);
    }

    @Override
    public List<ObjectItem> listObjects(String prefix, int limit) {
        return currentBackend().listObjects(prefix, limit);
    }

    @Override
    public String presignedGetUrl(String objectKey, int ttlSeconds) {
        return currentBackend().presignedGetUrl(objectKey, ttlSeconds);
    }

    @Override
    public void delete(String objectKey) {
        currentBackend().delete(objectKey);
    }

    @Override
    public void deletePrefix(String prefix) {
        currentBackend().deletePrefix(prefix);
    }

    private <T> T readWithFallback(String methodName, String objectKey, StorageRead<T> read) {
        if (storageProperties.getProvider() == StorageProperties.Provider.COS) {
            try {
                return read.get(cosBackend, objectKey);
            } catch (BizException ex) {
                if (ex.getCode() == 404 && storageProperties.isReadFallbackToMinio()) {
                    log.warn("Object storage {} COS miss, fell back to MinIO objectKey={}", methodName, objectKey);
                    return read.get(minioBackend, objectKey);
                }
                throw ex;
            }
        }
        return read.get(minioBackend, objectKey);
    }

    private ObjectStorageService currentBackend() {
        if (storageProperties.getProvider() == StorageProperties.Provider.COS) {
            return cosBackend;
        }
        return minioBackend;
    }

    @FunctionalInterface
    private interface StorageRead<T> {
        T get(ObjectStorageService storageService, String objectKey);
    }
}
