package com.huanjing.geo.common.storage;

public interface ObjectStorageService {

    void putBytes(String objectKey, byte[] bytes, String contentType);

    byte[] readBytes(String objectKey);

    ObjectStat stat(String objectKey);

    record ObjectStat(String objectKey, long size, String etag) {
    }
}
