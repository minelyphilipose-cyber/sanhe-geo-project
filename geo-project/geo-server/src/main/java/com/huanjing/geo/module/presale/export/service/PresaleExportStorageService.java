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

    public byte[] readObject(String objectKey) {
        return minioStorageService.getObjectBytes(objectKey);
    }

    public String presignedDownloadUrl(String objectKey) {
        return minioStorageService.buildPresignedDownloadUrl(objectKey, 600);
    }

    public void remove(String objectKey) {
        minioStorageService.remove(objectKey);
    }
}