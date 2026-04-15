package com.huanjing.geo.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.system.dto.KeywordAffixWordCreateRequest;
import com.huanjing.geo.module.system.dto.KeywordAffixWordOptionVO;
import com.huanjing.geo.module.system.dto.KeywordTypeOptionVO;
import com.huanjing.geo.module.system.dto.KeywordAffixWordUpdateRequest;
import com.huanjing.geo.module.system.entity.KeywordAffixWord;
import com.huanjing.geo.module.system.entity.SysDictItem;
import com.huanjing.geo.module.system.mapper.KeywordAffixWordMapper;
import com.huanjing.geo.module.system.mapper.SysDictItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class KeywordAffixWordService {

    private static final Set<String> AFFIX_KIND_SET = Set.of("prefix", "suffix");
    private static final String DICT_TYPE_QUESTION_TYPE = "question_type";
    private static final String DICT_TYPE_INDUSTRY_TAG = "industry_tag";

    private final CurrentUserService currentUserService;
    private final KeywordAffixWordMapper keywordAffixWordMapper;
    private final SysDictItemMapper sysDictItemMapper;

    public Page<KeywordAffixWord> page(long current, long size, String type, String affixKind, String keyword, Boolean enabled) {
        currentUserService.ensurePermission("keyword_affix.manage");
        LambdaQueryWrapper<KeywordAffixWord> wrapper = new LambdaQueryWrapper<KeywordAffixWord>()
                .orderByAsc(KeywordAffixWord::getType)
                .orderByAsc(KeywordAffixWord::getAffixKind)
                .orderByAsc(KeywordAffixWord::getSortOrder)
                .orderByAsc(KeywordAffixWord::getId);
        if (StringUtils.hasText(type)) {
            wrapper.eq(KeywordAffixWord::getType, type.trim().toLowerCase(Locale.ROOT));
        }
        if (StringUtils.hasText(affixKind)) {
            wrapper.eq(KeywordAffixWord::getAffixKind, normalizeAffixKind(affixKind));
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.like(KeywordAffixWord::getWordText, keyword.trim());
        }
        if (enabled != null) {
            wrapper.eq(KeywordAffixWord::getEnabled, enabled);
        }
        return keywordAffixWordMapper.selectPage(new Page<>(current, size), wrapper);
    }

    public KeywordAffixWordOptionVO options(String type) {
        currentUserService.ensurePermission("keyword_group.read");
        String normalizedType = StringUtils.hasText(type) ? type.trim().toLowerCase(Locale.ROOT) : null;
        if (StringUtils.hasText(normalizedType)) {
            ensureTypeExists(normalizedType, true);
        }
        List<KeywordAffixWord> enabledAffixWords = keywordAffixWordMapper.selectList(
            new LambdaQueryWrapper<KeywordAffixWord>()
                .eq(KeywordAffixWord::getEnabled, true)
                .in(KeywordAffixWord::getAffixKind, AFFIX_KIND_SET)
                .orderByAsc(KeywordAffixWord::getAffixKind)
                .orderByAsc(KeywordAffixWord::getSortOrder)
                .orderByAsc(KeywordAffixWord::getId)
        );
        List<SysDictItem> enabledTypeItems = listEnabledDictItems(DICT_TYPE_QUESTION_TYPE);
        List<SysDictItem> enabledIndustryItems = listEnabledDictItems(DICT_TYPE_INDUSTRY_TAG);

        KeywordAffixWordOptionVO vo = new KeywordAffixWordOptionVO();
        if (StringUtils.hasText(normalizedType)) {
            vo.setPrefixWords(enabledAffixWords.stream()
                    .filter(w -> "prefix".equals(w.getAffixKind()) && normalizedType.equals(w.getType()))
                    .toList());
            vo.setSuffixWords(enabledAffixWords.stream()
                    .filter(w -> "suffix".equals(w.getAffixKind()) && normalizedType.equals(w.getType()))
                    .toList());
        } else {
            vo.setPrefixWords(List.of());
            vo.setSuffixWords(List.of());
        }
        vo.setIndustryWords(enabledIndustryItems.stream().map(this::toIndustryOption).toList());
        vo.setTypeOptions(enabledTypeItems.stream().map(this::toTypeOption).toList());
        return vo;
    }

    @Transactional
    public KeywordAffixWord create(KeywordAffixWordCreateRequest req) {
        currentUserService.ensurePermission("keyword_affix.manage");
        String affixKind = normalizeAffixKind(req.getAffixKind());
        String type = normalizeWordType(req.getType(), affixKind, true);
        String wordText = normalizeWordText(req.getWordText());
        ensureUnique(null, type, affixKind, wordText);

        KeywordAffixWord word = new KeywordAffixWord();
        word.setType(type);
        word.setAffixKind(affixKind);
        word.setWordText(wordText);
        word.setSortOrder(req.getSortOrder() == null ? 100 : req.getSortOrder());
        word.setEnabled(req.getEnabled() == null || req.getEnabled());
        keywordAffixWordMapper.insert(word);
        return requireById(word.getId());
    }

    @Transactional
    public KeywordAffixWord update(Long id, KeywordAffixWordUpdateRequest req) {
        currentUserService.ensurePermission("keyword_affix.manage");
        KeywordAffixWord word = requireById(id);
        String affixKind = normalizeAffixKind(req.getAffixKind());
        String type = normalizeWordType(req.getType(), affixKind, true);
        String wordText = normalizeWordText(req.getWordText());
        ensureUnique(id, type, affixKind, wordText);

        word.setType(type);
        word.setAffixKind(affixKind);
        word.setWordText(wordText);
        word.setSortOrder(req.getSortOrder() == null ? 100 : req.getSortOrder());
        keywordAffixWordMapper.updateById(word);
        return requireById(id);
    }

    @Transactional
    public void updateStatus(Long id, boolean enabled) {
        currentUserService.ensurePermission("keyword_affix.manage");
        KeywordAffixWord word = requireById(id);
        word.setEnabled(enabled);
        keywordAffixWordMapper.updateById(word);
    }

    private KeywordAffixWord requireById(Long id) {
        KeywordAffixWord word = keywordAffixWordMapper.selectById(id);
        if (word == null) {
            throw new BizException(404, "Keyword affix word not found");
        }
        return word;
    }

    private void ensureUnique(Long currentId, String type, String affixKind, String wordText) {
        KeywordAffixWord existed = keywordAffixWordMapper.selectOne(
                new LambdaQueryWrapper<KeywordAffixWord>()
                        .eq(KeywordAffixWord::getType, type)
                        .eq(KeywordAffixWord::getAffixKind, affixKind)
                        .eq(KeywordAffixWord::getWordText, wordText)
                        .last("limit 1")
        );
        if (existed != null && !existed.getId().equals(currentId)) {
            throw new BizException(400, "Word already exists in same type and affix kind");
        }
    }

    public void ensureTypeExists(String rawType, boolean enabledOnly) {
        String type = normalizeTypeCode(rawType);
        LambdaQueryWrapper<SysDictItem> wrapper = new LambdaQueryWrapper<SysDictItem>()
                .eq(SysDictItem::getDictType, DICT_TYPE_QUESTION_TYPE)
                .eq(SysDictItem::getDictKey, type);
        if (enabledOnly) {
            wrapper.eq(SysDictItem::getEnabled, true);
        }
        SysDictItem existed = sysDictItemMapper.selectOne(wrapper.last("limit 1"));
        if (existed == null) {
            throw new BizException(400, "Unknown type: " + rawType);
        }
    }

    public List<KeywordTypeOptionVO> listEnabledTypeOptions() {
        currentUserService.ensurePermission("keyword_group.read");
        return listEnabledDictItems(DICT_TYPE_QUESTION_TYPE).stream().map(this::toTypeOption).toList();
    }

    private String normalizeTypeCode(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new BizException(400, "type is required");
        }
        String type = raw.trim().toLowerCase(Locale.ROOT);
        if (!type.matches("^[a-z][a-z0-9_]{0,15}$")) {
            throw new BizException(400, "type format invalid");
        }
        return type;
    }

    private String normalizeAffixKind(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new BizException(400, "affixKind is required");
        }
        String kind = raw.trim().toLowerCase(Locale.ROOT);
        if (!AFFIX_KIND_SET.contains(kind)) {
            throw new BizException(400, "affixKind must be prefix/suffix");
        }
        return kind;
    }

    private String normalizeWordType(String rawType, String affixKind, boolean checkExistingTypeForAffix) {
        String normalized = normalizeTypeCode(rawType);
        if (checkExistingTypeForAffix) {
            ensureTypeExists(normalized, false);
        }
        return normalized;
    }

    private String normalizeWordText(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new BizException(400, "wordText is required");
        }
        return raw.trim();
    }

    private List<SysDictItem> listEnabledDictItems(String dictType) {
        return sysDictItemMapper.selectList(new LambdaQueryWrapper<SysDictItem>()
                .eq(SysDictItem::getDictType, dictType)
                .eq(SysDictItem::getEnabled, true)
                .orderByAsc(SysDictItem::getSortOrder)
                .orderByAsc(SysDictItem::getId));
    }

    private KeywordAffixWord toIndustryOption(SysDictItem item) {
        KeywordAffixWord industry = new KeywordAffixWord();
        industry.setId(item.getId());
        industry.setType(item.getDictKey());
        industry.setAffixKind("industry");
        industry.setWordText(item.getDictValue());
        industry.setSortOrder(item.getSortOrder());
        industry.setEnabled(item.getEnabled());
        return industry;
    }

    private KeywordTypeOptionVO toTypeOption(SysDictItem item) {
        KeywordTypeOptionVO option = new KeywordTypeOptionVO();
        option.setValue(item.getDictKey());
        option.setLabel(item.getDictValue());
        return option;
    }
}
