package com.huanjing.geo.module.presale.generate;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.presale.dto.snapshot.common.MatchLevel;
import com.huanjing.geo.module.presale.dto.snapshot.common.ScoreSet;
import com.huanjing.geo.module.presale.dto.snapshot.raw.BenchmarksFrozen;
import com.huanjing.geo.module.presale.persist.entity.PresaleBenchmark;
import com.huanjing.geo.module.presale.persist.mapper.PresaleBenchmarkMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import java.time.LocalDate;

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
    private final PresaleBenchmarkMapper benchmarkMapper;

    private volatile Map<String, BenchmarkJsonEntry> entriesByKey = Collections.emptyMap();

    @Autowired
    public PresaleBenchmarkResolver(ObjectMapper objectMapper,
                                    ResourceLoader resourceLoader,
                                    PresaleBenchmarkMapper benchmarkMapper) {
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.resourcePath = RESOURCE_PATH;
        this.benchmarkMapper = benchmarkMapper;
    }

    /** 仅供资源兼容测试使用；生产运行时始终注入数据库 Mapper。 */
    PresaleBenchmarkResolver(ObjectMapper objectMapper, ResourceLoader resourceLoader, String resourcePath) {
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.resourcePath = resourcePath;
        this.benchmarkMapper = null;
    }

    /** 仅供既有资源兼容测试使用。 */
    PresaleBenchmarkResolver(ObjectMapper objectMapper, ResourceLoader resourceLoader) {
        this(objectMapper, resourceLoader, RESOURCE_PATH);
    }

    @PostConstruct
    void init() {
        if (benchmarkMapper != null) {
            log.info("Presale benchmark resolver uses database-backed manual configuration");
            return;
        }
        this.entriesByKey = Collections.unmodifiableMap(loadAndIndex(resourcePath));
        log.info("loaded {} entries from {}", entriesByKey.size(), displayPath(resourcePath));
    }

    public BenchmarksFrozen resolve(String industry, String industryRole) {
        return resolve(industry, industryRole, LocalDate.now());
    }

    public BenchmarksFrozen resolve(String industry, String industryRole, LocalDate generationDate) {
        if (benchmarkMapper != null) {
            return resolveFromDatabase(industry, industryRole, generationDate);
        }
        if (industry == null || industry.isBlank()) {
            log.warn("Resolve called with null/blank industry, falling back to _ALL_. "
                    + "This may indicate a Preflight validation gap.");
        }
        String normalizedIndustry = normalize(industry);
        String normalizedRole = normalize(industryRole);

        BenchmarkJsonEntry exact = entriesByKey.get(keyOf(normalizedIndustry, normalizedRole));
        if (exact != null) {
            return resolveWithLog(industry, industryRole, exact, MatchLevel.EXACT);
        }

        BenchmarkJsonEntry industryFallback = entriesByKey.get(keyOf(normalizedIndustry, ALL));
        if (industryFallback != null) {
            MatchLevel level = ALL.equals(normalizedIndustry)
                    ? MatchLevel.FALLBACK_GLOBAL : MatchLevel.FALLBACK_INDUSTRY;
            return resolveWithLog(industry, industryRole, industryFallback, level);
        }

        BenchmarkJsonEntry allFallback = entriesByKey.get(keyOf(ALL, ALL));
        if (allFallback != null) {
            return resolveWithLog(industry, industryRole, allFallback, MatchLevel.FALLBACK_GLOBAL);
        }

        return missingBenchmark();
    }

    BenchmarksFrozen resolveFromDatabase(String industry, String industryRole, LocalDate generationDate) {
        String normalizedIndustry = normalize(industry);
        String normalizedRole = normalize(industryRole);
        LocalDate effectiveDate = generationDate == null ? LocalDate.now() : generationDate;

        PresaleBenchmark exact = selectLatest(normalizedIndustry, normalizedRole, effectiveDate);
        if (exact != null) {
            return toBenchmarksFrozen(exact, MatchLevel.EXACT);
        }
        PresaleBenchmark industryFallback = selectLatest(normalizedIndustry, ALL, effectiveDate);
        if (industryFallback != null) {
            MatchLevel level = ALL.equals(normalizedIndustry)
                    ? MatchLevel.FALLBACK_GLOBAL : MatchLevel.FALLBACK_INDUSTRY;
            return toBenchmarksFrozen(industryFallback, level);
        }
        PresaleBenchmark globalFallback = selectLatest(ALL, ALL, effectiveDate);
        if (globalFallback != null) {
            return toBenchmarksFrozen(globalFallback, MatchLevel.FALLBACK_GLOBAL);
        }
        log.warn("No enabled presale benchmark found for industry={}, role={}, date={}",
                industry, industryRole, effectiveDate);
        return missingBenchmark();
    }

    private PresaleBenchmark selectLatest(String industry, String industryRole, LocalDate effectiveDate) {
        return benchmarkMapper.selectOne(new LambdaQueryWrapper<PresaleBenchmark>()
                .eq(PresaleBenchmark::getIndustry, industry)
                .eq(PresaleBenchmark::getIndustryRole, industryRole)
                .eq(PresaleBenchmark::getEnabled, true)
                .le(PresaleBenchmark::getEffectiveFrom, effectiveDate)
                .orderByDesc(PresaleBenchmark::getEffectiveFrom, PresaleBenchmark::getId)
                .last("LIMIT 1"));
    }

    private BenchmarksFrozen resolveWithLog(String industry,
                                            String industryRole,
                                            BenchmarkJsonEntry entry,
                                            MatchLevel matchLevel) {
        BenchmarksFrozen resolved = toBenchmarksFrozen(entry, matchLevel);
        log.info("Presale benchmark resolved: industry={}, industryRole={}, matchLevel={}",
                industry, industryRole, matchLevel);
        return resolved;
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
                .available(true)
                .dataVersion("resource-v1.0")
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

    private BenchmarksFrozen toBenchmarksFrozen(PresaleBenchmark entry, MatchLevel matchLevel) {
        return BenchmarksFrozen.builder()
                .available(true)
                .benchmarkId(entry.getId())
                .effectiveFrom(entry.getEffectiveFrom() == null ? null : entry.getEffectiveFrom().toString())
                .dataVersion(entry.getId() == null ? null : "manual-" + entry.getId())
                .industry(entry.getIndustry())
                .industryRole(entry.getIndustryRole())
                .matchLevel(matchLevel)
                .industryAvg(ScoreSet.builder()
                        .overall(value(entry.getAvgOverall()))
                        .mention(value(entry.getAvgMention()))
                        .ranking(value(entry.getAvgRanking()))
                        .sentiment(value(entry.getAvgSentiment()))
                        .coverage(value(entry.getAvgCoverage()))
                        .build())
                .top1(ScoreSet.builder()
                        .overall(value(entry.getTop1Overall()))
                        .mention(value(entry.getTop1Mention()))
                        .ranking(value(entry.getTop1Ranking()))
                        .sentiment(value(entry.getTop1Sentiment()))
                        .coverage(value(entry.getTop1Coverage()))
                        .build())
                .top10Score(value(entry.getTop10Score()))
                .confidenceLevel(parseConfidenceLevel(entry.getConfidenceLevel()))
                .source(BenchmarksFrozen.Source.MANUAL)
                .sampleSize(entry.getSampleSize())
                .build();
    }

    private BenchmarksFrozen missingBenchmark() {
        return BenchmarksFrozen.builder()
                .available(false)
                .matchLevel(MatchLevel.MISSING)
                .build();
    }

    private Double value(java.math.BigDecimal value) {
        return value == null ? null : value.doubleValue();
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
