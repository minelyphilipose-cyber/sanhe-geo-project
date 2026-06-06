package com.huanjing.geo.module.presale.generate.calc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.presale.dto.snapshot.computed.SceneCompetitorPressure;
import com.huanjing.geo.module.presale.dto.snapshot.raw.Competitor;
import com.huanjing.geo.module.presale.dto.snapshot.raw.RawSnapshotDTO;
import com.huanjing.geo.module.presale.dto.snapshot.raw.TestSummary;
import com.huanjing.geo.module.presale.generate.PresaleCompetitorAggregator;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiPromptResult;
import com.huanjing.geo.module.presale.persist.entity.PresaleReportVersionPromptTemplate;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiPromptResultMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportVersionPromptTemplateMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SceneCompetitorPressureCalculatorTest {

    @Mock
    private PresaleReportVersionPromptTemplateMapper versionPromptTemplateMapper;
    @Mock
    private PresaleAiPromptResultMapper aiPromptResultMapper;
    @Mock
    private PresaleCompetitorAggregator competitorAggregator;

    @Test
    void compute_countsOnlyRecommendationScenesAndFlagsNaturalSuppression() {
        SceneCompetitorPressureCalculator calculator = new SceneCompetitorPressureCalculator(
                versionPromptTemplateMapper, aiPromptResultMapper, competitorAggregator, new ObjectMapper());
        when(versionPromptTemplateMapper.selectList(any())).thenReturn(List.of(
                template(11L, "REC-1", "推荐型", "高", 0, "种牙医院推荐哪家?"),
                template(12L, "REC-2", "推荐型", "高", 0, "附近牙科推荐"),
                template(13L, "REC-LOW", "推荐型", "中", 0, "牙科知识科普"),
                template(21L, "CMP-1", "对比型", "高", 1, "A 和 B 哪个好"),
                template(31L, "COG-1", "认知型", "中", 0, "了解品牌")
        ));
        when(aiPromptResultMapper.selectList(any())).thenReturn(List.of(
                row(11L, "doubao", 0, "[\"竞品A\"]"),
                row(11L, "kimi", 0, "[\"竞品A\"]"),
                row(12L, "doubao", 1, "[\"竞品A\"]"),
                row(21L, "doubao", 0, "[\"竞品A\"]"),
                row(31L, "doubao", 0, "[\"竞品A\"]")
        ));
        when(competitorAggregator.matchCompetitorDisplayName(any(), any())).thenAnswer(invocation -> {
            String value = invocation.getArgument(0, String.class);
            List<String> candidates = invocation.getArgument(1);
            return candidates.stream().filter(value::equals).findFirst();
        });

        SceneCompetitorPressure result = calculator.compute(388L, raw());

        assertEquals(2, result.getHvRecoTotal());
        assertEquals(1, result.getSuppressedSceneCount());
        assertEquals("竞品A", result.getTopSuppressingCompetitor());
        assertEquals(2, result.getItems().size());

        SceneCompetitorPressure.Item first = result.getItems().get(0);
        assertTrue(Boolean.TRUE.equals(first.getSuppressed()));
        assertEquals(0, first.getTargetMentionedPlatformCount());
        assertEquals(2, first.getPlatformsEvaluated());
        assertEquals(1, first.getCompetitors().size());
        assertEquals("竞品A", first.getCompetitors().get(0).getName());
        assertEquals(2, first.getCompetitors().get(0).getMentionedPlatformCount());

        SceneCompetitorPressure.Item second = result.getItems().get(1);
        assertFalse(Boolean.TRUE.equals(second.getSuppressed()));
        assertEquals(1, second.getTargetMentionedPlatformCount());
    }

    @Test
    void compute_matchesKnownCompetitorAliasesWithoutMergingDistinctHospitals() {
        PresaleCompetitorAggregator realAggregator = new PresaleCompetitorAggregator(
                null, new ObjectMapper(), new com.huanjing.geo.module.presale.generate.CompetitorNameNormalizer());
        SceneCompetitorPressureCalculator calculator = new SceneCompetitorPressureCalculator(
                versionPromptTemplateMapper, aiPromptResultMapper, realAggregator, new ObjectMapper());
        when(versionPromptTemplateMapper.selectList(any())).thenReturn(List.of(
                template(41L, "REC-MEIAO", "推荐型", "高", 0, "种植牙性价比高的机构推荐一下?"),
                template(42L, "REC-HOSPITAL", "推荐型", "高", 0, "牙齿矫正哪家口腔医院比较好?")
        ));
        when(aiPromptResultMapper.selectList(any())).thenReturn(List.of(
                row(41L, "p1", 0, "[\"阜阳美奥口腔医院\"]"),
                row(41L, "p2", 0, "[\"阜阳美奥口腔\"]"),
                row(42L, "p1", 0, "[\"阜阳市人民医院口腔科\"]"),
                row(42L, "p2", 0, "[\"阜阳市第二人民医院口腔科\"]")
        ));

        SceneCompetitorPressure result = calculator.compute(390L, RawSnapshotDTO.builder()
                .competitors(List.of(
                        Competitor.builder().name("阜阳市人民医院口腔科").build(),
                        Competitor.builder().name("阜阳市第二人民医院口腔科").build(),
                        Competitor.builder().name("美奥口腔").build()
                ))
                .testSummary(TestSummary.builder().build())
                .build());

        SceneCompetitorPressure.Item meiaoScene = result.getItems().get(0);
        assertEquals("美奥口腔", meiaoScene.getCompetitors().get(0).getName());
        assertEquals(2, meiaoScene.getCompetitors().get(0).getMentionedPlatformCount());

        SceneCompetitorPressure.Item hospitalScene = result.getItems().get(1);
        assertEquals(1, hospitalScene.getCompetitors().stream()
                .filter(item -> "阜阳市人民医院口腔科".equals(item.getName()))
                .findFirst().orElseThrow().getMentionedPlatformCount());
        assertEquals(1, hospitalScene.getCompetitors().stream()
                .filter(item -> "阜阳市第二人民医院口腔科".equals(item.getName()))
                .findFirst().orElseThrow().getMentionedPlatformCount());
    }

    private static PresaleReportVersionPromptTemplate template(Long id,
                                                               String code,
                                                               String category,
                                                               String businessValue,
                                                               Integer hasCompetitorVar,
                                                               String prompt) {
        PresaleReportVersionPromptTemplate template = new PresaleReportVersionPromptTemplate();
        template.setId(id);
        template.setSourcePromptCode(code);
        template.setCategory(category);
        template.setBusinessValue(businessValue);
        template.setHasCompetitorVar(hasCompetitorVar);
        template.setPromptContent(prompt);
        return template;
    }

    private static PresaleAiPromptResult row(Long templateId,
                                             String platformCode,
                                             Integer isMentioned,
                                             String mentionedCompetitors) {
        PresaleAiPromptResult row = new PresaleAiPromptResult();
        row.setPromptTemplateId(templateId);
        row.setPlatformCode(platformCode);
        row.setIsMentioned(isMentioned);
        row.setMentionedCompetitors(mentionedCompetitors);
        return row;
    }

    private static RawSnapshotDTO raw() {
        return RawSnapshotDTO.builder()
                .competitors(List.of(Competitor.builder().name("竞品A").build()))
                .testSummary(TestSummary.builder()
                        .degradedPlatforms(List.of("failed-platform"))
                        .build())
                .build();
    }
}
