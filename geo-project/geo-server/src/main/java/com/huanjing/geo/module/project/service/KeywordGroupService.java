package com.huanjing.geo.module.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.project.dto.KeywordGroupColumnsRequest;
import com.huanjing.geo.module.project.dto.KeywordGroupColumnsVO;
import com.huanjing.geo.module.project.dto.KeywordGroupPayloadRequest;
import com.huanjing.geo.module.project.dto.KeywordGroupVO;
import com.huanjing.geo.module.project.dto.KeywordPreviewVO;
import com.huanjing.geo.module.project.dto.KeywordWordItemRequest;
import com.huanjing.geo.module.project.dto.KeywordWordItemVO;
import com.huanjing.geo.module.project.entity.KeywordGroup;
import com.huanjing.geo.module.project.entity.KeywordGroupResult;
import com.huanjing.geo.module.project.entity.KeywordGroupWord;
import com.huanjing.geo.module.project.mapper.KeywordGroupMapper;
import com.huanjing.geo.module.project.mapper.KeywordGroupResultMapper;
import com.huanjing.geo.module.project.mapper.KeywordGroupWordMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import com.huanjing.geo.module.system.service.KeywordAffixWordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KeywordGroupService {

    private static final int MAX_GENERATION = 1000;
    private static final Set<String> COLUMN_TYPE_SET = Set.of("region", "prefix", "core", "industry", "suffix");
    private static final Set<String> SOURCE_SET = Set.of("system", "custom");

    private final KeywordGroupMapper keywordGroupMapper;
    private final KeywordGroupResultMapper keywordGroupResultMapper;
    private final KeywordGroupWordMapper keywordGroupWordMapper;
    private final CompanyMapper companyMapper;
    private final CurrentUserService currentUserService;
    private final KeywordAffixWordService keywordAffixWordService;

    public Page<KeywordGroupVO> page(long current, long size, String keyword, Long companyId, String type) {
        currentUserService.ensurePermission("keyword_group.read");
        LambdaQueryWrapper<KeywordGroup> wrapper = new LambdaQueryWrapper<KeywordGroup>()
                .orderByDesc(KeywordGroup::getUpdatedAt)
                .orderByDesc(KeywordGroup::getId);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(KeywordGroup::getName, keyword.trim());
        }
        if (companyId != null) {
            wrapper.eq(KeywordGroup::getCompanyId, companyId);
        }
        if (StringUtils.hasText(type)) {
            wrapper.eq(KeywordGroup::getType, normalizeType(type));
        }
        Page<KeywordGroup> page = keywordGroupMapper.selectPage(new Page<>(current, size), wrapper);
        List<Long> groupIds = page.getRecords().stream().map(KeywordGroup::getId).toList();
        Map<Long, String> companyNameMap = buildCompanyNameMap(page.getRecords().stream().map(KeywordGroup::getCompanyId).toList());
        Map<Long, Long> estimatedCountMap = calcEstimatedCountsByGroupIds(groupIds);
        Map<Long, Long> savedCountMap = calcSavedCountsByGroupIds(groupIds);

        Page<KeywordGroupVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream()
                .map(group -> toSummaryVO(
                        group,
                        companyNameMap.get(group.getCompanyId()),
                        estimatedCountMap.get(group.getId()),
                        savedCountMap.get(group.getId())
                ))
                .toList());
        return result;
    }

    public KeywordGroupVO detail(Long id) {
        currentUserService.ensurePermission("keyword_group.read");
        KeywordGroup group = requireGroup(id);
        String companyName = resolveCompanyName(group.getCompanyId());
        Long estimatedCount = calcEstimatedCountsByGroupIds(List.of(group.getId())).getOrDefault(group.getId(), 0L);
        Long savedCount = calcSavedCountsByGroupIds(List.of(group.getId())).getOrDefault(group.getId(), 0L);
        return toDetailVO(group, companyName, estimatedCount, savedCount);
    }

    @Transactional
    public KeywordGroupVO create(KeywordGroupPayloadRequest req) {
        currentUserService.ensurePermission("keyword_group.write");
        if (!StringUtils.hasText(req.getName())) {
            throw new BizException(400, "name is required");
        }

        KeywordGroup group = new KeywordGroup();
        Company company = requireCompany(req.getCompanyId());
        group.setCompanyId(company.getId());
        group.setName(req.getName().trim());
        group.setType(normalizeType(req.getType()));
        group.setRemark(StringUtils.hasText(req.getRemark()) ? req.getRemark().trim() : null);
        keywordGroupMapper.insert(group);

        List<KeywordGroupWord> words = normalizeWordsForPersist(group.getId(), req.getColumns());
        List<String> candidateKeywords = buildCandidateKeywords(req.getColumns());
        List<String> resultKeywords = normalizeResultKeywordsForPersist(req, candidateKeywords);
        saveWords(group.getId(), words);
        saveResults(group.getId(), resultKeywords);
        return toDetailVO(requireGroup(group.getId()), company.getCompanyName(), calcEstimatedByWords(words), (long) resultKeywords.size());
    }

    @Transactional
    public KeywordGroupVO update(Long id, KeywordGroupPayloadRequest req) {
        currentUserService.ensurePermission("keyword_group.write");
        if (!StringUtils.hasText(req.getName())) {
            throw new BizException(400, "name is required");
        }
        KeywordGroup group = requireGroup(id);
        Company company = requireCompany(req.getCompanyId());
        group.setCompanyId(company.getId());
        group.setName(req.getName().trim());
        group.setType(normalizeType(req.getType()));
        group.setRemark(StringUtils.hasText(req.getRemark()) ? req.getRemark().trim() : null);
        keywordGroupMapper.updateById(group);

        List<KeywordGroupWord> words = normalizeWordsForPersist(id, req.getColumns());
        List<String> candidateKeywords = buildCandidateKeywords(req.getColumns());
        List<String> resultKeywords = normalizeResultKeywordsForPersist(req, candidateKeywords);
        saveWords(id, words);
        saveResults(id, resultKeywords);
        return toDetailVO(requireGroup(id), company.getCompanyName(), calcEstimatedByWords(words), (long) resultKeywords.size());
    }

    @Transactional
    public void delete(Long id) {
        currentUserService.ensurePermission("keyword_group.write");
        requireGroup(id);
        keywordGroupResultMapper.delete(new LambdaQueryWrapper<KeywordGroupResult>().eq(KeywordGroupResult::getGroupId, id));
        keywordGroupWordMapper.delete(new LambdaQueryWrapper<KeywordGroupWord>().eq(KeywordGroupWord::getGroupId, id));
        keywordGroupMapper.deleteById(id);
    }

    public KeywordPreviewVO preview(KeywordGroupPayloadRequest req) {
        currentUserService.ensurePermission("keyword_group.read");
        normalizeType(req.getType());
        int count = req.getCount() == null ? MAX_GENERATION : req.getCount();
        if (count <= 0) {
            throw new BizException(400, "count must be > 0");
        }

        List<String> regionWords = normalizeWordsForPreview(req.getColumns() == null ? null : req.getColumns().getRegionWords());
        List<String> prefixWords = normalizeWordsForPreview(req.getColumns() == null ? null : req.getColumns().getPrefixWords());
        List<String> coreWords = normalizeWordsForPreview(req.getColumns() == null ? null : req.getColumns().getCoreWords());
        List<String> industryWords = normalizeWordsForPreview(req.getColumns() == null ? null : req.getColumns().getIndustryWords());
        List<String> suffixWords = normalizeWordsForPreview(req.getColumns() == null ? null : req.getColumns().getSuffixWords());

        long totalEstimated = calcTotalEstimated(regionWords, prefixWords, coreWords, industryWords, suffixWords);
        if (totalEstimated > MAX_GENERATION) {
            throw new BizException(400, "预计生成 " + totalEstimated + " 条，超过上限 " + MAX_GENERATION + "，请减少选词");
        }

        List<String> keywords = buildKeywords(regionWords, prefixWords, coreWords, industryWords, suffixWords);
        Collections.shuffle(keywords);
        int limit = Math.min(Math.max(1, count), keywords.size());

        KeywordPreviewVO vo = new KeywordPreviewVO();
        vo.setTotalEstimated(totalEstimated);
        vo.setTotalAvailable(keywords.size());
        vo.setTotalGenerated(limit);
        vo.setKeywords(new ArrayList<>(keywords.subList(0, limit)));
        return vo;
    }

    public Map<Long, Long> calcEstimatedCountsByGroupIds(List<Long> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<KeywordGroupWord> words = keywordGroupWordMapper.selectList(
                new LambdaQueryWrapper<KeywordGroupWord>()
                        .in(KeywordGroupWord::getGroupId, groupIds)
        );
        Map<Long, Map<String, Integer>> countMap = new HashMap<>();
        for (KeywordGroupWord word : words) {
            countMap.computeIfAbsent(word.getGroupId(), k -> new HashMap<>())
                    .merge(word.getColumnType(), 1, Integer::sum);
        }
        Map<Long, Long> result = new HashMap<>();
        for (Long groupId : groupIds) {
            Map<String, Integer> columnCount = countMap.getOrDefault(groupId, Map.of());
            long estimated = 1L
                    * Math.max(1, columnCount.getOrDefault("region", 0))
                    * Math.max(1, columnCount.getOrDefault("prefix", 0))
                    * Math.max(1, columnCount.getOrDefault("core", 0))
                    * Math.max(1, columnCount.getOrDefault("industry", 0))
                    * Math.max(1, columnCount.getOrDefault("suffix", 0));
            result.put(groupId, estimated);
        }
        return result;
    }

    public Map<Long, Long> calcSavedCountsByGroupIds(List<Long> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<KeywordGroupResult> results = keywordGroupResultMapper.selectList(
                new LambdaQueryWrapper<KeywordGroupResult>()
                        .select(KeywordGroupResult::getGroupId)
                        .in(KeywordGroupResult::getGroupId, groupIds)
        );
        Map<Long, Long> countMap = new HashMap<>();
        for (KeywordGroupResult result : results) {
            countMap.merge(result.getGroupId(), 1L, Long::sum);
        }
        Map<Long, Long> finalMap = new HashMap<>();
        for (Long groupId : groupIds) {
            finalMap.put(groupId, countMap.getOrDefault(groupId, 0L));
        }
        return finalMap;
    }

    private List<KeywordGroupWord> normalizeWordsForPersist(Long groupId, KeywordGroupColumnsRequest columns) {
        Map<String, List<KeywordWordItemRequest>> sourceMap = new LinkedHashMap<>();
        sourceMap.put("region", columns == null ? null : columns.getRegionWords());
        sourceMap.put("prefix", columns == null ? null : columns.getPrefixWords());
        sourceMap.put("core", columns == null ? null : columns.getCoreWords());
        sourceMap.put("industry", columns == null ? null : columns.getIndustryWords());
        sourceMap.put("suffix", columns == null ? null : columns.getSuffixWords());

        List<KeywordGroupWord> result = new ArrayList<>();
        for (Map.Entry<String, List<KeywordWordItemRequest>> entry : sourceMap.entrySet()) {
            String columnType = entry.getKey();
            if (!COLUMN_TYPE_SET.contains(columnType)) {
                continue;
            }
            List<PreparedWord> normalized = normalizeItems(entry.getValue());
            for (int i = 0; i < normalized.size(); i++) {
                PreparedWord item = normalized.get(i);
                KeywordGroupWord word = new KeywordGroupWord();
                word.setGroupId(groupId);
                word.setColumnType(columnType);
                word.setWordText(item.wordText());
                word.setSource(item.source());
                word.setSortOrder(item.sortOrder() != null ? item.sortOrder() : ((i + 1) * 10));
                result.add(word);
            }
        }
        return result;
    }

    private void saveWords(Long groupId, List<KeywordGroupWord> words) {
        keywordGroupWordMapper.delete(new LambdaQueryWrapper<KeywordGroupWord>().eq(KeywordGroupWord::getGroupId, groupId));
        for (KeywordGroupWord word : words) {
            keywordGroupWordMapper.insert(word);
        }
    }

    private void saveResults(Long groupId, List<String> keywords) {
        keywordGroupResultMapper.delete(new LambdaQueryWrapper<KeywordGroupResult>().eq(KeywordGroupResult::getGroupId, groupId));
        for (int i = 0; i < keywords.size(); i++) {
            KeywordGroupResult result = new KeywordGroupResult();
            result.setGroupId(groupId);
            result.setKeywordText(keywords.get(i));
            result.setSortOrder((i + 1) * 10);
            keywordGroupResultMapper.insert(result);
        }
    }

    private KeywordGroup requireGroup(Long id) {
        KeywordGroup group = keywordGroupMapper.selectById(id);
        if (group == null) {
            throw new BizException(404, "Keyword group not found");
        }
        return group;
    }

    private KeywordGroupVO toSummaryVO(KeywordGroup group, String companyName, Long estimatedCount, Long savedCount) {
        KeywordGroupVO vo = new KeywordGroupVO();
        vo.setId(group.getId());
        vo.setCompanyId(group.getCompanyId());
        vo.setCompanyName(companyName);
        vo.setName(group.getName());
        vo.setType(group.getType());
        vo.setRemark(group.getRemark());
        vo.setEstimatedKeywordCount(estimatedCount == null ? 0L : estimatedCount);
        vo.setSavedKeywordCount(savedCount == null ? 0L : savedCount);
        vo.setCreatedAt(group.getCreatedAt());
        vo.setUpdatedAt(group.getUpdatedAt());
        return vo;
    }

    private KeywordGroupVO toDetailVO(KeywordGroup group, String companyName, Long estimatedCount, Long savedCount) {
        KeywordGroupVO vo = toSummaryVO(group, companyName, estimatedCount, savedCount);
        List<KeywordGroupWord> words = keywordGroupWordMapper.selectList(
                new LambdaQueryWrapper<KeywordGroupWord>()
                        .eq(KeywordGroupWord::getGroupId, group.getId())
                        .orderByAsc(KeywordGroupWord::getColumnType)
                        .orderByAsc(KeywordGroupWord::getSortOrder)
                        .orderByAsc(KeywordGroupWord::getId)
        );

        Map<String, List<KeywordWordItemVO>> map = new HashMap<>();
        for (String column : COLUMN_TYPE_SET) {
            map.put(column, new ArrayList<>());
        }
        for (KeywordGroupWord word : words) {
            KeywordWordItemVO item = new KeywordWordItemVO();
            item.setId(word.getId());
            item.setWordText(word.getWordText());
            item.setSource(word.getSource());
            item.setSortOrder(word.getSortOrder());
            map.computeIfAbsent(word.getColumnType(), k -> new ArrayList<>()).add(item);
        }

        KeywordGroupColumnsVO columnsVO = new KeywordGroupColumnsVO();
        columnsVO.setRegionWords(map.getOrDefault("region", List.of()));
        columnsVO.setPrefixWords(map.getOrDefault("prefix", List.of()));
        columnsVO.setCoreWords(map.getOrDefault("core", List.of()));
        columnsVO.setIndustryWords(map.getOrDefault("industry", List.of()));
        columnsVO.setSuffixWords(map.getOrDefault("suffix", List.of()));
        vo.setColumns(columnsVO);
        return vo;
    }

    private long calcEstimatedByWords(List<KeywordGroupWord> words) {
        Map<String, Integer> columnCount = new HashMap<>();
        for (KeywordGroupWord word : words) {
            columnCount.merge(word.getColumnType(), 1, Integer::sum);
        }
        return 1L
                * Math.max(1, columnCount.getOrDefault("region", 0))
                * Math.max(1, columnCount.getOrDefault("prefix", 0))
                * Math.max(1, columnCount.getOrDefault("core", 0))
                * Math.max(1, columnCount.getOrDefault("industry", 0))
                * Math.max(1, columnCount.getOrDefault("suffix", 0));
    }

    private Map<Long, String> buildCompanyNameMap(List<Long> companyIds) {
        List<Long> validIds = companyIds.stream().filter(id -> id != null && id > 0).distinct().toList();
        if (validIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return companyMapper.selectList(
                new LambdaQueryWrapper<Company>()
                        .select(Company::getId, Company::getCompanyName)
                        .in(Company::getId, validIds)
        ).stream().collect(Collectors.toMap(Company::getId, Company::getCompanyName, (a, b) -> a));
    }

    private String resolveCompanyName(Long companyId) {
        if (companyId == null) {
            return null;
        }
        Company company = companyMapper.selectById(companyId);
        return company == null ? null : company.getCompanyName();
    }

    private Company requireCompany(Long companyId) {
        Company company = companyMapper.selectById(companyId);
        if (company == null) {
            throw new BizException(404, "Company not found");
        }
        return company;
    }

    private String normalizeType(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new BizException(400, "type is required");
        }
        String type = raw.trim().toLowerCase(Locale.ROOT);
        keywordAffixWordService.ensureTypeExists(type, true);
        return type;
    }

    private List<PreparedWord> normalizeItems(List<KeywordWordItemRequest> input) {
        List<PreparedWord> rawItems = new ArrayList<>();
        if (input != null) {
            for (int i = 0; i < input.size(); i++) {
                KeywordWordItemRequest req = input.get(i);
                if (req == null || !StringUtils.hasText(req.getWordText())) {
                    continue;
                }
                String source = StringUtils.hasText(req.getSource()) ? req.getSource().trim().toLowerCase(Locale.ROOT) : "custom";
                if (!SOURCE_SET.contains(source)) {
                    source = "custom";
                }
                rawItems.add(new PreparedWord(req.getWordText().trim(), source, req.getSortOrder(), i));
            }
        }
        rawItems.sort((a, b) -> {
            int sortCmp = Integer.compare(a.sortOrder() == null ? Integer.MAX_VALUE : a.sortOrder(), b.sortOrder() == null ? Integer.MAX_VALUE : b.sortOrder());
            if (sortCmp != 0) {
                return sortCmp;
            }
            return Integer.compare(a.index(), b.index());
        });

        LinkedHashMap<String, PreparedWord> dedup = new LinkedHashMap<>();
        for (PreparedWord item : rawItems) {
            dedup.putIfAbsent(item.wordText(), item);
        }
        return new ArrayList<>(dedup.values());
    }

    private List<String> normalizeWordsForPreview(List<KeywordWordItemRequest> input) {
        return normalizeItems(input).stream().map(PreparedWord::wordText).toList();
    }

    private List<String> buildCandidateKeywords(KeywordGroupColumnsRequest columns) {
        List<String> regionWords = normalizeWordsForPreview(columns == null ? null : columns.getRegionWords());
        List<String> prefixWords = normalizeWordsForPreview(columns == null ? null : columns.getPrefixWords());
        List<String> coreWords = normalizeWordsForPreview(columns == null ? null : columns.getCoreWords());
        List<String> industryWords = normalizeWordsForPreview(columns == null ? null : columns.getIndustryWords());
        List<String> suffixWords = normalizeWordsForPreview(columns == null ? null : columns.getSuffixWords());

        long totalEstimated = calcTotalEstimated(regionWords, prefixWords, coreWords, industryWords, suffixWords);
        if (totalEstimated > MAX_GENERATION) {
            throw new BizException(400, "预计生成 " + totalEstimated + " 条，超过上限 " + MAX_GENERATION + "，请减少选词");
        }
        return buildKeywords(regionWords, prefixWords, coreWords, industryWords, suffixWords);
    }

    private List<String> normalizeResultKeywordsForPersist(KeywordGroupPayloadRequest req, List<String> candidateKeywords) {
        if (req.getResultKeywords() == null || req.getResultKeywords().isEmpty()) {
            throw new BizException(400, "resultKeywords is required");
        }

        int count = req.getCount() == null ? MAX_GENERATION : req.getCount();
        if (count <= 0) {
            throw new BizException(400, "count must be > 0");
        }

        int expectedSize = Math.min(Math.max(1, count), candidateKeywords.size());
        List<String> resultKeywords = req.getResultKeywords().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (resultKeywords.size() != req.getResultKeywords().size()) {
            throw new BizException(400, "resultKeywords contains duplicate or blank items");
        }
        if (resultKeywords.size() != expectedSize) {
            throw new BizException(400, "resultKeywords size does not match preview size");
        }

        Set<String> candidateSet = new LinkedHashSet<>(candidateKeywords);
        for (String keyword : resultKeywords) {
            if (!candidateSet.contains(keyword)) {
                throw new BizException(400, "resultKeywords contains invalid keyword: " + keyword);
            }
        }
        return resultKeywords;
    }

    private long calcTotalEstimated(
            List<String> region,
            List<String> prefix,
            List<String> core,
            List<String> industry,
            List<String> suffix
    ) {
        return 1L * Math.max(1, region.size())
                * Math.max(1, prefix.size())
                * Math.max(1, core.size())
                * Math.max(1, industry.size())
                * Math.max(1, suffix.size());
    }

    private List<String> buildKeywords(
            List<String> region,
            List<String> prefix,
            List<String> core,
            List<String> industry,
            List<String> suffix
    ) {
        List<String> regionList = region.isEmpty() ? List.of("") : region;
        List<String> prefixList = prefix.isEmpty() ? List.of("") : prefix;
        List<String> coreList = core.isEmpty() ? List.of("") : core;
        List<String> industryList = industry.isEmpty() ? List.of("") : industry;
        List<String> suffixList = suffix.isEmpty() ? List.of("") : suffix;

        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String regionWord : regionList) {
            for (String prefixWord : prefixList) {
                for (String coreWord : coreList) {
                    for (String industryWord : industryList) {
                        for (String suffixWord : suffixList) {
                            String combined = (regionWord + prefixWord + coreWord + industryWord + suffixWord).trim();
                            if (StringUtils.hasText(combined)) {
                                result.add(combined);
                            }
                        }
                    }
                }
            }
        }
        return new ArrayList<>(result);
    }

    private record PreparedWord(String wordText, String source, Integer sortOrder, int index) {
    }
}
