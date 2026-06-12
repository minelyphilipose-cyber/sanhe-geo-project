package com.huanjing.geo.module.customer.dto;

import com.huanjing.geo.module.customer.entity.BrandMaterial;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BrandMaterialVO {
    private Long id;
    private Long brandId;
    private Long folderId;
    private String category;
    private String fileName;
    private String fileType;
    /** Deprecated compatibility field. Use publicUrl/preview-url endpoints for access. */
    private String fileUrl;
    private String publicUrl;
    private String objectKey;
    private Long fileSize;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static BrandMaterialVO from(BrandMaterial material, String publicUrl) {
        BrandMaterialVO vo = new BrandMaterialVO();
        vo.setId(material.getId());
        vo.setBrandId(material.getBrandId());
        vo.setFolderId(material.getFolderId());
        vo.setCategory(material.getCategory());
        vo.setFileName(material.getFileName());
        vo.setFileType(material.getFileType());
        vo.setFileUrl(material.getFileUrl());
        vo.setPublicUrl(publicUrl);
        vo.setObjectKey(material.getObjectKey());
        vo.setFileSize(material.getFileSize());
        vo.setCreatedBy(material.getCreatedBy());
        vo.setCreatedAt(material.getCreatedAt());
        vo.setUpdatedAt(material.getUpdatedAt());
        return vo;
    }
}
