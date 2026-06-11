package com.huanjing.geo.module.retention.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ObjectStorageReferenceColumnVO {
    private String tableName;
    private String columnName;
    private String managedPrefix;
    private String note;
}
