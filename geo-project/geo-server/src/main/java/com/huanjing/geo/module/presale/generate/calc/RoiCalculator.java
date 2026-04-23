package com.huanjing.geo.module.presale.generate.calc;

import com.huanjing.geo.module.presale.dto.snapshot.computed.OptimizationFinding;
import com.huanjing.geo.module.presale.dto.snapshot.computed.RoiSimulation;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RoiCalculator {

    private static final double PHASE1_UPLIFT = 5.0;
    private static final double PHASE2_UPLIFT = 12.0;
    private static final double PHASE3_UPLIFT = 20.0;
    private static final double SCORE_CAP = 100.0;
    private static final double FIXED_EXPOSURE_MULTIPLIER = 1.8;

    public RoiSimulation compute(Double overallScore, List<OptimizationFinding> findings) {
        double current = overallScore == null ? 0.0 : overallScore;

        double t1 = Math.min(current + PHASE1_UPLIFT, SCORE_CAP);
        double t2 = Math.min(current + PHASE2_UPLIFT, SCORE_CAP);
        double t3 = Math.min(current + PHASE3_UPLIFT, SCORE_CAP);

        int phase1Total = 0;
        int phase2Total = 0;
        int phase3Total = 0;
        for (OptimizationFinding finding : findings == null ? List.<OptimizationFinding>of() : findings) {
            if (finding == null || finding.getPriority() == null) {
                phase3Total++;
                continue;
            }
            switch (finding.getPriority()) {
                case HIGH -> phase1Total++;
                case MEDIUM -> phase2Total++;
                case LOW -> phase3Total++;
            }
        }

        List<RoiSimulation.RoiPhase> phases = List.of(
                RoiSimulation.RoiPhase.builder()
                        .phaseNo(1)
                        .durationLabel("M1")
                        .targetScore(t1)
                        .upliftFromPrevious(t1 - current)
                        .completedOptimizationCount(0)
                        .totalOptimizationCount(phase1Total)
                        .build(),
                RoiSimulation.RoiPhase.builder()
                        .phaseNo(2)
                        .durationLabel("M2-3")
                        .targetScore(t2)
                        .upliftFromPrevious(t2 - t1)
                        .completedOptimizationCount(0)
                        .totalOptimizationCount(phase2Total)
                        .build(),
                RoiSimulation.RoiPhase.builder()
                        .phaseNo(3)
                        .durationLabel("M4-6")
                        .targetScore(t3)
                        .upliftFromPrevious(t3 - t2)
                        .completedOptimizationCount(0)
                        .totalOptimizationCount(phase3Total)
                        .build()
        );

        double upliftPercent = current == 0.0 ? 0.0 : ((t3 - current) / current * 100.0);
        return RoiSimulation.builder()
                .currentScore(current)
                .targetScore(t3)
                .estimatedUpliftPercent(upliftPercent)
                .estimatedExposureMultiplier(FIXED_EXPOSURE_MULTIPLIER)
                .phases(phases)
                .build();
    }
}

