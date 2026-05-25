package com.huanjing.geo.module.content.service.render.wechat;

import com.huanjing.geo.module.content.dto.render.WechatRenderDtos.ArticleBlock;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ArticleMarkdownBlockParserTest {

    private final ArticleMarkdownBlockParser parser = new ArticleMarkdownBlockParser();

    @Test
    void parseKeepsListAndTableAsNativeHtmlBlocksWithLockedRole() {
        String markdown = """
                - 第一项
                - 第二项

                | 城市 | 门店 |
                | --- | --- |
                | 北京 | 3 |
                """;

        List<ArticleBlock> blocks = parser.parse(markdown);

        assertThat(blocks).hasSize(2);
        assertThat(blocks.get(0).getType()).isEqualTo("list");
        assertThat(blocks.get(0).getDefaultRole()).isEqualTo("native_html");
        assertThat(blocks.get(0).getAllowedRoles()).containsExactly("native_html");
        assertThat(blocks.get(0).getHtml()).contains("<ul>", "<li>第一项</li>");
        assertThat(blocks.get(1).getType()).isEqualTo("table");
        assertThat(blocks.get(1).getDefaultRole()).isEqualTo("native_html");
        assertThat(blocks.get(1).getAllowedRoles()).containsExactly("native_html");
        assertThat(blocks.get(1).getHtml()).contains("<table>", "<td>北京</td>");
    }

    @Test
    void parseUsesSha256ContentHashAndStableIdWithoutOrder() {
        String markdown = """
                第一段

                第一段
                """;

        List<ArticleBlock> blocks = parser.parse(markdown);

        assertThat(blocks).hasSize(2);
        assertThat(blocks.get(0).getContentHash()).hasSize(64);
        assertThat(blocks.get(0).getContentHash()).isEqualTo(blocks.get(1).getContentHash());
        assertThat(blocks.get(0).getId()).isEqualTo(blocks.get(1).getId());
        assertThat(blocks.get(0).getOrder()).isNotEqualTo(blocks.get(1).getOrder());
    }

    @Test
    void parseMarksLeadingHeadingMatchingArticleTitleAsArticleTitle() {
        String markdown = """
                # 总在搜索“阜阳哪家SPA好”

                正文第一段

                ## 你现在最需要的
                """;

        List<ArticleBlock> blocks = parser.parse(markdown, "总在搜索“阜阳哪家SPA好”");

        assertThat(blocks).hasSize(3);
        assertThat(blocks.get(0).getType()).isEqualTo("article_title");
        assertThat(blocks.get(0).getDefaultRole()).isEqualTo("article_title");
        assertThat(blocks.get(0).getAllowedRoles()).containsExactly("article_title");
        assertThat(blocks.get(2).getType()).isEqualTo("heading");
        assertThat(blocks.get(2).getDefaultRole()).isEqualTo("heading");
    }
}
