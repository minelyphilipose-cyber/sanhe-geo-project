package com.huanjing.geo.module.presale.export.service;

import com.huanjing.geo.common.storage.MinioStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PresaleExportStorageService {
    private final MinioStorageService minioStorageService;

    public void uploadSnapshot(byte[] bytes, String objectKey) {
        minioStorageService.uploadBytes(bytes, objectKey, "application/json; charset=utf-8");
    }

    public void uploadPdf(byte[] bytes, String objectKey) {
        minioStorageService.uploadBytes(bytes, objectKey, "application/pdf");
    }

    public void uploadDebugFile(byte[] bytes, String objectKey, String contentType) {
        minioStorageService.uploadBytes(bytes, objectKey, contentType);
    }

    public byte[] readObject(String objectKey) {
        return minioStorageService.getObjectBytes(objectKey);
    }

    public String presignedDownloadUrl(String objectKey) {
        return minioStorageService.buildPresignedDownloadUrl(objectKey, 600);
    }

    public void remove(String objectKey) {
        minioStorageService.remove(objectKey);
    }

    public void removeStrict(String objectKey) throws Exception {
        minioStorageService.removeStrict(objectKey);
    }

    public void removePrefixStrict(String prefix) throws Exception {
        minioStorageService.removePrefixStrict(prefix);
    }
}
