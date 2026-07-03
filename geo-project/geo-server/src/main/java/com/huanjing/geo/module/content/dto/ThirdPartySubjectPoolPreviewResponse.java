package com.huanjing.geo.module.content.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ThirdPartySubjectPoolPreviewResponse(Long sourceBrandId,
                                                   String sourceBrandName,
                                                   List<String> coverableIndustries,
                                                   boolean includeAllIndustries,
                                                   boolean validSource,
                                                   boolean confirmed,
                                                   java.time.LocalDateTime lastConfirmedAt,
                                                   boolean llmFailed,
                                                   String llmFailureMessage,
                                                   int candidateCount,
                                                   int excludedCount,
                                                   int unavailableCount,
                                                   int confirmedCount,
                                                   int candidateDisplayCount,
                                                   int excludedDisplayCount,
                                                   List<Item> candidates,
                                                   List<Item> excluded,
                                                   List<Item> availableSubjects) {

    public record Item(Long brandId,
                       String brandName,
                       String industry,
                       Long companyId,
                       String companyName,
                       Long subjectProjectId,
                       LocalDateTime lastSelectedAt,
                       Boolean available,
                       String matchSource,
                       String matchedIndustry,
                       String reasonCode,
                       String reason) {
    }
}
