package com.huanjing.geo.module.project.dto;

import lombok.Data;

import java.util.List;

@Data
public class KeywordGroupColumnsVO {
    private List<KeywordWordItemVO> regionWords;
    private List<KeywordWordItemVO> prefixWords;
    private List<KeywordWordItemVO> coreWords;
    private List<KeywordWordItemVO> industryWords;
    private List<KeywordWordItemVO> suffixWords;
}
