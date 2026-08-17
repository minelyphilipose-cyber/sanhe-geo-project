package com.huanjing.geo.module.retention.detailcleanup;

import com.huanjing.geo.module.retention.detailcleanup.DataRetentionDetailCleanupModels.ReadinessCode;
import com.huanjing.geo.module.retention.detailcleanup.DataRetentionDetailCleanupModels.ReadinessEvaluation;
import org.springframework.stereotype.Service;

@Service
public class DataRetentionDetailCleanupPromotionGate {
    private final DataRetentionDetailCleanupReadinessService readiness;
    private final DataRetentionDetailCleanupSafetyConverger converger;

    public DataRetentionDetailCleanupPromotionGate(DataRetentionDetailCleanupReadinessService readiness,
                                                   DataRetentionDetailCleanupSafetyConverger converger) {
        this.readiness = readiness; this.converger = converger;
    }

    public ReadinessEvaluation evaluateAndConverge() {
        ReadinessEvaluation evaluation = readiness.evaluate();
        if (evaluation.requiresDisable()) converger.converge(evaluation);
        return evaluation;
    }

    public static boolean infrastructure(ReadinessCode code) {
        return code == ReadinessCode.MIGRATION_NOT_READY || code == ReadinessCode.READINESS_QUERY_FAILED;
    }
}
