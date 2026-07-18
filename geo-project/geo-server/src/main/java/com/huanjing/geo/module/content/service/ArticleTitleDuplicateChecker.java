package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.mapper.ArticleDraftMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.Normalizer;

@Service
@RequiredArgsConstructor
public class ArticleTitleDuplicateChecker {

    private final ArticleDraftMapper articleDraftMapper;

    public boolean exists(Long projectId, String title) {
        String normalized = normalize(title);
        if (projectId == null || normalized.isEmpty()) {
            return false;
        }
        return articleDraftMapper.selectList(
                        new LambdaQueryWrapper<ArticleDraft>()
                                .select(ArticleDraft::getTitle)
                                .eq(ArticleDraft::getProjectId, projectId)
                                .isNotNull(ArticleDraft::getTitle)
                ).stream()
                .map(ArticleDraft::getTitle)
                .anyMatch(existing -> normalized.equals(normalize(existing)));
    }

    String normalize(String title) {
        if (!StringUtils.hasText(title)) {
            return "";
        }
        String normalized = Normalizer.normalize(title, Normalizer.Form.NFKC).toLowerCase();
        StringBuilder result = new StringBuilder(normalized.length());
        normalized.codePoints()
                .filter(Character::isLetterOrDigit)
                .forEach(result::appendCodePoint);
        return result.toString();
    }
}
