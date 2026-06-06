package com.huanjing.geo.module.presale.dto.response;

import com.huanjing.geo.module.presale.persist.entity.PresaleHeatmapSummary;
import com.huanjing.geo.module.presale.persist.entity.PresaleIndustryBucketMapping;
import com.huanjing.geo.module.presale.persist.entity.PresaleIndustryBucketReviewTask;
import com.huanjing.geo.module.presale.persist.entity.PresaleLexiconBucket;
import com.huanjing.geo.module.presale.persist.entity.PresaleNarrativeFindingCopy;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PresaleNarrativeConfigAdminResponse {
    private String configVersion;
    private List<PresaleNarrativeFindingCopy> findingCopies;
    private List<PresaleHeatmapSummary> heatmapSummaries;
    private List<PresaleLexiconBucket> lexiconBuckets;
    private List<PresaleIndustryBucketMapping> industryBucketMappings;
    private List<PresaleIndustryBucketReviewTask> lexiconReviewTasks;
}
