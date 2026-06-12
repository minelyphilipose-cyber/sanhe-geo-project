package com.huanjing.geo.module.customer.dto;

import com.huanjing.geo.module.customer.entity.BrandImageFolder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class BrandImageFolderVO {
    private Long id;
    private Long brandId;
    private String folderName;
    private String description;
    private String status;
    private Boolean isDefault;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<Long> projectIds = new ArrayList<>();
    private List<String> tags = new ArrayList<>();
    private List<BrandMaterialVO> materials = new ArrayList<>();
    private Integer materialCount;
    private Boolean projectRelated;

    public static BrandImageFolderVO from(BrandImageFolder folder) {
        BrandImageFolderVO vo = new BrandImageFolderVO();
        vo.setId(folder.getId());
        vo.setBrandId(folder.getBrandId());
        vo.setFolderName(folder.getFolderName());
        vo.setDescription(folder.getDescription());
        vo.setStatus(folder.getStatus());
        vo.setIsDefault(folder.getDefaultFlag());
        vo.setCreatedBy(folder.getCreatedBy());
        vo.setCreatedAt(folder.getCreatedAt());
        vo.setUpdatedAt(folder.getUpdatedAt());
        return vo;
    }
}
