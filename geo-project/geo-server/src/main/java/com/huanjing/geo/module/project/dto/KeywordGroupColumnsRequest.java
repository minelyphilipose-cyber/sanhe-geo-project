package com.huanjing.geo.module.project.dto;

import lombok.Data;

import java.util.List;

@Data
public class KeywordGroupColumnsRequest {
    private List<KeywordWordItemRequest> regionWords;
    private List<KeywordWordItemRequest> prefixWords;
    private List<KeywordWordItemRequest> coreWords;
    private List<KeywordWordItemRequest> industryWords;
    private List<KeywordWordItemRequest> suffixWords;
}
