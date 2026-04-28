package com.huanjing.geo.module.system.dto;

import com.huanjing.geo.module.project.dto.KeywordTypeConfigVO;
import lombok.Data;

import java.util.List;

@Data
public class KeywordAffixWordOptionVO {
    private List<KeywordAffixWordOptionItemVO> areaWords;
    private List<KeywordAffixWordOptionItemVO> prefixWords;
    private List<KeywordAffixWordOptionItemVO> suffixWords;
    private List<KeywordAffixWordOptionItemVO> industryWords;
    private List<KeywordAffixWordOptionItemVO> compareWords;
    private List<KeywordTypeOptionVO> typeOptions;
    private List<KeywordTypeConfigVO> typeConfigs;
    private KeywordTypeConfigVO currentTypeConfig;
}
