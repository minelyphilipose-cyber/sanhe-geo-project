package com.huanjing.geo.module.project.service;

import lombok.Data;

@Data
class BaselineObservationScoringResult {
    private boolean mentioned;
    private boolean recommended;
    private Integer rankingPosition;
    private String sentiment;
    private String impressionState;
    private String mentionType;
    private String judgeEvidence;
}
