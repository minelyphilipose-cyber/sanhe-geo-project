package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.entity.ArticleBatch;
import com.huanjing.geo.module.content.mapper.ArticleBatchMapper;
import com.huanjing.geo.module.content.mapper.ArticleGenerationLogMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArticleGenerationPersistenceServiceTest {

    private ArticleBatchMapper articleBatchMapper;
    private ArticleGenerationPersistenceService service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ArticleBatch.class);
        articleBatchMapper = mock(ArticleBatchMapper.class);
        service = new ArticleGenerationPersistenceService(
                articleBatchMapper,
                mock(ArticleGenerationLogMapper.class),
                mock(ContentArticleService.class)
        );
    }

    @Test
    void ensureArticleBatchCreatesWhenNoActiveBatchExists() {
        when(articleBatchMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        ArticleBatch batch = service.ensureArticleBatch(21L, 100L, LocalDate.of(2026, 5, 9),
                2, "industry_site", 2);

        ArgumentCaptor<ArticleBatch> captor = ArgumentCaptor.forClass(ArticleBatch.class);
        verify(articleBatchMapper).insert(captor.capture());
        ArticleBatch inserted = captor.getValue();
        assertSame(batch, inserted);
        assertEquals(21L, inserted.getDispatchTaskId());
        assertEquals("industry_site", inserted.getTargetChannel());
        assertEquals(2, inserted.getGenerationSlotNo());
        assertEquals("running", inserted.getStatus());
    }

    @Test
    void ensureArticleBatchReusesSingleActiveBatch() {
        ArticleBatch existing = new ArticleBatch();
        existing.setId(31L);
        when(articleBatchMapper.selectList(any(Wrapper.class))).thenReturn(List.of(existing));

        ArticleBatch batch = service.ensureArticleBatch(22L, 100L, LocalDate.of(2026, 5, 9),
                1, "official_site", 1);

        assertSame(existing, batch);
        verify(articleBatchMapper, never()).insert(any());
    }

    @Test
    void ensureArticleBatchRejectsAmbiguousActiveBatches() {
        ArticleBatch first = new ArticleBatch();
        first.setId(41L);
        ArticleBatch second = new ArticleBatch();
        second.setId(42L);
        when(articleBatchMapper.selectList(any(Wrapper.class))).thenReturn(List.of(first, second));

        assertThrows(BizException.class, () -> service.ensureArticleBatch(23L, 100L,
                LocalDate.of(2026, 5, 9), 1, "official_site", 1));

        verify(articleBatchMapper, never()).insert(any());
    }
}
