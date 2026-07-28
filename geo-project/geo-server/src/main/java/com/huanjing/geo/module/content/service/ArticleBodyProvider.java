package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.storage.ObjectStorageService;
import com.huanjing.geo.module.content.entity.ArticleDraftVersion;
import com.huanjing.geo.module.content.mapper.ArticleDraftVersionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@Slf4j
public class ArticleBodyProvider {

    private final ArticleDraftVersionMapper articleDraftVersionMapper;
    private final ObjectStorageService objectStorageService;

    public ArticleBody getArticleBody(Long versionId) {
        if (versionId == null) {
            throw new BizException(400, "versionId is required");
        }
        ArticleDraftVersion version = articleDraftVersionMapper.selectOne(
                new LambdaQueryWrapper<ArticleDraftVersion>()
                        .eq(ArticleDraftVersion::getId, versionId)
                        .last("LIMIT 1"));
        if (version == null) {
            throw new BizException(404, "Article version not found");
        }
        return getArticleBody(version);
    }

    public ArticleBody getLatestArticleBody(Long articleId) {
        if (articleId == null) {
            throw new BizException(400, "articleId is required");
        }
        ArticleDraftVersion version = articleDraftVersionMapper.selectOne(
                new LambdaQueryWrapper<ArticleDraftVersion>()
                        .eq(ArticleDraftVersion::getArticleId, articleId)
                        .orderByDesc(ArticleDraftVersion::getVersionNo)
                        .last("LIMIT 1"));
        if (version == null) {
            throw new BizException(404, "Article version not found");
        }
        return getArticleBody(version);
    }

    public ArticleBody getArticleBody(ArticleDraftVersion version) {
        if (version == null) {
            throw new BizException(400, "article version is required");
        }
        Long versionId = version.getId();
        if (StringUtils.hasText(version.getContentMarkdown())) {
            return new ArticleBody(versionId, version.getContentMarkdown(), "db", sha256Hex(version.getContentMarkdown()));
        }
        if (versionId == null) {
            throw new BizException(400, "article version id is required for archived body");
        }
        if (!StringUtils.hasText(version.getContentObjectKey())) {
            throw new BizException(404, "Article body unavailable: no DB body or archive object key");
        }
        byte[] bytes;
        try {
            bytes = objectStorageService.readBytes(version.getContentObjectKey());
        } catch (Exception ex) {
            throw new BizException(500, "Article body archive unavailable", ex);
        }
        String checksum = sha256Hex(bytes);
        if (!StringUtils.hasText(version.getContentChecksum())) {
            if (version.getContentPurgedAt() != null) {
                throw new BizException(500, "Article body archive checksum is missing");
            }
            log.warn("Article archive checksum is empty, versionId={}, objectKey={}", versionId, version.getContentObjectKey());
        }
        if (StringUtils.hasText(version.getContentChecksum())
                && !version.getContentChecksum().equalsIgnoreCase(checksum)) {
            throw new BizException(500, "Article body archive checksum mismatch");
        }
        return new ArticleBody(versionId, new String(bytes, StandardCharsets.UTF_8), "object_storage", checksum);
    }

    public ArticleDraftVersion hydrateContent(ArticleDraftVersion version) {
        ArticleBody body = getArticleBody(version);
        version.setContentMarkdown(body.markdown());
        return version;
    }

    private String sha256Hex(String value) {
        return sha256Hex(value.getBytes(StandardCharsets.UTF_8));
    }

    private String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    public record ArticleBody(Long versionId, String markdown, String source, String checksum) {
    }
}
