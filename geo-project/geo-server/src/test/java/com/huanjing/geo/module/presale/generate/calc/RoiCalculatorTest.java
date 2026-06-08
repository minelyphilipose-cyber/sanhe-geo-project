package com.huanjing.geo.module.presale.generate.calc;

import com.huanjing.geo.module.presale.dto.snapshot.computed.OptimizationFinding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoiCalculatorTest {

    private final RoiCalculator calculator = new RoiCalculator();

    @Test
    void noFindings_targetDoesNotGrow() {
        var roi = calculator.compute(45.0, List.of());

        assertEquals(45.0, roi.getTargetScoreLow(), 0.0001);
        assertEquals(45.0, roi.getTargetScoreHigh(), 0.0001);
        assertEquals(45.0, roi.getTargetScore(), 0.0001);
        assertEquals(0.0, roi.getEstimatedUpliftPercentLow(), 0.0001);
        assertEquals(0.0, roi.getEstimatedUpliftPercentHigh(), 0.0001);
        assertNull(roi.getEstimatedExposureMultiplier());

        for (var phase : roi.getPhases()) {
            assertEquals(0, phase.getPlannedOptimizationCount());
            assertEquals(0.0, phase.getUpliftFromPreviousLow(), 0.0001);
            assertEquals(0.0, phase.getUpliftFromPreviousHigh(), 0.0001);
            assertFalse(phase.getProjectionEnabled());
        }
    }

    @Test
    void findingsPriorityAllocation_drivesPhaseRanges() {
        var roi = calculator.compute(50.0, List.of(
                finding(OptimizationFinding.Priority.HIGH),
                finding(OptimizationFinding.Priority.HIGH),
                finding(OptimizationFinding.Priority.MEDIUM),
                finding(OptimizationFinding.Priority.LOW),
                finding(null)
        ));

        assertEquals(2, roi.getPhases().get(0).getPlannedOptimizationCount());
        assertEquals(1, roi.getPhases().get(1).getPlannedOptimizationCount());
        assertEquals(2, roi.getPhases().get(2).getPlannedOptimizationCount());

        assertEquals(54.0, roi.getPhases().get(0).getTargetScoreLow(), 0.0001);
        assertEquals(56.0, roi.getPhases().get(0).getTargetScoreHigh(), 0.0001);
        assertEquals(55.0, roi.getPhases().get(1).getTargetScoreLow(), 0.0001);
        assertEquals(58.0, roi.getPhases().get(1).getTargetScoreHigh(), 0.0001);
        assertEquals(56.0, roi.getTargetScoreLow(), 0.0001);
        assertEquals(60.0, roi.getTargetScoreHigh(), 0.0001);
        assertEquals(12.0, roi.getEstimatedUpliftPercentLow(), 0.0001);
        assertEquals(20.0, roi.getEstimatedUpliftPercentHigh(), 0.0001);
    }

    @Test
    void emptyMiddlePhase_reportsNoGainForThatPhase() {
        var roi = calculator.compute(60.0, List.of(
                finding(OptimizationFinding.Priority.HIGH),
                finding(OptimizationFinding.Priority.LOW)
        ));

        var phase2 = roi.getPhases().get(1);
        assertEquals(0, phase2.getPlannedOptimizationCount());
        assertEquals(0.0, phase2.getUpliftFromPreviousLow(), 0.0001);
        assertEquals(0.0, phase2.getUpliftFromPreviousHigh(), 0.0001);
        assertFalse(phase2.getProjectionEnabled());

        assertTrue(roi.getPhases().get(0).getProjectionEnabled());
        assertTrue(roi.getPhases().get(2).getProjectionEnabled());
    }

    @Test
    void nearCap_targetRangeCappedAtNinetyFive() {
        var roi = calculator.compute(94.0, List.of(
                finding(OptimizationFinding.Priority.HIGH),
                finding(OptimizationFinding.Priority.HIGH)
        ));

        assertEquals(95.0, roi.getTargetScoreLow(), 0.0001);
        assertEquals(95.0, roi.getTargetScoreHigh(), 0.0001);
        assertEquals(1.06, roi.getEstimatedUpliftPercentHigh(), 0.05);
    }

    @Test
    void manyFindings_totalUpliftUsesConservativeUpperBound() {
        var findings = java.util.stream.IntStream.range(0, 20)
                .mapToObj(i -> finding(OptimizationFinding.Priority.HIGH))
                .toList();

        var roi = calculator.compute(10.0, findings);

        assertEquals(40.0, roi.getTargetScoreHigh(), 0.0001);
    }

    private OptimizationFinding finding(OptimizationFinding.Priority priority) {
        OptimizationFinding f = new OptimizationFinding();
        f.setPriority(priority);
        return f;
    }
}
