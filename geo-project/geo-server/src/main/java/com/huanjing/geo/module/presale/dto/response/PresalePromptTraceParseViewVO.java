package com.huanjing.geo.module.presale.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PresalePromptTraceParseViewVO {
    private String mentionedText;
    private String attributionType;
    private Boolean targetEntityHit;
    private Boolean representedBrandHit;
    private Boolean targetBrandRelationHit;
    private String rankingText;
    private String sentimentText;
    private String sentimentType;
    private List<String> mentionedCompetitors;
    private List<String> sceneAdvantages;
    private List<KeywordView> topKeywords;
    private NegativeEvidenceView negativeEvidence;

    @Data
    @Builder
    public static class KeywordView {
        private String keyword;
        private String sentimentText;
        private String sentimentType;
    }

    @Data
    @Builder
    public static class NegativeEvidenceView {
        private String hasNegativeText;
        private Boolean hasNegative;
        private String snippet;
    }
}
