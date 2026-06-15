package com.huanjing.geo.module.content.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ThirdPartySubjectPoolPreviewResponse(Long sourceBrandId,
                                                   String sourceBrandName,
                                                   List<String> coverableIndustries,
                                                   boolean includeAllIndustries,
                                                   boolean validSource,
                                                   int candidateCount,
                                                   int excludedCount,
                                                   int candidateDisplayCount,
                                                   int excludedDisplayCount,
                                                   List<Item> candidates,
                                                   List<Item> excluded) {

    public record Item(Long brandId,
                       String brandName,
                       String industry,
                       Long companyId,
                       String companyName,
                       Long subjectProjectId,
                       LocalDateTime lastSelectedAt,
                       String reasonCode,
                       String reason) {
    }
}
