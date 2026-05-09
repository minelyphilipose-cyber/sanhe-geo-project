package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.module.content.entity.ArticleBatch;
import com.huanjing.geo.module.content.entity.ArticleGenerationLog;
import com.huanjing.geo.module.content.mapper.ArticleBatchMapper;
import com.huanjing.geo.module.content.mapper.ArticleGenerationLogMapper;
import com.huanjing.geo.module.project.entity.Project;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ArticleGenerationPersistenceService {

    private final ArticleBatchMapper articleBatchMapper;
    private final ArticleGenerationLogMapper articleGenerationLogMapper;
    private final ContentArticleService contentArticleService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ArticleBatch ensureArticleBatch(Long taskId, Long projectId, LocalDate batchDate, int batchNo) {
        ArticleBatch existing = articleBatchMapper.selectOne(
                new LambdaQueryWrapper<ArticleBatch>()
                        .eq(ArticleBatch::getProjectId, projectId)
                        .eq(ArticleBatch::getBatchDate, batchDate)
                        .eq(ArticleBatch::getBatchNo, batchNo)
                        .last("LIMIT 1")
        );
        int actualBatchNo = batchNo;
        if (existing != null) {
            existing.setStatus("superseded");
            articleBatchMapper.updateById(existing);
            actualBatchNo = resolveNextArticleBatchNo(projectId, batchDate);
        }

        ArticleBatch batch = new ArticleBatch();
        batch.setDispatchTaskId(taskId);
        batch.setProjectId(projectId);
        batch.setBatchDate(batchDate);
        batch.setBatchNo(actualBatchNo);
        batch.setStatus("running");
        batch.setTotalCount(0);
        batch.setCompletedCount(0);
        batch.setFailedCount(0);
        articleBatchMapper.insert(batch);
        return batch;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistGeneratedArticle(Long batchId,
                                        Project project,
                                        String articleType,
                                        String title,
                                        String contentMarkdown,
                                        String promptSnapshot,
                                        String inputSnapshot,
                                        String platformCode,
                                        String modelId,
                                        String articleAngle) {
        contentArticleService.createGeneratedDraft(
                batchId,
                project,
                articleType,
                title,
                contentMarkdown,
                promptSnapshot,
                inputSnapshot,
                platformCode,
                modelId
        );

        ArticleGenerationLog row = new ArticleGenerationLog();
        row.setProjectId(project.getId());
        row.setArticleType(articleType);
        row.setArticleAngle(articleAngle);
        row.setGeneratedTitle(title);
        row.setModelCode(platformCode);
        articleGenerationLogMapper.insert(row);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeBatch(Long batchId, int total, int completed, int failed) {
        ArticleBatch batch = articleBatchMapper.selectById(batchId);
        if (batch == null) {
            return;
        }
        batch.setTotalCount(total);
        batch.setCompletedCount(completed);
        batch.setFailedCount(failed);
        if (completed > 0 && failed > 0) {
            batch.setStatus("completed_with_failure");
        } else if (completed > 0) {
            batch.setStatus("completed");
        } else if (failed > 0) {
            batch.setStatus("failed");
        } else {
            batch.setStatus("completed");
        }
        articleBatchMapper.updateById(batch);
    }

    private int resolveNextArticleBatchNo(Long projectId, LocalDate batchDate) {
        Integer maxBatchNo = articleBatchMapper.selectList(
                new LambdaQueryWrapper<ArticleBatch>()
                        .eq(ArticleBatch::getProjectId, projectId)
                        .eq(ArticleBatch::getBatchDate, batchDate)
                        .select(ArticleBatch::getBatchNo)
        ).stream()
                .map(ArticleBatch::getBatchNo)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0);
        return Math.max(maxBatchNo, 0) + 1;
    }
}
