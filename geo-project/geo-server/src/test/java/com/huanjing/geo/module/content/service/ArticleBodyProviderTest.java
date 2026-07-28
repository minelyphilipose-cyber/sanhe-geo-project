package com.huanjing.geo.module.content.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.storage.ObjectStorageService;
import com.huanjing.geo.module.content.entity.ArticleDraftVersion;
import com.huanjing.geo.module.content.mapper.ArticleDraftVersionMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArticleBodyProviderTest {

    private final ArticleDraftVersionMapper versionMapper = mock(ArticleDraftVersionMapper.class);
    private final ObjectStorageService objectStorageService = mock(ObjectStorageService.class);
    private final ArticleBodyProvider provider = new ArticleBodyProvider(versionMapper, objectStorageService);

    @Test
    void returnsDatabaseBodyWithoutReadingObjectStorage() {
        ArticleDraftVersion version = new ArticleDraftVersion();
        version.setId(10L);
        version.setContentMarkdown("# current");

        ArticleBodyProvider.ArticleBody body = provider.getArticleBody(version);

        assertEquals("# current", body.markdown());
        assertEquals("db", body.source());
        verify(objectStorageService, never()).readBytes(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void returnsArchivedBodyAfterDatabaseBodyWasPurged() {
        byte[] archived = "# archived".getBytes(StandardCharsets.UTF_8);
        ArticleDraftVersion version = archivedVersion(archived);
        when(objectStorageService.readBytes("archive/article/10.md")).thenReturn(archived);

        ArticleBodyProvider.ArticleBody body = provider.getArticleBody(version);

        assertEquals("# archived", body.markdown());
        assertEquals("object_storage", body.source());
        assertEquals(sha256(archived), body.checksum());
    }

    @Test
    void rejectsArchivedBodyWhenChecksumDoesNotMatch() {
        ArticleDraftVersion version = archivedVersion("# expected".getBytes(StandardCharsets.UTF_8));
        when(objectStorageService.readBytes("archive/article/10.md"))
                .thenReturn("# tampered".getBytes(StandardCharsets.UTF_8));

        BizException error = assertThrows(BizException.class, () -> provider.getArticleBody(version));

        assertEquals(500, error.getCode());
        assertEquals("Article body archive checksum mismatch", error.getMessage());
    }

    @Test
    void requiresChecksumAfterDatabaseBodyWasPurged() {
        ArticleDraftVersion version = archivedVersion("# archived".getBytes(StandardCharsets.UTF_8));
        version.setContentChecksum(null);
        when(objectStorageService.readBytes("archive/article/10.md"))
                .thenReturn("# archived".getBytes(StandardCharsets.UTF_8));

        BizException error = assertThrows(BizException.class, () -> provider.getArticleBody(version));

        assertEquals(500, error.getCode());
        assertEquals("Article body archive checksum is missing", error.getMessage());
    }

    private ArticleDraftVersion archivedVersion(byte[] body) {
        ArticleDraftVersion version = new ArticleDraftVersion();
        version.setId(10L);
        version.setContentMarkdown(null);
        version.setContentObjectKey("archive/article/10.md");
        version.setContentChecksum(sha256(body));
        version.setContentPurgedAt(LocalDateTime.now());
        return version;
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
