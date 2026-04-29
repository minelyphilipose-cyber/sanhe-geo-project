package com.huanjing.geo.module.project.dto;

import lombok.Data;

import java.util.List;

@Data
public class KeywordLlmQuestionGenerateVO {
    private String generationToken;
    private String seedText;
    private List<String> newQuestions;
}
