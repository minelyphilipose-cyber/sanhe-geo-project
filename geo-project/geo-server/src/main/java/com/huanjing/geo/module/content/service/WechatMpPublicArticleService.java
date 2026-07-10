package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.dto.WechatMpArticleListVO;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.entity.WechatMenuConfig;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.content.mapper.WechatMenuConfigMapper;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WechatMpPublicArticleService {
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;
    private static final Set<String> VISIBLE_STATUSES = Set.of("published", "published_confirmed", "distributed");

    private final WechatMenuConfigMapper menuConfigMapper;
    private final SelfMediaAccountMapper selfMediaAccountMapper;
    private final BrandMapper brandMapper;
    private final JdbcTemplate jdbcTemplate;

    public WechatMpArticleListVO list(String publicSlug, Integer page, Integer size) {
        if (!StringUtils.hasText(publicSlug)) {
            throw new BizException(404, "wechat article page not found");
        }
        WechatMenuConfig config = menuConfigMapper.selectOne(new LambdaQueryWrapper<WechatMenuConfig>()
                .eq(WechatMenuConfig::getPublicSlug, publicSlug.trim())
                .last("LIMIT 1"));
        if (config == null || "disabled".equalsIgnoreCase(config.getMenuStatus())) {
            throw new BizException(404, "wechat article page not found");
        }
        SelfMediaAccount account = selfMediaAccountMapper.selectById(config.getSelfMediaAccountId());
        Brand brand = brandMapper.selectById(config.getBrandId());
        int pageNo = Math.max(page == null ? 1 : page, 1);
        int pageSize = Math.max(1, Math.min(size == null ? DEFAULT_SIZE : size, MAX_SIZE));
        int offset = (pageNo - 1) * pageSize;

        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                  FROM article_publish_record r
                 WHERE r.target_channel = 'wechat_mp'
                   AND r.self_media_account_id = ?
                   AND r.publish_status IN ('published', 'published_confirmed', 'distributed')
                   AND NULLIF(TRIM(r.published_url), '') IS NOT NULL
                """, Long.class, config.getSelfMediaAccountId());
        List<WechatMpArticleListVO.ArticleItem> articles = jdbcTemplate.query("""
                SELECT r.id,
                       COALESCE(NULLIF(TRIM(r.title), ''), NULLIF(TRIM(ad.title), ''), '未命名文章') AS title,
                       NULLIF(TRIM(r.digest), '') AS digest,
                       COALESCE(NULLIF(TRIM(r.cover_url), ''), NULLIF(TRIM(ad.cover_image_url), '')) AS cover_url,
                       r.published_url,
                       COALESCE(NULLIF(TRIM(r.platform_article_id), ''), '') AS platform_article_id,
                       COALESCE(r.published_at, r.verified_at, r.created_at) AS published_at
                  FROM article_publish_record r
                  LEFT JOIN article_draft ad ON ad.id = r.article_id
                 WHERE r.target_channel = 'wechat_mp'
                   AND r.self_media_account_id = ?
                   AND r.publish_status IN ('published', 'published_confirmed', 'distributed')
                   AND NULLIF(TRIM(r.published_url), '') IS NOT NULL
                 ORDER BY COALESCE(r.published_at, r.verified_at, r.created_at) DESC, r.id DESC
                 LIMIT ? OFFSET ?
                """, (rs, rowNum) -> toItem(rs), config.getSelfMediaAccountId(), pageSize, offset);

        WechatMpArticleListVO vo = new WechatMpArticleListVO();
        vo.setBrandName(brand == null ? null : brand.getBrandName());
        vo.setAccountName(account == null ? null : account.getAccountName());
        vo.setVisualUrl(account == null ? null : account.getAvatarUrl());
        vo.setPublicPhone(brand == null ? null : brand.getPublicPhone());
        vo.setPublicAddress(brand == null ? null : brand.getPublicAddress());
        vo.setPage(pageNo);
        vo.setSize(pageSize);
        vo.setTotal(total == null ? 0L : total);
        vo.setArticles(articles);
        return vo;
    }

    private WechatMpArticleListVO.ArticleItem toItem(ResultSet rs) throws SQLException {
        WechatMpArticleListVO.ArticleItem item = new WechatMpArticleListVO.ArticleItem();
        item.setId(rs.getLong("id"));
        item.setTitle(rs.getString("title"));
        item.setDigest(rs.getString("digest"));
        item.setCoverUrl(rs.getString("cover_url"));
        item.setArticleUrl(rs.getString("published_url"));
        item.setPlatformArticleId(rs.getString("platform_article_id"));
        item.setPublishedAt(timestamp(rs, "published_at"));
        return item;
    }

    private LocalDateTime timestamp(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column) == null ? null : rs.getTimestamp(column).toLocalDateTime();
    }
}
