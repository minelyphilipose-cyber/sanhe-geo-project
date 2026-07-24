package com.huanjing.geo.module.project.service;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
class BaselineSemanticJudgeResult {
    private boolean judgeUsed;
    private String judgeRawResponse;
    private String judgeError;
    private boolean mentioned;
    private boolean recommended;
    private Integer rankingPosition;
    private String sentiment;
    private String impressionState;
    private String mentionType;
    private String judgeEvidence;
    private EntityHit brandHit;
    private List<EntityHit> competitorHits = new ArrayList<>();
    private List<EntityHit> negativeHits = new ArrayList<>();

    @Data
    static class EntityHit {
        private Long entityId;
        private String canonicalName;
        private String rawText;
        private String hitType;
        private boolean tracked;
        private int mentionCount = 1;
        private Integer startOffset;
        private Integer endOffset;

        boolean hasOffset() {
            return startOffset != null && endOffset != null && endOffset > startOffset;
        }
    }
}
