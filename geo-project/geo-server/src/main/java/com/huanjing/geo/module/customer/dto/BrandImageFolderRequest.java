package com.huanjing.geo.module.customer.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class BrandImageFolderRequest {
    @NotBlank
    private String folderName;
    private String description;
    private String status;
    private List<Long> projectIds;
    private List<String> tags;
}
