package com.huanjing.geo.module.partner.dto;

import com.huanjing.geo.module.project.dto.KeywordWordItemVO;
import lombok.Data;

import java.util.List;

@Data
public class PartnerKeywordGroupColumnsVO {
    private List<KeywordWordItemVO> areaWords;
    private List<KeywordWordItemVO> prefixWords;
    private List<KeywordWordItemVO> coreWords;
    private List<KeywordWordItemVO> industryWords;
    private List<KeywordWordItemVO> suffixWords;
    private List<KeywordWordItemVO> coreQuestionWords;
}
