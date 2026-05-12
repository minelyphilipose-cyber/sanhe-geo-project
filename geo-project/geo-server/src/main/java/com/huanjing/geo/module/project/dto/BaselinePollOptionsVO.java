package com.huanjing.geo.module.project.dto;

import lombok.Data;

import java.util.List;

@Data
public class BaselinePollOptionsVO {
    private List<BaselinePollOptionVO> platforms;
    private List<BaselinePollQuestionTierVO> questionTiers;
    private BaselinePollBatchVO latestBatch;
}
