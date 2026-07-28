package com.huanjing.geo.module.dispatch.websearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.util.EntityMatchTextNormalizer;
import com.huanjing.geo.module.dispatch.entity.PollCitation;
import com.huanjing.geo.module.dispatch.entity.PollInvocationAttempt;
import com.huanjing.geo.module.dispatch.entity.PollSearchSource;
import com.huanjing.geo.module.dispatch.mapper.PollCitationMapper;
import com.huanjing.geo.module.dispatch.mapper.PollInvocationAttemptMapper;
import com.huanjing.geo.module.dispatch.mapper.PollSearchSourceMapper;
import com.huanjing.geo.module.dispatch.websearch.classification.PollClassificationInput;
import com.huanjing.geo.module.dispatch.websearch.classification.PollResultClassifier;
import com.huanjing.geo.module.dispatch.websearch.enums.BrandMatchStrength;
import com.huanjing.geo.module.dispatch.websearch.enums.CitationConfidence;
import com.huanjing.geo.module.dispatch.websearch.enums.ErrorCategory;
import com.huanjing.geo.module.dispatch.websearch.enums.ResultCode;
import com.huanjing.geo.module.dispatch.websearch.model.WebSearchCitation;
import com.huanjing.geo.module.dispatch.websearch.model.WebSearchResponse;
import com.huanjing.geo.module.dispatch.websearch.model.WebSearchSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WebSearchAttemptResultWriter {

    private final PollInvocationAttemptMapper attemptMapper;
    private final PollSearchSourceMapper sourceMapper;
    private final PollCitationMapper citationMapper;
    private final PollResultClassifier classifier;
    private final ObjectMapper objectMapper;

    @Transactional
    public ResultCode writeSuccess(PollInvocationAttempt attempt,
                                   WebSearchResponse response,
                                   Set<String> brandNames,
                                   LocalDateTime now) {
        List<String> normalizedBrands = normalizeBrandNames(brandNames);
        List<PollSearchSource> persistedSources = persistSources(attempt.getId(), response.sources(), normalizedBrands, now);
        boolean brandInSearch = persistedSources.stream().anyMatch(
                source -> BrandMatchStrength.STRONG.name().equals(source.getBrandMatchStrength()));
        boolean brandInAnswer = stronglyMatches(response.answer(), normalizedBrands);
        CitationConfidence confidence = persistCitations(
                attempt.getId(), response.citations(), persistedSources, now);
        ResultCode resultCode = classifier.classify(new PollClassificationInput(
                true,
                true,
                StringUtils.hasText(response.answer()),
                response.searchStatus(),
                brandInSearch,
                brandInAnswer,
                confidence
        ));

        PollInvocationAttempt update = new PollInvocationAttempt();
        update.setId(attempt.getId());
        update.setCallStatus("SUCCEEDED");
        update.setResponseModelId(response.responseModelId());
        update.setSearchStatus(response.searchStatus().name());
        update.setSearchTriggered(response.searchTriggered());
        update.setGenerationSkipped(response.generationSkipped());
        update.setSearchEvidenceJson(writeJson(response.searchEvidence()));
        update.setAnswer(response.answer());
        update.setBrandInSearch(brandInSearch);
        update.setBrandInAnswer(brandInAnswer);
        update.setCitationConfidence(confidence.name());
        update.setResultCode(resultCode.name());
        update.setUsageJson(writeJson(response.usage()));
        update.setUpdatedAt(now);
        requireUpdated(attemptMapper.updateById(update), attempt.getId());
        return resultCode;
    }

    @Transactional
    public void writeFailure(PollInvocationAttempt attempt,
                             ErrorCategory category,
                             String errorCode,
                             String errorMessage,
                             LocalDateTime now) {
        PollInvocationAttempt update = new PollInvocationAttempt();
        update.setId(attempt.getId());
        update.setCallStatus("FAILED");
        update.setSearchStatus(com.huanjing.geo.module.dispatch.websearch.enums.SearchStatus.FAILED.name());
        update.setResultCode(ResultCode.R0.name());
        update.setErrorCategory(category.name());
        update.setErrorCode(errorCode);
        update.setErrorMessage(truncate(errorMessage, 2000));
        update.setUpdatedAt(now);
        requireUpdated(attemptMapper.updateById(update), attempt.getId());
    }

    private List<PollSearchSource> persistSources(Long attemptId,
                                                  List<WebSearchSource> sources,
                                                  List<String> normalizedBrands,
                                                  LocalDateTime now) {
        List<PollSearchSource> persisted = new ArrayList<>();
        for (WebSearchSource source : sources) {
            Set<String> matched = matchedBrands(sourceText(source), normalizedBrands);
            BrandMatchStrength strength = matched.isEmpty() ? BrandMatchStrength.NONE : BrandMatchStrength.STRONG;
            PollSearchSource entity = new PollSearchSource();
            entity.setAttemptId(attemptId);
            entity.setSearchEventIndex(source.searchEventIndex());
            entity.setRankNo(source.rank());
            entity.setQueryText(source.query());
            entity.setTitle(source.title());
            entity.setOriginalUrl(source.originalUrl());
            entity.setNormalizedUrl(source.normalizedUrl());
            entity.setDomain(source.domain());
            entity.setMedia(source.media());
            entity.setSnippet(source.snippet());
            entity.setPublishTime(source.publishTime());
            entity.setBrandMatched(!matched.isEmpty());
            entity.setBrandMatchStrength(strength.name());
            entity.setMatchedKeywordsJson(writeJson(matched));
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            sourceMapper.insert(entity);
            persisted.add(entity);
        }
        return persisted;
    }

    private CitationConfidence persistCitations(Long attemptId,
                                                List<WebSearchCitation> citations,
                                                List<PollSearchSource> sources,
                                                LocalDateTime now) {
        CitationConfidence aggregate = CitationConfidence.NONE;
        for (WebSearchCitation citation : citations) {
            PollSearchSource source = sourceAt(sources, citation.sourceOccurrenceIndex());
            boolean sourceStrong = source != null
                    && BrandMatchStrength.STRONG.name().equals(source.getBrandMatchStrength());
            CitationConfidence finalConfidence;
            String validationStatus;
            if (citation.confidence() == CitationConfidence.CONFIRMED && sourceStrong) {
                finalConfidence = CitationConfidence.CONFIRMED;
                validationStatus = "VALID_BRAND_STRONG";
            } else if (citation.confidence() != CitationConfidence.NONE && source != null) {
                finalConfidence = CitationConfidence.PROBABLE;
                validationStatus = sourceStrong ? "INCOMPLETE_CITATION" : "SOURCE_BRAND_NOT_STRONG";
            } else {
                finalConfidence = CitationConfidence.NONE;
                validationStatus = citation.validationStatus();
            }
            aggregate = stronger(aggregate, finalConfidence);

            PollCitation entity = new PollCitation();
            entity.setAttemptId(attemptId);
            entity.setSourceId(source == null ? null : source.getId());
            entity.setCitationIndex(citation.citationIndex());
            entity.setAnswerStart(citation.answerStart());
            entity.setAnswerEnd(citation.answerEnd());
            entity.setCitationText(citation.citationText());
            entity.setConfidence(finalConfidence.name());
            entity.setValidationStatus(StringUtils.hasText(validationStatus) ? validationStatus : "INVALID");
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            citationMapper.insert(entity);
        }
        return aggregate;
    }

    private PollSearchSource sourceAt(List<PollSearchSource> sources, Integer index) {
        return index == null || index < 0 || index >= sources.size() ? null : sources.get(index);
    }

    private CitationConfidence stronger(CitationConfidence left, CitationConfidence right) {
        return left.ordinal() >= right.ordinal() ? left : right;
    }

    private List<String> normalizeBrandNames(Set<String> brandNames) {
        if (brandNames == null || brandNames.isEmpty()) {
            return List.of();
        }
        return brandNames.stream()
                .map(EntityMatchTextNormalizer::normalize)
                .filter(value -> value.length() >= 2)
                .distinct()
                .toList();
    }

    private boolean stronglyMatches(String text, List<String> normalizedBrands) {
        return !matchedBrands(text, normalizedBrands).isEmpty();
    }

    private Set<String> matchedBrands(String text, List<String> normalizedBrands) {
        String normalizedText = EntityMatchTextNormalizer.normalize(text);
        if (normalizedText.isEmpty()) {
            return Set.of();
        }
        Set<String> matches = new LinkedHashSet<>();
        for (String brand : normalizedBrands) {
            if (normalizedText.contains(brand)) {
                matches.add(brand);
            }
        }
        return matches;
    }

    private String sourceText(WebSearchSource source) {
        return String.join(" ",
                value(source.title()), value(source.snippet()), value(source.domain()), value(source.originalUrl()));
    }

    private String value(String text) {
        return text == null ? "" : text;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize web-search audit data", ex);
        }
    }

    private void requireUpdated(int rows, Long attemptId) {
        if (rows != 1) {
            throw new IllegalStateException("Invocation attempt changed concurrently: " + attemptId);
        }
    }

    private String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
