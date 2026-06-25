package com.huanjing.geo.common.llm.alert;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "geo.llm.capacity-alert")
public class LlmCapacityAlertProperties {
    private boolean enabled = true;
    private long scanFixedDelayMs = 120_000L;
    private int windowMinutes = 10;
    private int minCallsForRatio = 20;
    private double rateLimitRatioThreshold = 0.05D;
    private String recipientRole = "super_admin";
    private Hunyuan hunyuan = new Hunyuan();

    public void setScanFixedDelayMs(long scanFixedDelayMs) {
        this.scanFixedDelayMs = Math.max(30_000L, scanFixedDelayMs);
    }

    public void setWindowMinutes(int windowMinutes) {
        this.windowMinutes = Math.max(1, windowMinutes);
    }

    public void setMinCallsForRatio(int minCallsForRatio) {
        this.minCallsForRatio = Math.max(1, minCallsForRatio);
    }

    public void setRateLimitRatioThreshold(double rateLimitRatioThreshold) {
        if (Double.isNaN(rateLimitRatioThreshold) || Double.isInfinite(rateLimitRatioThreshold)) {
            this.rateLimitRatioThreshold = 0.05D;
            return;
        }
        this.rateLimitRatioThreshold = Math.max(0.0D, Math.min(1.0D, rateLimitRatioThreshold));
    }

    @Data
    public static class Hunyuan {
        private String platformCodes = "hunyuan,yuanbao";
        private int activePeakThreshold = 5;
        private int activePeakSustainedMinutes = 5;
        private double progressLagWarnRatio = 0.20D;
        private int progressLagSustainedMinutes = 30;
        private double finalCompletionCriticalRatio = 0.95D;
        private int sliceStartMinuteOfDay = 5;
        private int completionGraceMinutes = 60;

        public void setActivePeakThreshold(int activePeakThreshold) {
            this.activePeakThreshold = Math.max(1, activePeakThreshold);
        }

        public void setActivePeakSustainedMinutes(int activePeakSustainedMinutes) {
            this.activePeakSustainedMinutes = Math.max(1, activePeakSustainedMinutes);
        }

        public void setProgressLagWarnRatio(double progressLagWarnRatio) {
            if (Double.isNaN(progressLagWarnRatio) || Double.isInfinite(progressLagWarnRatio)) {
                this.progressLagWarnRatio = 0.20D;
                return;
            }
            this.progressLagWarnRatio = Math.max(0.0D, Math.min(1.0D, progressLagWarnRatio));
        }

        public void setProgressLagSustainedMinutes(int progressLagSustainedMinutes) {
            this.progressLagSustainedMinutes = Math.max(1, progressLagSustainedMinutes);
        }

        public void setFinalCompletionCriticalRatio(double finalCompletionCriticalRatio) {
            if (Double.isNaN(finalCompletionCriticalRatio) || Double.isInfinite(finalCompletionCriticalRatio)) {
                this.finalCompletionCriticalRatio = 0.95D;
                return;
            }
            this.finalCompletionCriticalRatio = Math.max(0.0D, Math.min(1.0D, finalCompletionCriticalRatio));
        }

        public void setSliceStartMinuteOfDay(int sliceStartMinuteOfDay) {
            this.sliceStartMinuteOfDay = Math.max(0, Math.min(1439, sliceStartMinuteOfDay));
        }

        public void setCompletionGraceMinutes(int completionGraceMinutes) {
            this.completionGraceMinutes = Math.max(0, completionGraceMinutes);
        }
    }
}
