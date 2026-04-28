package com.huanjing.geo.module.project.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KeywordRequiredColumnsVO {
    private boolean area;
    private boolean prefix;
    private boolean core;
    private boolean industry;
    private boolean suffix;
    private boolean compareCore;
    private boolean compareWord;
}
