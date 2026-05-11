package com.huanjing.geo.module.project.dto;

import lombok.Data;

@Data
public class KeywordGroupImportResultVO {
    private KeywordGroupVO group;
    private int importedCount;
    private int countA;
    private int countB;
    private int countC;
}
