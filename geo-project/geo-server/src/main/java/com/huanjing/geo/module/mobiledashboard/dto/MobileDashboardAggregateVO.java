package com.huanjing.geo.module.mobiledashboard.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class MobileDashboardAggregateVO {

    private MobileDashboardAggregateVO() {
    }

    @Data
    public static class TrendPoint {
        private LocalDate date;
        private Integer value;
    }

    @Data
    public static class PlatformMetric {
        private String code;
        private MobileDashboardMetricVO<Integer> rate;
        private Long completedCount;
        private Long mentionCount;
    }

    @Data
    public static class SceneMetric {
        private String code;
        private boolean visible = true;
        private MobileDashboardMetricVO<Long> covered;
        private MobileDashboardMetricVO<Long> total;
    }

    @Data
    public static class KeyMetric {
        private String key;
        private MobileDashboardMetricVO<?> metric;
    }

    @Data
    public static class EcoAssets {
        private MobileDashboardMetricVO<Long> totalAssets;
        private MobileDashboardMetricVO<Long> monthNew;
        private MobileDashboardMetricVO<Long> indexed;
        private MobileDashboardMetricVO<Long> coveredQuestions;
        private String indexMeasurementScope;
    }

    @Data
    public static class ContentProgress {
        private MobileDashboardMetricVO<Long> monthContent;
        private MobileDashboardMetricVO<Long> published;
        private MobileDashboardMetricVO<Long> indexed;
        private MobileDashboardMetricVO<Long> building;
        private String indexMeasurementScope;
    }

    @Data
    public static class CompetitorComparison {
        private boolean available;
        private String reason;
        private List<Object> rows = new ArrayList<>();
    }

    @Data
    public static class Home {
        private MobileDashboardMetricVO<Integer> overallMentionRate;
        private List<TrendPoint> trend = new ArrayList<>();
        private List<KeyMetric> metrics = new ArrayList<>();
        private List<PlatformMetric> platformPerformance = new ArrayList<>();
        private List<SceneMetric> sceneCoverage = new ArrayList<>();
        private CompetitorComparison competitorComparison;
        private ContentProgress contentProgress;
        private EcoAssets ecoAssets;
    }

    @Data
    public static class MonitorOverview {
        private MobileDashboardMetricVO<Long> monitoredQuestions;
        private MobileDashboardMetricVO<Long> brandMentioned;
        private MobileDashboardMetricVO<Integer> aiRecommendRate;
        private MobileDashboardMetricVO<Long> firstRecommendCount;
    }

    @Data
    public static class QuestionMonitorList {
        private boolean available;
        private String reason;
        private List<QuestionMonitorItem> items = new ArrayList<>();
    }

    @Data
    public static class QuestionMonitorItem {
        private Long pollResultId;
        private String platformCode;
        private List<String> platformCodes = new ArrayList<>();
        private String questionTitle;
        private LocalDateTime completedAt;
        private Boolean mentioned;
        private MobileDashboardMetricVO<Boolean> recommended;
        private MobileDashboardMetricVO<Boolean> firstRecommend;
        private MobileDashboardMetricVO<Integer> rankPosition;
        private String evidence;
        private String responseText;
        private List<String> tags = new ArrayList<>();
    }

    @Data
    public static class QuestionCoverageProgress {
        private MobileDashboardMetricVO<Long> covered;
        private MobileDashboardMetricVO<Long> monitoring;
        private MobileDashboardMetricVO<Long> building;
    }

    @Data
    public static class Monitor {
        private MonitorOverview overview;
        private List<String> platformFilters = new ArrayList<>();
        private QuestionMonitorList questionList;
        private List<SceneMetric> scenePerformance = new ArrayList<>();
        private QuestionCoverageProgress questionCoverage;
    }

    @Data
    public static class PlatformCompletion {
        private String code;
        private MobileDashboardMetricVO<Integer> completionRate;
        private Long published;
        private Long quota;
    }

    @Data
    public static class OwnedPublish {
        private String code;
        private MobileDashboardMetricVO<Long> published;
        private MobileDashboardMetricVO<Long> indexed;
    }

    @Data
    public static class TaskList {
        private boolean available;
        private String reason;
        private List<ContentTaskItem> items = new ArrayList<>();
    }

    @Data
    public static class ContentTaskItem {
        private Long draftId;
        private String title;
        private List<String> keywords = new ArrayList<>();
        private List<String> platformCodes = new ArrayList<>();
        private String publishUrl;
        private String status;
        private LocalDateTime date;
    }

    @Data
    public static class Content {
        private ContentProgress overview;
        private List<PlatformCompletion> platformCompletion = new ArrayList<>();
        private TaskList taskList;
        private List<OwnedPublish> ownedPublish = new ArrayList<>();
        private EcoAssets ecoAssets;
    }

    @Data
    public static class HighlightList {
        private boolean available;
        private String reason;
        private List<Object> items = new ArrayList<>();
    }

    @Data
    public static class DeliverySummary {
        private MobileDashboardMetricVO<Long> published;
        private MobileDashboardMetricVO<Long> assetNew;
        private MobileDashboardMetricVO<Long> indexed;
        private MobileDashboardMetricVO<Long> coveredQuestions;
        private String indexMeasurementScope;
    }

    @Data
    public static class Report {
        private MobileDashboardMetricVO<Integer> overallMentionRate;
        private List<TrendPoint> trend = new ArrayList<>();
        private List<KeyMetric> coreResults = new ArrayList<>();
        private HighlightList highlights;
        private DeliverySummary deliverySummary;
        private EcoAssets ecoAssets;
    }
}
