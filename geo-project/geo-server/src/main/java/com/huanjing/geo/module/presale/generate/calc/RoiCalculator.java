package com.huanjing.geo.module.presale.generate.calc;

import com.huanjing.geo.module.presale.dto.snapshot.computed.OptimizationFinding;
import com.huanjing.geo.module.presale.dto.snapshot.computed.RoiSimulation;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RoiCalculator {

    private static final double TARGET_SCORE_CAP = 95.0;
    private static final double FIXED_EXPOSURE_MULTIPLIER = 1.8;

    public RoiSimulation compute(Double overallScore, List<OptimizationFinding> findings) {
        double current = overallScore == null ? 0.0 : overallScore;

        PhaseUplift uplift = resolvePhaseUplift(current);
        double t1 = capTargetScore(current + uplift.phase1());
        double t2 = capTargetScore(current + uplift.phase2());
        double t3 = capTargetScore(current + uplift.phase3());

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
                        .upliftFromPrevious(Math.max(0.0, t1 - current))
                        .completedOptimizationCount(0)
                        .totalOptimizationCount(phase1Total)
                        .build(),
                RoiSimulation.RoiPhase.builder()
                        .phaseNo(2)
                        .durationLabel("M2-3")
                        .targetScore(t2)
                        .upliftFromPrevious(Math.max(0.0, t2 - t1))
                        .completedOptimizationCount(0)
                        .totalOptimizationCount(phase2Total)
                        .build(),
                RoiSimulation.RoiPhase.builder()
                        .phaseNo(3)
                        .durationLabel("M4-6")
                        .targetScore(t3)
                        .upliftFromPrevious(Math.max(0.0, t3 - t2))
                        .completedOptimizationCount(0)
                        .totalOptimizationCount(phase3Total)
                        .build()
        );

        double upliftPercent = Double.compare(current, 0.0) == 0 ? 0.0 : Math.max(0.0, (t3 - current) / current * 100.0);
        return RoiSimulation.builder()
                .currentScore(current)
                .targetScore(t3)
                .estimatedUpliftPercent(upliftPercent)
                .estimatedExposureMultiplier(FIXED_EXPOSURE_MULTIPLIER)
                .phases(phases)
                .build();
    }

    private PhaseUplift resolvePhaseUplift(double current) {
        if (current >= 95.0) {
            return new PhaseUplift(0.0, 0.0, 0.0);
        }
        if (current >= 90.0) {
            return new PhaseUplift(1.0, 2.0, 3.0);
        }
        if (current >= 85.0) {
            return new PhaseUplift(2.0, 4.0, 6.0);
        }
        if (current >= 75.0) {
            return new PhaseUplift(3.0, 7.0, 10.0);
        }
        if (current >= 60.0) {
            return new PhaseUplift(4.0, 9.0, 15.0);
        }
        return new PhaseUplift(5.0, 12.0, 20.0);
    }

    private double capTargetScore(double score) {
        return Math.min(score, TARGET_SCORE_CAP);
    }

    private record PhaseUplift(double phase1, double phase2, double phase3) {
    }
}
