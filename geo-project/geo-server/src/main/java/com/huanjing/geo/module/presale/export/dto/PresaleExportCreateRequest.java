package com.huanjing.geo.module.presale.export.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PresaleExportCreateRequest {
    @NotNull
    private Long versionId;
    private String exportProfile = "PDF_A4_DPR2";
    private String editableContentHash;
    private Boolean forceRefresh = false;
}