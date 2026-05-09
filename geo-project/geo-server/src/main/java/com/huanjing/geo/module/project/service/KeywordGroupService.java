package com.huanjing.geo.module.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.project.dto.KeywordGroupColumnsRequest;
import com.huanjing.geo.module.project.dto.KeywordGroupColumnsVO;
import com.huanjing.geo.module.project.dto.KeywordGroupListItemVO;
import com.huanjing.geo.module.project.dto.KeywordGroupPayloadRequest;
import com.huanjing.geo.module.project.dto.KeywordGroupVO;
import com.huanjing.geo.module.project.dto.KeywordPreviewItemVO;
import com.huanjing.geo.module.project.dto.KeywordPreviewVO;
import com.huanjing.geo.module.project.dto.KeywordRequiredColumnsVO;
import com.huanjing.geo.module.project.dto.KeywordTypeConfigVO;
import com.huanjing.geo.module.project.dto.KeywordWordItemRequest;
import com.huanjing.geo.module.project.dto.KeywordWordItemVO;
import com.huanjing.geo.module.project.dto.LlmQuestionItemDTO;
import com.huanjing.geo.module.project.entity.KeywordGroup;
import com.huanjing.geo.module.project.entity.KeywordGroupResult;
import com.huanjing.geo.module.project.entity.KeywordGroupWord;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.KeywordGroupMapper;
import com.huanjing.geo.module.project.mapper.KeywordGroupResultMapper;
import com.huanjing.geo.module.project.mapper.KeywordGroupWordMapper;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KeywordGroupService {

    private static final int MAX_GENERATION = 1000;
    private static final Set<String> COLUMN_TYPE_SET = Set.of("area", "region", "prefix", "core", "industry", "suffix", "core_a", "compare", "core_b");
    private static final Set<String> SOURCE_SET = Set.of("system", "custom");
    private static final String RESULT_SOURCE_CARTESIAN = "cartesian";
    private static final String RESULT_SOURCE_LLM = "llm";

    private final KeywordGroupMapper keywordGroupMapper;
    private final KeywordGroupResultMapper keywordGroupResultMapper;
    private final KeywordGroupWordMapper keywordGroupWordMapper;
    private final CompanyMapper companyMapper;
    private final ProjectMapper projectMapper;
    private final CurrentUserService currentUserService;
    private final KeywordTypeConfigService keywordTypeConfigService;
    private final KeywordLlmQuestionService keywordLlmQuestionService;

    public Page<KeywordGroupListItemVO> page(long current, long size, String keyword, Long companyId, Long projectId, String type) {
        currentUserService.ensurePermission("keyword_group.read");
        LambdaQueryWrapper<KeywordGroup> wrapper = new LambdaQueryWrapper<KeywordGroup>()
                .eq(KeywordGroup::getDeleted, false)
                .orderByDesc(KeywordGroup::getUpdatedAt)
                .orderByDesc(KeywordGroup::getId);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(KeywordGroup::getName, keyword.trim());
        }
        if (companyId != null) {
            wrapper.eq(KeywordGroup::getCompanyId, companyId);
        }
        if (projectId != null) {
            wrapper.eq(KeywordGroup::getProjectId, projectId);
        }
        if (StringUtils.hasText(type)) {
            wrapper.eq(KeywordGroup::getType, normalizeType(type));
        }

        Page<KeywordGroup> page = keywordGroupMapper.selectPage(new Page<>(current, size), wrapper);
        List<Long> groupIds = page.getRecords().stream().map(KeywordGroup::getId).toList();
        Map<Long, String> companyNameMap = buildCompanyNameMap(page.getRecords().stream().map(KeywordGroup::getCompanyId).toList());
        Map<Long, Project> projectMap = buildProjectMap(page.getRecords().stream().map(KeywordGroup::getProjectId).toList());
        Map<Long, Long> savedCountMap = calcSavedCountsByGroupIds(groupIds);

        Page<KeywordGroupListItemVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream()
                .map(group -> toListItemVO(group, companyNameMap.get(group.getCompanyId()), projectMap.get(group.getProjectId()), savedCountMap.get(group.getId())))
                .toList());
        return result;
    }

    public KeywordGroupVO detail(Long id) {
        currentUserService.ensurePermission("keyword_group.read");
        KeywordGroup group = requireGroup(id);
        String companyName = resolveCompanyName(group.getCompanyId());
        Project project = group.getProjectId() == null ? null : projectMapper.selectById(group.getProjectId());
        Long estimatedCount = calcEstimatedCountsByGroupIds(List.of(group.getId())).getOrDefault(group.getId(), 0L);
        Long savedCount = calcSavedCountsByGroupIds(List.of(group.getId())).getOrDefault(group.getId(), 0L);
        return toDetailVO(group, companyName, project, estimatedCount, savedCount);
    }

    @Transactional
    public KeywordGroupVO create(KeywordGroupPayloadRequest req) {
        currentUserService.ensurePermission("keyword_group.write");
        if (!StringUtils.hasText(req.getName())) {
            throw new BizException(400, "name is required");
        }

        String type = normalizeType(req.getType());
        Company company = requireCompany(req.getCompanyId());
        Project project = resolveProject(req.getProjectId(), company.getId());
        ensureNameUnique(company.getId(), req.getName().trim(), null);

        KeywordGroup group = new KeywordGroup();
        group.setCompanyId(company.getId());
        group.setProjectId(project == null ? null : project.getId());
        group.setName(req.getName().trim());
        group.setType(type);
        group.setAreaEnabled(resolveAreaEnabled(type, req.getAreaEnabled(), null));
        group.setFunctionIndustryTag(normalizeNullable(req.getFunctionIndustryTag()));
        group.setRemark(normalizeNullable(req.getRemark()));
        group.setDeleted(false);
        keywordGroupMapper.insert(group);

        List<KeywordGroupWord> words = normalizeWordsForPersist(group.getId(), type, req.getColumns());
        assertGenerationLimit(type, req);
        List<String> candidateKeywords = buildCandidateKeywords(req);
        PreparedResults preparedResults = prepareResultsForPersist(req, candidateKeywords, null);
        saveWords(group.getId(), words);
        saveResults(group.getId(), preparedResults.items());
        keywordLlmQuestionService.deleteToken(req.getLlmGenerationToken());
        return toDetailVO(requireGroup(group.getId()), company.getCompanyName(), project, calcEstimatedByWords(type, group.getAreaEnabled(), words) + preparedResults.llmCount(), (long) preparedResults.items().size());
    }

    @Transactional
    public KeywordGroupVO update(Long id, KeywordGroupPayloadRequest req) {
        currentUserService.ensurePermission("keyword_group.write");
        if (!StringUtils.hasText(req.getName())) {
            throw new BizException(400, "name is required");
        }
        KeywordGroup group = requireGroup(id);
        String type = normalizeType(req.getType());
        Company company = requireCompany(req.getCompanyId());
        Project project = resolveProject(req.getProjectId(), company.getId());
        ensureNameUnique(company.getId(), req.getName().trim(), id);

        group.setCompanyId(company.getId());
        group.setProjectId(project == null ? null : project.getId());
        group.setName(req.getName().trim());
        group.setType(type);
        group.setAreaEnabled(resolveAreaEnabled(type, req.getAreaEnabled(), group.getAreaEnabled()));
        group.setFunctionIndustryTag(normalizeNullable(req.getFunctionIndustryTag()));
        group.setRemark(normalizeNullable(req.getRemark()));
        keywordGroupMapper.updateById(group);

        List<KeywordGroupWord> words = normalizeWordsForPersist(id, type, req.getColumns());
        assertGenerationLimit(type, req);
        List<String> candidateKeywords = buildCandidateKeywords(req);
        PreparedResults preparedResults = prepareResultsForPersist(req, candidateKeywords, id);
        saveWords(id, words);
        saveResults(id, preparedResults.items());
        keywordLlmQuestionService.deleteToken(req.getLlmGenerationToken());
        return toDetailVO(requireGroup(id), company.getCompanyName(), project, calcEstimatedByWords(type, group.getAreaEnabled(), words) + preparedResults.llmCount(), (long) preparedResults.items().size());
    }

    @Transactional
    public void delete(Long id) {
        currentUserService.ensurePermission("keyword_group.write");
        KeywordGroup group = requireGroup(id);
        keywordGroupResultMapper.delete(new LambdaQueryWrapper<KeywordGroupResult>().eq(KeywordGroupResult::getGroupId, id));
        keywordGroupWordMapper.delete(new LambdaQueryWrapper<KeywordGroupWord>().eq(KeywordGroupWord::getGroupId, id));
        group.setName(group.getName() + "_deleted_" + id);
        group.setDeleted(true);
        keywordGroupMapper.updateById(group);
    }

    public KeywordPreviewVO preview(KeywordGroupPayloadRequest req) {
        currentUserService.ensurePermission("keyword_group.read");
        String type = normalizeType(req.getType());
        int count = req.getCount() == null ? MAX_GENERATION : req.getCount();
        if (count <= 0) {
            throw new BizException(400, "count must be > 0");
        }

        long totalEstimated = calcTotalEstimated(type, req.getAreaEnabled(), req.getColumns());
        if (totalEstimated > MAX_GENERATION) {
            throw new BizException(400, "预计生成 " + totalEstimated + " 条，超过上限 " + MAX_GENERATION + "，请减少选词");
        }
        List<String> keywords = buildCandidateKeywords(req);
        PreparedResults preparedResults = prepareResultsForPreview(req, keywords);

        KeywordPreviewVO vo = new KeywordPreviewVO();
        vo.setTotalEstimated(totalEstimated + preparedResults.llmCount());
        vo.setTotalAvailable(keywords.size() + preparedResults.llmCount());
        vo.setTotalGenerated(preparedResults.items().size());
        // TODO: 阶段二接入黑名单后置过滤后更新真实过滤数量。
        vo.setFilteredCount(0);
        vo.setItems(preparedResults.items());
        return vo;
    }

    public Map<Long, Long> calcEstimatedCountsByGroupIds(List<Long> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<KeywordGroup> groups = keywordGroupMapper.selectList(new LambdaQueryWrapper<KeywordGroup>().in(KeywordGroup::getId, groupIds));
        Map<Long, KeywordGroup> groupMap = groups.stream().collect(Collectors.toMap(KeywordGroup::getId, g -> g));
        Map<Long, Long> llmCountMap = calcLlmCountsByGroupIds(groupIds);
        List<KeywordGroupWord> words = keywordGroupWordMapper.selectList(
                new LambdaQueryWrapper<KeywordGroupWord>().in(KeywordGroupWord::getGroupId, groupIds)
        );
        Map<Long, List<KeywordGroupWord>> wordMap = words.stream().collect(Collectors.groupingBy(KeywordGroupWord::getGroupId));
        Map<Long, Long> result = new HashMap<>();
        for (Long groupId : groupIds) {
            KeywordGroup group = groupMap.get(groupId);
            long cartesianCount = calcEstimatedByWords(
                    group == null ? null : group.getType(),
                    group == null ? null : group.getAreaEnabled(),
                    wordMap.getOrDefault(groupId, List.of())
            );
            result.put(groupId, cartesianCount + llmCountMap.getOrDefault(groupId, 0L));
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

    public long countSelectedSavedKeywords(Long projectId) {
        if (projectId == null) {
            return 0L;
        }
        return keywordGroupResultMapper.countSavedKeywordsByProject(projectId);
    }

    public long countActiveProjectSavedKeywords(Long companyId, Long excludeProjectId) {
        if (companyId == null) {
            return 0L;
        }
        return keywordGroupResultMapper.countSavedKeywordsByCompanyActiveProjects(companyId, excludeProjectId);
    }

    private List<KeywordGroupWord> normalizeWordsForPersist(Long groupId, String type, KeywordGroupColumnsRequest columns) {
        Map<String, List<KeywordWordItemRequest>> sourceMap = new LinkedHashMap<>();
        if (isCompareType(type)) {
            sourceMap.put("core_a", columns == null ? null : columns.getCoreWordsA());
            sourceMap.put("compare", columns == null ? null : columns.getCompareWords());
            sourceMap.put("core_b", columns == null ? null : columns.getCoreWordsB());
            sourceMap.put("suffix", columns == null ? null : columns.getSuffixWords());
        } else {
            sourceMap.put("area", readAreaWords(columns));
            sourceMap.put("prefix", columns == null ? null : columns.getPrefixWords());
            sourceMap.put("core", columns == null ? null : columns.getCoreWords());
            sourceMap.put("industry", columns == null ? null : columns.getIndustryWords());
            sourceMap.put("suffix", columns == null ? null : columns.getSuffixWords());
        }

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

    private void saveResults(Long groupId, List<KeywordPreviewItemVO> items) {
        keywordGroupResultMapper.delete(new LambdaQueryWrapper<KeywordGroupResult>().eq(KeywordGroupResult::getGroupId, groupId));
        for (int i = 0; i < items.size(); i++) {
            KeywordPreviewItemVO item = items.get(i);
            KeywordGroupResult result = new KeywordGroupResult();
            result.setGroupId(groupId);
            result.setKeywordText(item.getText());
            result.setSourceType(normalizeResultSource(item.getSourceType()));
            result.setSeedText(RESULT_SOURCE_LLM.equals(normalizeResultSource(item.getSourceType())) ? normalizeNullable(item.getSeedText()) : null);
            result.setSortOrder((i + 1) * 10);
            keywordGroupResultMapper.insert(result);
        }
    }

    private KeywordGroup requireGroup(Long id) {
        KeywordGroup group = keywordGroupMapper.selectById(id);
        if (group == null) {
            throw new BizException(404, "Keyword group not found");
        }
        if (Boolean.TRUE.equals(group.getDeleted())) {
            throw new BizException(404, "Keyword group not found");
        }
        return group;
    }

    private KeywordGroupListItemVO toListItemVO(KeywordGroup group, String companyName, Project project, Long savedCount) {
        KeywordGroupListItemVO vo = new KeywordGroupListItemVO();
        vo.setId(group.getId());
        vo.setCompanyId(group.getCompanyId());
        vo.setCompanyName(companyName);
        vo.setProjectId(group.getProjectId());
        vo.setProjectName(project == null ? null : project.getProjectName());
        vo.setPackageType(project == null ? null : project.getPackageType());
        vo.setName(group.getName());
        vo.setType(group.getType());
        vo.setTypeLabel(keywordTypeConfigService.labelOf(group.getType()));
        vo.setLegacyType(keywordTypeConfigService.isLegacyType(group.getType()));
        vo.setSavedKeywordCount(savedCount == null ? 0L : savedCount);
        vo.setUpdatedAt(group.getUpdatedAt());
        return vo;
    }

    private KeywordGroupVO toSummaryVO(KeywordGroup group, String companyName, Project project, Long estimatedCount, Long savedCount) {
        KeywordGroupVO vo = new KeywordGroupVO();
        vo.setId(group.getId());
        vo.setCompanyId(group.getCompanyId());
        vo.setCompanyName(companyName);
        vo.setProjectId(group.getProjectId());
        vo.setProjectName(project == null ? null : project.getProjectName());
        vo.setPackageType(project == null ? null : project.getPackageType());
        vo.setName(group.getName());
        vo.setType(group.getType());
        vo.setTypeLabel(keywordTypeConfigService.labelOf(group.getType()));
        vo.setLegacyType(keywordTypeConfigService.isLegacyType(group.getType()));
        vo.setAreaEnabled(group.getAreaEnabled());
        vo.setFunctionIndustryTag(group.getFunctionIndustryTag());
        vo.setRemark(group.getRemark());
        vo.setEstimatedKeywordCount(estimatedCount == null ? 0L : estimatedCount);
        vo.setSavedKeywordCount(savedCount == null ? 0L : savedCount);
        vo.setCreatedAt(group.getCreatedAt());
        vo.setUpdatedAt(group.getUpdatedAt());
        return vo;
    }

    private KeywordGroupVO toDetailVO(KeywordGroup group, String companyName, Project project, Long estimatedCount, Long savedCount) {
        KeywordGroupVO vo = toSummaryVO(group, companyName, project, estimatedCount, savedCount);
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
            item.setIsManual(false);
            item.setIsTemporary(false);
            map.computeIfAbsent(word.getColumnType(), k -> new ArrayList<>()).add(item);
        }

        List<KeywordWordItemVO> areaWords = new ArrayList<>();
        areaWords.addAll(map.getOrDefault("area", List.of()));
        areaWords.addAll(map.getOrDefault("region", List.of()));

        KeywordGroupColumnsVO columnsVO = new KeywordGroupColumnsVO();
        columnsVO.setAreaWords(areaWords);
        columnsVO.setPrefixWords(map.getOrDefault("prefix", List.of()));
        columnsVO.setCoreWords(map.getOrDefault("core", List.of()));
        columnsVO.setIndustryWords(map.getOrDefault("industry", List.of()));
        columnsVO.setSuffixWords(map.getOrDefault("suffix", List.of()));
        columnsVO.setCoreWordsA(map.getOrDefault("core_a", List.of()));
        columnsVO.setCompareWords(map.getOrDefault("compare", List.of()));
        columnsVO.setCoreWordsB(map.getOrDefault("core_b", List.of()));
        vo.setColumns(columnsVO);
        vo.setLlmQuestions(keywordGroupResultMapper.selectLlmQuestionsByGroupId(group.getId()));
        return vo;
    }

    private long calcEstimatedByWords(String type, Boolean areaEnabled, List<KeywordGroupWord> words) {
        Map<String, Integer> columnCount = new HashMap<>();
        for (KeywordGroupWord word : words) {
            String columnType = normalizeColumnType(word.getColumnType());
            columnCount.merge(columnType, 1, Integer::sum);
        }
        if (isCompareType(type)) {
            return 1L
                    * Math.max(1, columnCount.getOrDefault("core_a", 0))
                    * Math.max(1, columnCount.getOrDefault("compare", 0))
                    * Math.max(1, columnCount.getOrDefault("core_b", 0))
                    * Math.max(1, columnCount.getOrDefault("suffix", 0));
        }
        boolean useArea = Boolean.TRUE.equals(areaEnabled);
        return 1L
                * (useArea ? Math.max(1, columnCount.getOrDefault("area", 0)) : 1)
                * Math.max(1, columnCount.getOrDefault("prefix", 0))
                * Math.max(1, columnCount.getOrDefault("core", 0))
                * Math.max(1, columnCount.getOrDefault("industry", 0))
                * Math.max(1, columnCount.getOrDefault("suffix", 0));
    }

    private Map<Long, Long> calcLlmCountsByGroupIds(List<Long> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<KeywordGroupResult> results = keywordGroupResultMapper.selectList(
                new LambdaQueryWrapper<KeywordGroupResult>()
                        .select(KeywordGroupResult::getGroupId)
                        .in(KeywordGroupResult::getGroupId, groupIds)
                        .eq(KeywordGroupResult::getSourceType, RESULT_SOURCE_LLM)
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

    private Map<Long, Project> buildProjectMap(List<Long> projectIds) {
        List<Long> validIds = projectIds.stream().filter(id -> id != null && id > 0).distinct().toList();
        if (validIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return projectMapper.selectList(
                new LambdaQueryWrapper<Project>()
                        .select(Project::getId, Project::getProjectName, Project::getPackageType)
                        .in(Project::getId, validIds)
        ).stream().collect(Collectors.toMap(Project::getId, p -> p, (a, b) -> a));
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

    private Project resolveProject(Long projectId, Long companyId) {
        if (projectId == null) {
            return null;
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BizException(404, "Project not found");
        }
        if (project.getCompanyId() != null && !project.getCompanyId().equals(companyId)) {
            throw new BizException(400, "Project does not belong to company");
        }
        return project;
    }

    private String normalizeType(String raw) {
        String type = keywordTypeConfigService.normalizeType(raw);
        if (!keywordTypeConfigService.isKnownType(type)) {
            throw new BizException(400, "Unknown keyword group type: " + raw);
        }
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
                String source = StringUtils.hasText(req.getSource()) ? req.getSource().trim().toLowerCase() : "custom";
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

    private List<String> buildCandidateKeywords(KeywordGroupPayloadRequest req) {
        String type = normalizeType(req.getType());
        validateRequiredColumns(type, req);

        List<String> keywords;
        if (isCompareType(type)) {
            keywords = buildCompareKeywords(
                    normalizeWordsForPreview(req.getColumns() == null ? null : req.getColumns().getCoreWordsA()),
                    normalizeWordsForPreview(req.getColumns() == null ? null : req.getColumns().getCompareWords()),
                    normalizeWordsForPreview(req.getColumns() == null ? null : req.getColumns().getCoreWordsB()),
                    normalizeWordsForPreview(req.getColumns() == null ? null : req.getColumns().getSuffixWords())
            );
        } else {
            boolean useArea = shouldUseArea(type, req.getAreaEnabled());
            keywords = buildStandardKeywords(
                    useArea ? normalizeWordsForPreview(readAreaWords(req.getColumns())) : List.of(),
                    normalizeWordsForPreview(req.getColumns() == null ? null : req.getColumns().getPrefixWords()),
                    normalizeWordsForPreview(req.getColumns() == null ? null : req.getColumns().getCoreWords()),
                    normalizeWordsForPreview(req.getColumns() == null ? null : req.getColumns().getIndustryWords()),
                    normalizeWordsForPreview(req.getColumns() == null ? null : req.getColumns().getSuffixWords())
            );
        }
        return keywords;
    }

    private void assertGenerationLimit(String type, KeywordGroupPayloadRequest req) {
        long totalEstimated = calcTotalEstimated(type, req.getAreaEnabled(), req.getColumns());
        if (totalEstimated > MAX_GENERATION) {
            throw new BizException(400, "预计生成 " + totalEstimated + " 条，超过上限 " + MAX_GENERATION + "，请减少选词");
        }
    }

    private void validateRequiredColumns(String type, KeywordGroupPayloadRequest req) {
        if (keywordTypeConfigService.isLegacyType(type)) {
            return;
        }
        KeywordTypeConfigVO config = keywordTypeConfigService.getConfig(type);
        KeywordRequiredColumnsVO required = config.getRequiredColumns();
        KeywordGroupColumnsRequest columns = req.getColumns();
        if (required.isCore() && normalizeWordsForPreview(columns == null ? null : columns.getCoreWords()).isEmpty()) {
            throw new BizException(400, "coreWords is required");
        }
        if (required.isIndustry() && normalizeWordsForPreview(columns == null ? null : columns.getIndustryWords()).isEmpty()) {
            throw new BizException(400, "industryWords is required");
        }
        if (required.isCompareCore()) {
            if (normalizeWordsForPreview(columns == null ? null : columns.getCoreWordsA()).isEmpty()) {
                throw new BizException(400, "COMPARE_CORE_A_REQUIRED: coreWordsA is required");
            }
            if (normalizeWordsForPreview(columns == null ? null : columns.getCoreWordsB()).isEmpty()) {
                throw new BizException(400, "COMPARE_CORE_B_REQUIRED: coreWordsB is required");
            }
        }
        if (required.isCompareWord() && normalizeWordsForPreview(columns == null ? null : columns.getCompareWords()).isEmpty()) {
            throw new BizException(400, "COMPARE_WORD_REQUIRED: compareWords is required");
        }
        if (required.isSuffix() && normalizeWordsForPreview(columns == null ? null : columns.getSuffixWords()).isEmpty()) {
            throw new BizException(400, "suffixWords is required");
        }
        if (config.isFunctionIndustryRequired() && !StringUtils.hasText(req.getFunctionIndustryTag())) {
            throw new BizException(400, "FUNCTION_INDUSTRY_REQUIRED: functionIndustryTag is required");
        }
    }

    private PreparedResults prepareResultsForPreview(KeywordGroupPayloadRequest req, List<String> candidateKeywords) {
        List<LlmQuestionItemDTO> llmQuestions = normalizeLlmQuestionsForPreview(req);
        return allocateResults(req, candidateKeywords, llmQuestions);
    }

    private PreparedResults prepareResultsForPersist(KeywordGroupPayloadRequest req, List<String> candidateKeywords, Long groupId) {
        if (req.getResultKeywords() == null || req.getResultKeywords().isEmpty()) {
            throw new BizException(400, "resultKeywords is required");
        }
        List<LlmQuestionItemDTO> llmQuestions = normalizeLlmQuestionsForPersist(req, groupId);
        PreparedResults expected = allocateResults(req, candidateKeywords, llmQuestions);
        List<KeywordPreviewItemVO> submitted = normalizeSubmittedResultItems(req.getResultKeywords());
        if (submitted.size() != expected.items().size()) {
            throw new BizException(400, "INVALID_RESULT_KEYWORDS: resultKeywords size does not match preview size");
        }
        for (int i = 0; i < submitted.size(); i++) {
            KeywordPreviewItemVO actual = submitted.get(i);
            KeywordPreviewItemVO want = expected.items().get(i);
            if (!want.getText().equals(actual.getText())
                    || !want.getSourceType().equals(normalizeResultSource(actual.getSourceType()))
                    || !equalsNullable(normalizeNullable(want.getSeedText()), normalizeNullable(actual.getSeedText()))) {
                throw new BizException(400, "INVALID_RESULT_KEYWORDS: " + actual.getText());
            }
        }
        return expected;
    }

    private PreparedResults allocateResults(KeywordGroupPayloadRequest req, List<String> candidateKeywords, List<LlmQuestionItemDTO> llmQuestions) {
        int count = req.getCount() == null ? MAX_GENERATION : req.getCount();
        if (count <= 0) {
            throw new BizException(400, "count must be > 0");
        }
        if (count < llmQuestions.size()) {
            throw new BizException(400, "COUNT_LESS_THAN_LLM: 入库数 " + count + " 小于已生成 LLM 问题数 " + llmQuestions.size() + ",请调整");
        }

        int cartesianLimit = count - llmQuestions.size();
        List<KeywordPreviewItemVO> items = new ArrayList<>();
        Set<String> usedTexts = new LinkedHashSet<>();
        for (LlmQuestionItemDTO question : llmQuestions) {
            KeywordPreviewItemVO item = new KeywordPreviewItemVO(question.getQuestionText(), RESULT_SOURCE_LLM);
            item.setSeedText(normalizeNullable(question.getSeedText()));
            items.add(item);
            usedTexts.add(question.getQuestionText());
        }
        for (String keyword : candidateKeywords) {
            if (items.size() >= count) {
                break;
            }
            if (cartesianLimit <= 0 || !usedTexts.add(keyword)) {
                continue;
            }
            items.add(new KeywordPreviewItemVO(keyword, RESULT_SOURCE_CARTESIAN));
            cartesianLimit--;
        }
        return new PreparedResults(items, llmQuestions.size());
    }

    private List<LlmQuestionItemDTO> normalizeLlmQuestionsForPreview(KeywordGroupPayloadRequest req) {
        return dedupLlmItems(req.getLlmQuestions());
    }

    private List<LlmQuestionItemDTO> normalizeLlmQuestionsForPersist(KeywordGroupPayloadRequest req, Long groupId) {
        List<LlmQuestionItemDTO> llmQuestions = dedupLlmItems(req.getLlmQuestions());
        if (llmQuestions.isEmpty()) {
            return List.of();
        }

        Set<String> valid;
        if (StringUtils.hasText(req.getLlmGenerationToken())) {
            valid = keywordLlmQuestionService.loadTokenItems(req.getLlmGenerationToken()).stream()
                    .map(this::llmItemKey)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        } else if (groupId != null) {
            valid = keywordGroupResultMapper.selectLlmQuestionsByGroupId(groupId).stream()
                    .map(this::llmItemKey)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        } else {
            throw new BizException(400, "LLM_QUESTION_TAMPERED: LLM 生成已过期,请重新生成");
        }
        if (valid.isEmpty()) {
            throw new BizException(400, "LLM_QUESTION_TAMPERED: LLM 生成已过期,请重新生成");
        }
        for (LlmQuestionItemDTO question : llmQuestions) {
            if (!valid.contains(llmItemKey(question))) {
                throw new BizException(400, "LLM_QUESTION_TAMPERED: 检测到大模型问题被篡改,请重新生成");
            }
        }
        return llmQuestions;
    }

    private List<KeywordPreviewItemVO> normalizeSubmittedResultItems(List<KeywordPreviewItemVO> input) {
        List<KeywordPreviewItemVO> items = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (KeywordPreviewItemVO item : input) {
            if (item == null || !StringUtils.hasText(item.getText())) {
                continue;
            }
            String text = item.getText().trim();
            String sourceType = normalizeResultSource(item.getSourceType());
            String seedText = RESULT_SOURCE_LLM.equals(sourceType) ? normalizeNullable(item.getSeedText()) : null;
            String key = sourceType + "\n" + text + "\n" + (seedText == null ? "" : seedText);
            if (seen.add(key)) {
                KeywordPreviewItemVO normalized = new KeywordPreviewItemVO(text, sourceType);
                normalized.setSeedText(seedText);
                items.add(normalized);
            }
        }
        if (items.size() != input.size()) {
            throw new BizException(400, "resultKeywords contains duplicate or blank items");
        }
        return items;
    }

    private List<LlmQuestionItemDTO> dedupLlmItems(List<LlmQuestionItemDTO> input) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<String, LlmQuestionItemDTO> map = new LinkedHashMap<>();
        for (LlmQuestionItemDTO item : input) {
            if (item == null || !StringUtils.hasText(item.getQuestionText())) {
                continue;
            }
            String questionText = item.getQuestionText().trim();
            String seedText = normalizeNullable(item.getSeedText());
            if (questionText.length() > 64) {
                throw new BizException(400, "LLM_QUESTION_TAMPERED: 检测到大模型问题被篡改,请重新生成");
            }
            map.putIfAbsent(questionText, new LlmQuestionItemDTO(questionText, seedText));
        }
        return new ArrayList<>(map.values());
    }

    private String llmItemKey(LlmQuestionItemDTO item) {
        String questionText = item == null ? "" : normalizeNullable(item.getQuestionText());
        String seedText = item == null ? "" : normalizeNullable(item.getSeedText());
        return (questionText == null ? "" : questionText) + "\n" + (seedText == null ? "" : seedText);
    }

    private boolean equalsNullable(String left, String right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
    }

    private String normalizeResultSource(String sourceType) {
        return RESULT_SOURCE_LLM.equals(sourceType) ? RESULT_SOURCE_LLM : RESULT_SOURCE_CARTESIAN;
    }

    private long calcTotalEstimated(String type, Boolean areaEnabled, KeywordGroupColumnsRequest columns) {
        if (isCompareType(type)) {
            return 1L
                    * Math.max(1, normalizeWordsForPreview(columns == null ? null : columns.getCoreWordsA()).size())
                    * Math.max(1, normalizeWordsForPreview(columns == null ? null : columns.getCompareWords()).size())
                    * Math.max(1, normalizeWordsForPreview(columns == null ? null : columns.getCoreWordsB()).size())
                    * Math.max(1, normalizeWordsForPreview(columns == null ? null : columns.getSuffixWords()).size());
        }
        boolean useArea = shouldUseArea(type, areaEnabled);
        return 1L
                * (useArea ? Math.max(1, normalizeWordsForPreview(readAreaWords(columns)).size()) : 1)
                * Math.max(1, normalizeWordsForPreview(columns == null ? null : columns.getPrefixWords()).size())
                * Math.max(1, normalizeWordsForPreview(columns == null ? null : columns.getCoreWords()).size())
                * Math.max(1, normalizeWordsForPreview(columns == null ? null : columns.getIndustryWords()).size())
                * Math.max(1, normalizeWordsForPreview(columns == null ? null : columns.getSuffixWords()).size());
    }

    private List<String> buildStandardKeywords(
            List<String> area,
            List<String> prefix,
            List<String> core,
            List<String> industry,
            List<String> suffix
    ) {
        List<String> areaList = area.isEmpty() ? List.of("") : area;
        List<String> prefixList = prefix.isEmpty() ? List.of("") : prefix;
        List<String> coreList = core.isEmpty() ? List.of("") : core;
        List<String> industryList = industry.isEmpty() ? List.of("") : industry;
        List<String> suffixList = suffix.isEmpty() ? List.of("") : suffix;

        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String areaWord : areaList) {
            for (String prefixWord : prefixList) {
                for (String coreWord : coreList) {
                    for (String industryWord : industryList) {
                        for (String suffixWord : suffixList) {
                            String combined = (areaWord + prefixWord + coreWord + industryWord + suffixWord).trim();
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

    private List<String> buildCompareKeywords(List<String> coreA, List<String> compare, List<String> coreB, List<String> suffix) {
        List<String> suffixList = suffix.isEmpty() ? List.of("") : suffix;
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String coreAWord : coreA) {
            for (String compareWord : compare) {
                for (String coreBWord : coreB) {
                    for (String suffixWord : suffixList) {
                        String combined = (coreAWord + compareWord + coreBWord + suffixWord).trim();
                        if (StringUtils.hasText(combined)) {
                            result.add(combined);
                        }
                    }
                }
            }
        }
        return new ArrayList<>(result);
    }

    private boolean isCompareType(String type) {
        return "comparison".equals(type);
    }

    private boolean shouldUseArea(String type, Boolean areaEnabled) {
        if (keywordTypeConfigService.isLegacyType(type)) {
            return Boolean.TRUE.equals(areaEnabled);
        }
        KeywordTypeConfigVO config = keywordTypeConfigService.getConfig(type);
        if (!config.getColumns().isArea()) {
            return false;
        }
        return areaEnabled == null ? config.isAreaEnabledByDefault() : areaEnabled;
    }

    private Boolean resolveAreaEnabled(String type, Boolean requested, Boolean existing) {
        if (requested != null) {
            return requested;
        }
        if (existing != null) {
            return existing;
        }
        if (keywordTypeConfigService.isLegacyType(type)) {
            return false;
        }
        return keywordTypeConfigService.getConfig(type).isAreaEnabledByDefault();
    }

    private List<KeywordWordItemRequest> readAreaWords(KeywordGroupColumnsRequest columns) {
        if (columns == null) {
            return null;
        }
        return columns.getAreaWords() != null ? columns.getAreaWords() : columns.getRegionWords();
    }

    private String normalizeColumnType(String columnType) {
        return "region".equals(columnType) ? "area" : columnType;
    }

    private String normalizeNullable(String raw) {
        return StringUtils.hasText(raw) ? raw.trim() : null;
    }

    private void ensureNameUnique(Long companyId, String name, Long excludeId) {
        Long count = keywordGroupMapper.selectCount(new LambdaQueryWrapper<KeywordGroup>()
                .eq(KeywordGroup::getCompanyId, companyId)
                .eq(KeywordGroup::getName, name)
                .eq(KeywordGroup::getDeleted, false)
                .ne(excludeId != null, KeywordGroup::getId, excludeId));
        if (count != null && count > 0) {
            throw new BizException(400, "KEYWORD_GROUP_NAME_DUPLICATE: 该客户下已存在同名词组");
        }
    }

    private record PreparedWord(String wordText, String source, Integer sortOrder, int index) {
    }

    private record PreparedResults(List<KeywordPreviewItemVO> items, int llmCount) {
    }
}
