package com.huanjing.geo.module.retention.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArticleArchivePurgeCosPrefixContractTest {

    @Test
    void createsAnIsolatedGeneratedRunPrefixBelowTheRequiredRoot() {
        String prefix = ArticleArchivePurgeMysqlCosIntegrationTest.safeRunPrefix(
                "geo-retention-it/article-archive/");

        assertThat(prefix)
                .matches("^geo-retention-it/article-archive/run-[a-f0-9]{32}/$");
        ArticleArchivePurgeMysqlCosIntegrationTest.requireSafeGeneratedPrefix(prefix);
    }

    @Test
    void rejectsBucketRootsTraversalAndNonGeneratedCleanupPrefixes() {
        assertThatThrownBy(() -> ArticleArchivePurgeMysqlCosIntegrationTest.safeRunPrefix("/"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ArticleArchivePurgeMysqlCosIntegrationTest.safeRunPrefix("archive/article/"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ArticleArchivePurgeMysqlCosIntegrationTest.safeRunPrefix(
                "geo-retention-it/../archive/"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ArticleArchivePurgeMysqlCosIntegrationTest.requireSafeGeneratedPrefix(
                "geo-retention-it/article-archive/"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ArticleArchivePurgeMysqlCosIntegrationTest.requireSafeGeneratedPrefix(
                "geo-retention-it/run-0123456789abcdef0123456789abcdef/extra/"))
                .isInstanceOf(IllegalStateException.class);
    }
}
