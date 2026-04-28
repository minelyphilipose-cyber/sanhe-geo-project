package com.huanjing.geo.module.project.dto;

import lombok.Data;

import java.util.List;

@Data
public class KeywordGroupColumnsVO {
    private List<KeywordWordItemVO> areaWords;
    /**
     * Legacy alias kept for old frontend compatibility. New UI should read areaWords.
     * TODO V1.6: remove after frontend and external callers fully migrate.
     */
    @Deprecated
    private List<KeywordWordItemVO> regionWords;
    private List<KeywordWordItemVO> prefixWords;
    private List<KeywordWordItemVO> coreWords;
    private List<KeywordWordItemVO> industryWords;
    private List<KeywordWordItemVO> suffixWords;
    private List<KeywordWordItemVO> coreWordsA;
    private List<KeywordWordItemVO> compareWords;
    private List<KeywordWordItemVO> coreWordsB;
}
