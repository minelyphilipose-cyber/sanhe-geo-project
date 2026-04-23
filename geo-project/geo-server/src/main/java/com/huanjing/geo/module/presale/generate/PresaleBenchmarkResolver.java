package com.huanjing.geo.module.presale.generate;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.presale.dto.snapshot.common.MatchLevel;
import com.huanjing.geo.module.presale.dto.snapshot.common.ScoreSet;
import com.huanjing.geo.module.presale.dto.snapshot.raw.BenchmarksFrozen;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从 classpath JSON 加载 benchmark 映射并提供回退解析。
 */
@Component
public class PresaleBenchmarkResolver {

    private static final Logger log = LoggerFactory.getLogger(PresaleBenchmarkResolver.class);
    private static final String RESOURCE_PATH = "classpath:benchmarks/v1.json";
    private static final String ALL = "_ALL_";

    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final String resourcePath;

    private volatile Map<String, BenchmarkJsonEntry> entriesByKey = Collections.emptyMap();

    @Autowired
    public PresaleBenchmarkResolver(ObjectMapper objectMapper, ResourceLoader resourceLoader) {
        this(objectMapper, resourceLoader, RESOURCE_PATH);
    }

    PresaleBenchmarkResolver(ObjectMapper objectMapper, ResourceLoader resourceLoader, String resourcePath) {
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.resourcePath = resourcePath;
    }

    @PostConstruct
    void init() {
        this.entriesByKey = Collections.unmodifiableMap(loadAndIndex(resourcePath));
        log.info("loaded {} entries from {}", entriesByKey.size(), displayPath(resourcePath));
    }

    public BenchmarksFrozen resolve(String industry, String industryRole) {
        if (industry == null || industry.isBlank()) {
            log.warn("Resolve called with null/blank industry, falling back to _ALL_. "
                    + "This may indicate a Preflight validation gap.");
        }
        String normalizedIndustry = normalize(industry);
        String normalizedRole = normalize(industryRole);

        BenchmarkJsonEntry exact = entriesByKey.get(keyOf(normalizedIndustry, normalizedRole));
        if (exact != null) {
            return toBenchmarksFrozen(exact, MatchLevel.EXACT);
        }

        BenchmarkJsonEntry industryFallback = entriesByKey.get(keyOf(normalizedIndustry, ALL));
        if (industryFallback != null) {
            return toBenchmarksFrozen(industryFallback, MatchLevel.FALLBACK_INDUSTRY);
        }

        BenchmarkJsonEntry allFallback = entriesByKey.get(keyOf(ALL, ALL));
        if (allFallback != null) {
            return toBenchmarksFrozen(allFallback, MatchLevel.FALLBACK_INDUSTRY);
        }

        throw new IllegalStateException("Benchmark fallback entry (_ALL_, _ALL_) is missing");
    }

    private Map<String, BenchmarkJsonEntry> loadAndIndex(String path) {
        Resource resource = resourceLoader.getResource(path);
        if (!resource.exists()) {
            throw new IllegalStateException("Benchmark resource not found: " + path);
        }

        BenchmarkRoot root;
        try (InputStream inputStream = resource.getInputStream()) {
            root = objectMapper.readValue(inputStream, BenchmarkRoot.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load benchmark resource: " + path, e);
        }

        if (root == null || root.getEntries() == null || root.getEntries().isEmpty()) {
            throw new IllegalStateException("Benchmark entries is empty: " + path);
        }

        Map<String, BenchmarkJsonEntry> out = new LinkedHashMap<>();
        for (BenchmarkJsonEntry entry : root.getEntries()) {
            String k = keyOf(normalize(entry.getIndustry()), normalize(entry.getIndustryRole()));
            if (out.containsKey(k)) {
                throw new IllegalStateException("Duplicate benchmark key: " + k);
            }
            out.put(k, entry);
        }

        if (!out.containsKey(keyOf(ALL, ALL))) {
            throw new IllegalStateException("Benchmark fallback entry (_ALL_, _ALL_) is required");
        }
        return out;
    }

    private BenchmarksFrozen toBenchmarksFrozen(BenchmarkJsonEntry entry, MatchLevel matchLevel) {
        return BenchmarksFrozen.builder()
                .industry(entry.getIndustry())
                .industryRole(entry.getIndustryRole())
                .matchLevel(matchLevel)
                .industryAvg(entry.getIndustryAvg())
                .top1(entry.getTop1())
                .top10Score(entry.getTop10Score())
                .confidenceLevel(parseConfidenceLevel(entry.getConfidenceLevel()))
                .source(parseSource(entry.getSource()))
                .sampleSize(entry.getSampleSize())
                .industryRanking(entry.getIndustryRanking() == null ? null :
                        BenchmarksFrozen.IndustryRanking.builder()
                                .position(entry.getIndustryRanking().getPosition())
                                .total(entry.getIndustryRanking().getTotal())
                                .build())
                .build();
    }

    private BenchmarksFrozen.ConfidenceLevel parseConfidenceLevel(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return BenchmarksFrozen.ConfidenceLevel.valueOf(value);
    }

    private BenchmarksFrozen.Source parseSource(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return BenchmarksFrozen.Source.valueOf(value);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }
        return value.trim();
    }

    private String keyOf(String industry, String industryRole) {
        return industry + "||" + industryRole;
    }

    private String displayPath(String path) {
        if (path == null) {
            return "";
        }
        if (path.startsWith("classpath:")) {
            return path.substring("classpath:".length());
        }
        return path;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    static class BenchmarkRoot {
        private String version;
        private String description;
        private List<BenchmarkJsonEntry> entries;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    static class BenchmarkJsonEntry {
        private String industry;

        @JsonProperty("industryRole")
        @JsonAlias("industry_role")
        private String industryRole;

        @JsonProperty("industryAvg")
        @JsonAlias("industry_avg")
        private ScoreSet industryAvg;

        private ScoreSet top1;

        @JsonProperty("top10Score")
        @JsonAlias("top10_score")
        private Double top10Score;

        @JsonProperty("confidenceLevel")
        @JsonAlias("confidence_level")
        private String confidenceLevel;

        private String source;

        @JsonProperty("sampleSize")
        @JsonAlias("sample_size")
        private Integer sampleSize;

        @JsonProperty("industryRanking")
        @JsonAlias("industry_ranking")
        private IndustryRankingJson industryRanking;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    static class IndustryRankingJson {
        private Integer position;
        private Integer total;
    }
}
