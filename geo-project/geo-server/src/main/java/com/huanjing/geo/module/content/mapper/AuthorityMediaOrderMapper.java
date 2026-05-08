package com.huanjing.geo.module.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.content.entity.AuthorityMediaOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AuthorityMediaOrderMapper extends BaseMapper<AuthorityMediaOrder> {

    @Select("SELECT * FROM authority_media_order WHERE distribution_task_id = #{distributionTaskId} LIMIT 1")
    AuthorityMediaOrder selectByDistributionTaskId(@Param("distributionTaskId") Long distributionTaskId);

    @Select("""
            SELECT *
            FROM authority_media_order
            WHERE article_id = #{articleId}
              AND resource_id = #{resourceId}
              AND submit_status IN ('created', 'submitting', 'submitted', 'submit_failed')
            ORDER BY id DESC
            LIMIT 1
            """)
    List<AuthorityMediaOrder> selectUnfinishedByArticleAndResource(@Param("articleId") Long articleId,
                                                                   @Param("resourceId") Long resourceId);

    @Select("""
            SELECT *
            FROM authority_media_order
            WHERE resource_type = 'NEWS_MEDIA'
              AND external_no IS NOT NULL
              AND submit_status IN ('submitted', 'submit_failed')
              AND (remote_status IS NULL OR remote_status IN (0, 1))
              AND (next_check_at IS NULL OR next_check_at <= #{now})
            ORDER BY COALESCE(next_check_at, submitted_at, created_at) ASC, id ASC
            LIMIT #{limit}
            """)
    List<AuthorityMediaOrder> selectDueForStatusCheck(@Param("now") LocalDateTime now,
                                                      @Param("limit") int limit);

    @Update("UPDATE authority_media_order " +
            "SET external_no = #{externalNo}, lock_version = lock_version + 1 " +
            "WHERE id = #{id} AND external_no IS NULL")
    int assignExternalNoIfAbsent(@Param("id") Long id, @Param("externalNo") String externalNo);

    @Update("UPDATE authority_media_order " +
            "SET submit_status = #{submitStatus}, submitted_at = #{submittedAt}, " +
            "    remote_status = #{remoteStatus}, remote_status_text = #{remoteStatusText}, " +
            "    request_payload = #{requestPayload}, response_payload = #{responsePayload}, " +
            "    lock_version = lock_version + 1 " +
            "WHERE id = #{id}")
    int updateSubmissionResult(@Param("id") Long id,
                               @Param("submitStatus") String submitStatus,
                               @Param("submittedAt") LocalDateTime submittedAt,
                               @Param("remoteStatus") Integer remoteStatus,
                               @Param("remoteStatusText") String remoteStatusText,
                               @Param("requestPayload") String requestPayload,
                               @Param("responsePayload") String responsePayload);

    @Update("UPDATE authority_media_order " +
            "SET remote_status = #{remoteStatus}, remote_status_text = #{remoteStatusText}, " +
            "    published_url = #{publishedUrl}, reject_reason = #{rejectReason}, " +
            "    remote_published_at = #{remotePublishedAt}, last_checked_at = #{checkedAt}, " +
            "    next_check_at = #{nextCheckAt}, response_payload = #{responsePayload}, " +
            "    extra_payload = #{extraPayload}, lock_version = lock_version + 1 " +
            "WHERE id = #{id} AND lock_version = #{lockVersion}")
    int updateRemoteStatus(@Param("id") Long id,
                           @Param("lockVersion") Integer lockVersion,
                           @Param("remoteStatus") Integer remoteStatus,
                           @Param("remoteStatusText") String remoteStatusText,
                           @Param("publishedUrl") String publishedUrl,
                           @Param("rejectReason") String rejectReason,
                           @Param("remotePublishedAt") LocalDateTime remotePublishedAt,
                           @Param("checkedAt") LocalDateTime checkedAt,
                           @Param("nextCheckAt") LocalDateTime nextCheckAt,
                           @Param("responsePayload") String responsePayload,
                           @Param("extraPayload") String extraPayload);
}
