package com.huanjing.geo.module.system.dto;

import com.huanjing.geo.module.system.entity.KeywordAffixWord;
import lombok.Data;

import java.util.List;

@Data
public class KeywordAffixWordOptionVO {
    private List<KeywordAffixWord> prefixWords;
    private List<KeywordAffixWord> suffixWords;
    private List<KeywordAffixWord> industryWords;
    private List<KeywordTypeOptionVO> typeOptions;
}
