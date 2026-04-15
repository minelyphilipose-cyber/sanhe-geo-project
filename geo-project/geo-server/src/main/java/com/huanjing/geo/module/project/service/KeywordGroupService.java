package com.huanjing.geo.module.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.project.dto.KeywordGroupColumnsRequest;
import com.huanjing.geo.module.project.dto.KeywordGroupColumnsVO;
import com.huanjing.geo.module.project.dto.KeywordGroupPayloadRequest;
import com.huanjing.geo.module.project.dto.KeywordGroupVO;
import com.huanjing.geo.module.project.dto.KeywordPreviewVO;
import com.huanjing.geo.module.project.dto.KeywordWordItemRequest;
import com.huanjing.geo.module.project.dto.KeywordWordItemVO;
import com.huanjing.geo.module.project.entity.KeywordGroup;
import com.huanjing.geo.module.project.entity.KeywordGroupWord;
import com.huanjing.geo.module.project.mapper.KeywordGroupMapper;
import com.huanjing.geo.module.project.mapper.KeywordGroupWordMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import com.huanjing.geo.module.system.service.KeywordAffixWordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class KeywordGroupService {

    private static final int MAX_GENERATION = 1000;
    private static final Set<String> COLUMN_TYPE_SET = Set.of("region", "prefix", "core", "industry", "suffix");
    private static final Set<String> SOURCE_SET = Set.of("system", "custom");

    private final KeywordGroupMapper keywordGroupMapper;
    private final KeywordGroupWordMapper keywordGroupWordMapper;
    private final CurrentUserService currentUserService;
    private final KeywordAffixWordService keywordAffixWordService;

    public Page<KeywordGroupVO> page(long current, long size, String keyword, String type) {
        currentUserService.ensurePermission("keyword_group.read");
        LambdaQueryWrapper<KeywordGroup> wrapper = new LambdaQueryWrapper<KeywordGroup>()
                .orderByDesc(KeywordGroup::getUpdatedAt)
                .orderByDesc(KeywordGroup::getId);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(KeywordGroup::getName, keyword.trim());
        }
        if (StringUtils.hasText(type)) {
            wrapper.eq(KeywordGroup::getType, normalizeType(type));
        }
        Page<KeywordGroup> page = keywordGroupMapper.selectPage(new Page<>(current, size), wrapper);
        Page<KeywordGroupVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toSummaryVO).toList());
        return result;
    }

    public KeywordGroupVO detail(Long id) {
        currentUserService.ensurePermission("keyword_group.read");
        KeywordGroup group = requireGroup(id);
        return toDetailVO(group);
    }

    @Transactional
    public KeywordGroupVO create(KeywordGroupPayloadRequest req) {
        currentUserService.ensurePermission("keyword_group.write");
        if (!StringUtils.hasText(req.getName())) {
            throw new BizException(400, "name is required");
        }

        KeywordGroup group = new KeywordGroup();
        group.setName(req.getName().trim());
        group.setType(normalizeType(req.getType()));
        group.setRemark(StringUtils.hasText(req.getRemark()) ? req.getRemark().trim() : null);
        keywordGroupMapper.insert(group);

        List<KeywordGroupWord> words = normalizeWordsForPersist(group.getId(), req.getColumns());
        saveWords(group.getId(), words);
        return toDetailVO(requireGroup(group.getId()));
    }

    @Transactional
    public KeywordGroupVO update(Long id, KeywordGroupPayloadRequest req) {
        currentUserService.ensurePermission("keyword_group.write");
        if (!StringUtils.hasText(req.getName())) {
            throw new BizException(400, "name is required");
        }
        KeywordGroup group = requireGroup(id);
        group.setName(req.getName().trim());
        group.setType(normalizeType(req.getType()));
        group.setRemark(StringUtils.hasText(req.getRemark()) ? req.getRemark().trim() : null);
        keywordGroupMapper.updateById(group);

        List<KeywordGroupWord> words = normalizeWordsForPersist(id, req.getColumns());
        saveWords(id, words);
        return toDetailVO(requireGroup(id));
    }

    @Transactional
    public void delete(Long id) {
        currentUserService.ensurePermission("keyword_group.write");
        requireGroup(id);
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
        int limit = Math.min(Math.max(1, count), keywords.size());

        KeywordPreviewVO vo = new KeywordPreviewVO();
        vo.setTotalEstimated(totalEstimated);
        vo.setTotalGenerated(limit);
        vo.setKeywords(keywords.subList(0, limit));
        return vo;
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

    private KeywordGroup requireGroup(Long id) {
        KeywordGroup group = keywordGroupMapper.selectById(id);
        if (group == null) {
            throw new BizException(404, "Keyword group not found");
        }
        return group;
    }

    private KeywordGroupVO toSummaryVO(KeywordGroup group) {
        KeywordGroupVO vo = new KeywordGroupVO();
        vo.setId(group.getId());
        vo.setName(group.getName());
        vo.setType(group.getType());
        vo.setRemark(group.getRemark());
        vo.setCreatedAt(group.getCreatedAt());
        vo.setUpdatedAt(group.getUpdatedAt());
        return vo;
    }

    private KeywordGroupVO toDetailVO(KeywordGroup group) {
        KeywordGroupVO vo = toSummaryVO(group);
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
