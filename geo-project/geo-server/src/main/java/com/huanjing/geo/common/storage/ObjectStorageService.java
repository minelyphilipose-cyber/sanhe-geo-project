package com.huanjing.geo.common.storage;

public interface ObjectStorageService {

    void putBytes(String objectKey, byte[] bytes, String contentType);

    byte[] readBytes(String objectKey);

    java.io.InputStream openStream(String objectKey);

    ObjectStat stat(String objectKey);

    java.util.List<ObjectItem> listObjects(String prefix, int limit);

    String presignedGetUrl(String objectKey, int ttlSeconds);

    void delete(String objectKey);

    void deletePrefix(String prefix);

    record ObjectStat(String objectKey, long size, String etag) {
    }

    record ObjectItem(String objectKey, long size, java.time.OffsetDateTime lastModified) {
    }
}
