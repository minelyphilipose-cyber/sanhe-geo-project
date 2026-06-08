package com.huanjing.geo.module.presale.generate.calc;

import com.huanjing.geo.module.presale.dto.snapshot.computed.OptimizationFinding;
import com.huanjing.geo.module.presale.dto.snapshot.computed.RoiSimulation;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RoiCalculator {

    private static final double TARGET_SCORE_CAP = 95.0;
    private static final double MAX_TOTAL_TARGET_UPLIFT = 30.0;
    private static final PhaseContribution HIGH_CONTRIBUTION = new PhaseContribution(2.0, 3.0);
    private static final PhaseContribution MEDIUM_CONTRIBUTION = new PhaseContribution(1.0, 2.0);
    private static final PhaseContribution LOW_CONTRIBUTION = new PhaseContribution(0.5, 1.0);

    public RoiSimulation compute(Double overallScore, List<OptimizationFinding> findings) {
        double current = overallScore == null ? 0.0 : overallScore;

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

        PhaseRange phase1Range = phaseRange(current, 0.0, 0.0, phase1Total, HIGH_CONTRIBUTION);
        PhaseRange phase2Range = phaseRange(current,
                phase1Range.cumulativeLowUplift(), phase1Range.cumulativeHighUplift(),
                phase2Total, MEDIUM_CONTRIBUTION);
        PhaseRange phase3Range = phaseRange(current,
                phase2Range.cumulativeLowUplift(), phase2Range.cumulativeHighUplift(),
                phase3Total, LOW_CONTRIBUTION);

        List<RoiSimulation.RoiPhase> phases = List.of(
                buildPhase(1, "M1", phase1Range, phase1Total),
                buildPhase(2, "M2-3", phase2Range, phase2Total),
                buildPhase(3, "M4-6", phase3Range, phase3Total)
        );

        double targetLow = phase3Range.targetLow();
        double targetHigh = phase3Range.targetHigh();
        double targetMid = midpoint(targetLow, targetHigh);
        double upliftPercentLow = upliftPercent(current, targetLow);
        double upliftPercentHigh = upliftPercent(current, targetHigh);
        double upliftPercentMid = upliftPercent(current, targetMid);
        return RoiSimulation.builder()
                .currentScore(current)
                .targetScore(targetMid)
                .targetScoreLow(targetLow)
                .targetScoreHigh(targetHigh)
                .estimatedUpliftPercent(upliftPercentMid)
                .estimatedUpliftPercentLow(upliftPercentLow)
                .estimatedUpliftPercentHigh(upliftPercentHigh)
                .estimatedExposureMultiplier(null)
                .caseStudyRange(null)
                .phases(phases)
                .build();
    }

    private RoiSimulation.RoiPhase buildPhase(int phaseNo, String durationLabel, PhaseRange range, int plannedCount) {
        return RoiSimulation.RoiPhase.builder()
                .phaseNo(phaseNo)
                .durationLabel(durationLabel)
                .targetScore(midpoint(range.targetLow(), range.targetHigh()))
                .targetScoreLow(range.targetLow())
                .targetScoreHigh(range.targetHigh())
                .upliftFromPrevious(midpoint(range.phaseLowUplift(), range.phaseHighUplift()))
                .upliftFromPreviousLow(range.phaseLowUplift())
                .upliftFromPreviousHigh(range.phaseHighUplift())
                .projectionEnabled(plannedCount > 0)
                .completedOptimizationCount(0)
                .totalOptimizationCount(plannedCount)
                .plannedOptimizationCount(plannedCount)
                .build();
    }

    private PhaseRange phaseRange(double current,
                                  double previousLowUplift,
                                  double previousHighUplift,
                                  int plannedCount,
                                  PhaseContribution contribution) {
        double phaseLow = plannedCount <= 0 ? 0.0 : plannedCount * contribution.low();
        double phaseHigh = plannedCount <= 0 ? 0.0 : plannedCount * contribution.high();
        double cumulativeLow = capUplift(previousLowUplift + phaseLow);
        double cumulativeHigh = capUplift(previousHighUplift + phaseHigh);
        double targetLow = capTargetScore(current + cumulativeLow);
        double targetHigh = capTargetScore(current + cumulativeHigh);
        if (targetHigh < targetLow) {
            targetHigh = targetLow;
        }
        return new PhaseRange(
                targetLow,
                targetHigh,
                Math.max(0.0, cumulativeLow - previousLowUplift),
                Math.max(0.0, cumulativeHigh - previousHighUplift),
                cumulativeLow,
                cumulativeHigh
        );
    }

    private double capTargetScore(double score) {
        return Math.min(score, TARGET_SCORE_CAP);
    }

    private double capUplift(double uplift) {
        return Math.min(uplift, MAX_TOTAL_TARGET_UPLIFT);
    }

    private double midpoint(double low, double high) {
        return (low + high) / 2.0;
    }

    private double upliftPercent(double current, double target) {
        return Double.compare(current, 0.0) == 0 ? 0.0 : Math.max(0.0, (target - current) / current * 100.0);
    }

    private record PhaseContribution(double low, double high) {
    }

    private record PhaseRange(double targetLow,
                              double targetHigh,
                              double phaseLowUplift,
                              double phaseHighUplift,
                              double cumulativeLowUplift,
                              double cumulativeHighUplift) {
    }
}
