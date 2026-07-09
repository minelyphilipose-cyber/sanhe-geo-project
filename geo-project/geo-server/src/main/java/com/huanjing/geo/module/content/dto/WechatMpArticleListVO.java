package com.huanjing.geo.module.content.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class WechatMpArticleListVO {
    private String brandName;
    private String accountName;
    private String visualUrl;
    private String publicPhone;
    private String publicAddress;
    private Integer page;
    private Integer size;
    private Long total;
    private List<ArticleItem> articles = new ArrayList<>();

    @Data
    public static class ArticleItem {
        private Long id;
        private String title;
        private String digest;
        private String coverUrl;
        private String articleUrl;
        private String platformArticleId;
        private LocalDateTime publishedAt;
    }
}
