package com.huanjing.geo.module.presale.generate.calc;

import com.huanjing.geo.module.presale.dto.snapshot.computed.OptimizationFinding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoiCalculatorTest {

    private final RoiCalculator calculator = new RoiCalculator();

    @Test
    void lowScore_case() {
        var roi = calculator.compute(45.0, List.of());
        assertEquals(50.0, roi.getPhases().get(0).getTargetScore(), 0.0001);
        assertEquals(57.0, roi.getPhases().get(1).getTargetScore(), 0.0001);
        assertEquals(65.0, roi.getPhases().get(2).getTargetScore(), 0.0001);
        assertEquals(5.0, roi.getPhases().get(0).getUpliftFromPrevious(), 0.0001);
        assertEquals(7.0, roi.getPhases().get(1).getUpliftFromPrevious(), 0.0001);
        assertEquals(8.0, roi.getPhases().get(2).getUpliftFromPrevious(), 0.0001);
        assertEquals(44.44, roi.getEstimatedUpliftPercent(), 0.05);
    }

    @Test
    void highScore_case() {
        var roi = calculator.compute(95.0, List.of());
        assertEquals(95.0, roi.getPhases().get(0).getTargetScore(), 0.0001);
        assertEquals(95.0, roi.getPhases().get(1).getTargetScore(), 0.0001);
        assertEquals(95.0, roi.getPhases().get(2).getTargetScore(), 0.0001);
        assertEquals(0.0, roi.getPhases().get(0).getUpliftFromPrevious(), 0.0001);
        assertEquals(0.0, roi.getPhases().get(1).getUpliftFromPrevious(), 0.0001);
        assertEquals(0.0, roi.getPhases().get(2).getUpliftFromPrevious(), 0.0001);
        assertEquals(0.0, roi.getEstimatedUpliftPercent(), 0.0001);
    }

    @Test
    void highButImprovableScore_case() {
        var roi = calculator.compute(88.0, List.of());
        assertEquals(90.0, roi.getPhases().get(0).getTargetScore(), 0.0001);
        assertEquals(92.0, roi.getPhases().get(1).getTargetScore(), 0.0001);
        assertEquals(94.0, roi.getPhases().get(2).getTargetScore(), 0.0001);
        assertEquals(2.0, roi.getPhases().get(0).getUpliftFromPrevious(), 0.0001);
        assertEquals(2.0, roi.getPhases().get(1).getUpliftFromPrevious(), 0.0001);
        assertEquals(2.0, roi.getPhases().get(2).getUpliftFromPrevious(), 0.0001);
        assertEquals(6.82, roi.getEstimatedUpliftPercent(), 0.05);
    }

    @Test
    void ninetyPlusScore_capsAtNinetyFive() {
        var roi = calculator.compute(92.0, List.of());
        assertEquals(93.0, roi.getPhases().get(0).getTargetScore(), 0.0001);
        assertEquals(94.0, roi.getPhases().get(1).getTargetScore(), 0.0001);
        assertEquals(95.0, roi.getPhases().get(2).getTargetScore(), 0.0001);
        assertEquals(3.26, roi.getEstimatedUpliftPercent(), 0.05);
    }

    @Test
    void zeroScore_case() {
        var roi = calculator.compute(0.0, List.of());
        assertEquals(5.0, roi.getPhases().get(0).getTargetScore(), 0.0001);
        assertEquals(12.0, roi.getPhases().get(1).getTargetScore(), 0.0001);
        assertEquals(20.0, roi.getPhases().get(2).getTargetScore(), 0.0001);
        assertEquals(0.0, roi.getEstimatedUpliftPercent(), 0.0001);
    }

    @Test
    void findingsPriorityAllocation_case() {
        var f1 = finding(OptimizationFinding.Priority.HIGH);
        var f2 = finding(OptimizationFinding.Priority.HIGH);
        var f3 = finding(OptimizationFinding.Priority.MEDIUM);
        var f4 = finding(OptimizationFinding.Priority.LOW);
        var f5 = finding(null);

        var roi = calculator.compute(50.0, List.of(f1, f2, f3, f4, f5));
        assertEquals(2, roi.getPhases().get(0).getTotalOptimizationCount());
        assertEquals(1, roi.getPhases().get(1).getTotalOptimizationCount());
        assertEquals(2, roi.getPhases().get(2).getTotalOptimizationCount());
    }

    private OptimizationFinding finding(OptimizationFinding.Priority priority) {
        OptimizationFinding f = new OptimizationFinding();
        f.setPriority(priority);
        return f;
    }
}
