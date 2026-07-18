package com.huanjing.geo.module.content.service;

import com.huanjing.geo.module.customer.entity.BrandImageFolder;
import com.huanjing.geo.module.customer.entity.BrandMaterial;
import com.huanjing.geo.module.customer.mapper.BrandImageFolderMapper;
import com.huanjing.geo.module.customer.mapper.BrandMaterialMapper;
import com.huanjing.geo.module.customer.service.BrandMaterialPublicUrlService;
import com.huanjing.geo.module.project.entity.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArticleAutoImageInsertionServiceTest {

    private BrandImageFolderMapper folderMapper;
    private BrandMaterialMapper brandMaterialMapper;
    private BrandMaterialPublicUrlService publicUrlService;
    private ArticleAutoImageInsertionService service;

    @BeforeEach
    void setUp() {
        folderMapper = mock(BrandImageFolderMapper.class);
        brandMaterialMapper = mock(BrandMaterialMapper.class);
        publicUrlService = mock(BrandMaterialPublicUrlService.class);
        service = new ArticleAutoImageInsertionService(folderMapper, brandMaterialMapper, publicUrlService);
    }

    @Test
    void insertForChannel_addsPublicImagesAfterFrontAndBackParagraphs() {
        BrandMaterial first = material(1L, "第一张.png", "https://cdn.example.com/raw-a.png");
        BrandMaterial second = material(2L, "第二张.png", "https://cdn.example.com/raw-b.png");
        when(folderMapper.selectList(any())).thenReturn(List.of(folder(100L, "插图_产品")));
        when(brandMaterialMapper.selectList(any())).thenReturn(List.of(first, second));
        when(publicUrlService.buildPublicStreamUrl(first))
                .thenReturn("https://app.example.com/api/public/brand-materials/1/stream?sig=a");
        when(publicUrlService.buildPublicStreamUrl(second))
                .thenReturn("https://app.example.com/api/public/brand-materials/2/stream?sig=b");

        String result = service.insertForChannel(project(), "industry_site", """
                # 标题

                %s

                %s

                %s

                %s
                """.formatted(paragraph("第一段文字。", 30), paragraph("第二段文字。", 30),
                paragraph("第三段文字。", 30), paragraph("第四段文字。", 30)));

        assertThat(result).contains("api/public/brand-materials/1/stream?sig=a");
        assertThat(result).contains("api/public/brand-materials/2/stream?sig=b");
        assertThat(result).contains("![第一张.png](https://app.example.com/api/public/brand-materials/1/stream?sig=a)");
        assertThat(result).contains("![第二张.png](https://app.example.com/api/public/brand-materials/2/stream?sig=b)");
        assertThat(result).doesNotContain("<img");
        assertThat(result).doesNotContain("<p>");
        assertThat(result.indexOf("api/public/brand-materials/"))
                .isGreaterThan(result.indexOf("第一段文字。"))
                .isLessThan(result.indexOf("第二段文字。"));
        assertThat(result.lastIndexOf("api/public/brand-materials/"))
                .isGreaterThan(result.indexOf("第三段文字。"));
    }

    @Test
    void insertForChannel_ignoresNonTargetChannels() {
        String markdown = "正文";

        String result = service.insertForChannel(project(), "self_media", "xiaohongshu", markdown, null);

        assertThat(result).isEqualTo(markdown);
        verify(folderMapper, never()).selectList(any());
        verify(brandMaterialMapper, never()).selectList(any());
    }

    @Test
    void insertForChannel_addsImagesOnlyForSelfMediaThatAllowBodyImages() {
        BrandMaterial image = material(1L, "配图.png", "https://cdn.example.com/image.png");
        when(folderMapper.selectList(any())).thenReturn(List.of(folder(100L, "插图_1")));
        when(brandMaterialMapper.selectList(any())).thenReturn(List.of(image));
        when(publicUrlService.buildPublicStreamUrl(image))
                .thenReturn("https://app.example.com/api/public/brand-materials/1/stream");

        String wechat = service.insertForChannel(project(), "self_media", "wechat", """
                # 标题

                第一段文字。

                第二段文字。
                """, null);

        assertThat(wechat).contains("![配图.png](https://app.example.com/api/public/brand-materials/1/stream)");

        String douyin = service.insertForChannel(project(), "self_media", "douyin", "正文", null);
        String xiaohongshu = service.insertForChannel(project(), "self_media", "xiaohongshu", "正文", null);
        String toutiao = service.insertForChannel(project(), "self_media", "toutiao", "正文", null);

        assertThat(douyin).isEqualTo("正文");
        assertThat(xiaohongshu).isEqualTo("正文");
        assertThat(toutiao).isEqualTo("正文");
    }

    @Test
    void insertForChannel_stripsExistingImagesForToutiao() {
        String result = service.insertForChannel(project(), "self_media", "toutiao", """
                # 标题

                第一段文字。

                ![品牌配图](https://app.example.com/api/public/brand-materials/1/stream)

                第二段文字。<img src="https://cdn.example.com/inline.png" alt="内嵌图">

                <p><img src="https://cdn.example.com/block.png" alt="段落图"></p>

                [保留链接](https://example.com)
                """, null);

        assertThat(result).contains("# 标题");
        assertThat(result).contains("第一段文字。");
        assertThat(result).contains("第二段文字。");
        assertThat(result).contains("[保留链接](https://example.com)");
        assertThat(result).doesNotContain("![");
        assertThat(result).doesNotContain("<img");
        assertThat(result).doesNotContain("brand-materials");
        assertThat(result).doesNotContain("cdn.example.com");
        verify(folderMapper, never()).selectList(any());
        verify(brandMaterialMapper, never()).selectList(any());
    }

    @Test
    void insertForChannel_usesDynamicImageCountByArticleLength() {
        BrandMaterial first = material(1L, "第一张.png", "https://cdn.example.com/raw-a.png");
        BrandMaterial second = material(2L, "第二张.png", "https://cdn.example.com/raw-b.png");
        BrandMaterial third = material(3L, "第三张.png", "https://cdn.example.com/raw-c.png");
        when(folderMapper.selectList(any())).thenReturn(List.of(folder(100L, "插图_1")));
        when(brandMaterialMapper.selectList(any())).thenReturn(List.of(first, second, third));
        when(publicUrlService.buildPublicStreamUrl(first))
                .thenReturn("https://app.example.com/api/public/brand-materials/1/stream");
        when(publicUrlService.buildPublicStreamUrl(second))
                .thenReturn("https://app.example.com/api/public/brand-materials/2/stream");
        when(publicUrlService.buildPublicStreamUrl(third))
                .thenReturn("https://app.example.com/api/public/brand-materials/3/stream");

        String shortArticle = service.insertForChannel(project(), "industry_site", null, "短正文", null);
        String longArticle = service.insertForChannel(project(), "industry_site", null, """
                # 标题

                %s

                %s

                %s

                %s
                """.formatted(paragraph("第一段长正文。", 60), paragraph("第二段长正文。", 60),
                paragraph("第三段长正文。", 60), paragraph("第四段长正文。", 60)), null);

        assertThat(countOccurrences(shortArticle, "api/public/brand-materials/")).isEqualTo(1);
        assertThat(countOccurrences(longArticle, "api/public/brand-materials/")).isEqualTo(3);
    }

    @Test
    void insertForChannel_excludesExistingMarkdownImagesAndCoverImage() {
        BrandMaterial existing = material(1L, "已有图.png", "https://cdn.example.com/existing.png");
        BrandMaterial cover = material(2L, "封面.png", "https://cdn.example.com/cover.png");
        BrandMaterial usable = material(3L, "可用图.png", "https://cdn.example.com/usable.png");
        when(folderMapper.selectList(any())).thenReturn(List.of(folder(100L, "插图_1")));
        when(brandMaterialMapper.selectList(any())).thenReturn(List.of(existing, cover, usable));
        when(publicUrlService.buildPublicStreamUrl(existing))
                .thenReturn("https://app.example.com/api/public/brand-materials/1/stream");
        when(publicUrlService.buildPublicStreamUrl(cover))
                .thenReturn("https://app.example.com/api/public/brand-materials/2/stream");
        when(publicUrlService.buildPublicStreamUrl(usable))
                .thenReturn("https://app.example.com/api/public/brand-materials/3/stream");

        String result = service.insertForChannel(project(), "authority_media", null, """
                正文第一段。

                ![已有图](https://app.example.com/api/public/brand-materials/1/stream)

                正文第二段。
                """, "https://app.example.com/api/public/brand-materials/2/stream");

        assertThat(countOccurrences(result, "brand-materials/1/stream")).isEqualTo(1);
        assertThat(result).doesNotContain("brand-materials/2/stream");
        assertThat(result).contains("![可用图.png](https://app.example.com/api/public/brand-materials/3/stream)");
    }

    @Test
    void insertForChannel_fallbacksToWholeBrandLibraryWithoutIllustrationFolder() {
        BrandMaterial image = material(1L, "兜底图.png", "https://cdn.example.com/image.png");
        when(folderMapper.selectList(any())).thenReturn(List.of());
        when(brandMaterialMapper.selectList(any())).thenReturn(List.of(image));
        when(publicUrlService.buildPublicStreamUrl(image))
                .thenReturn("https://app.example.com/api/public/brand-materials/1/stream");

        String result = service.insertForChannel(project(), "authority_media", null, "正文", null);

        assertThat(result).contains("![兜底图.png](https://app.example.com/api/public/brand-materials/1/stream)");
    }

    @Test
    void insertForChannel_stripsExistingImagesForDouyinBody() {
        String result = service.insertForChannel(project(), "self_media", "douyin", """
                # 标题

                第一段文字。

                ![正文配图](https://app.example.com/api/public/brand-materials/2/stream)

                第二段文字。<img src="https://cdn.example.com/inline.png" alt="内嵌图">
                """, "https://app.example.com/api/public/brand-materials/1/stream");

        assertThat(result).contains("# 标题");
        assertThat(result).contains("第一段文字。");
        assertThat(result).contains("第二段文字。");
        assertThat(result).doesNotContain("![");
        assertThat(result).doesNotContain("<img");
        verify(folderMapper, never()).selectList(any());
        verify(brandMaterialMapper, never()).selectList(any());
    }

    @Test
    void insertForChannel_skipsCodeBlocksTablesExistingImagesAndTrailingSections() {
        BrandMaterial image = material(1L, "配图.png", "https://cdn.example.com/image.png");
        when(folderMapper.selectList(any())).thenReturn(List.of(folder(100L, "插图_1")));
        when(brandMaterialMapper.selectList(any())).thenReturn(List.of(image));
        when(publicUrlService.buildPublicStreamUrl(image))
                .thenReturn("https://app.example.com/api/public/brand-materials/1/stream");

        String result = service.insertForChannel(project(), "forum", null, """
                # 标题

                ```
                代码里的文字不应作为插入点。
                ```

                | 指标 | 值 |
                | --- | --- |
                | A | B |

                正常正文第一段。

                ![已有图](https://app.example.com/existing.png)

                ## 结语

                收尾内容。
                """, null);

        assertThat(result.indexOf("brand-materials/1/stream"))
                .isGreaterThan(result.indexOf("正常正文第一段。"))
                .isLessThan(result.indexOf("![已有图]"));
    }

    @Test
    void insertForChannel_filtersUnsupportedAndEmptyImageFiles() {
        BrandMaterial gif = material(1L, "动图.gif", "gif", 100L);
        BrandMaterial svg = material(2L, "矢量图.svg", "svg", 100L);
        BrandMaterial empty = material(3L, "空图.png", "png", 0L);
        BrandMaterial usable = material(4L, "可用图.webp", "webp", 100L);
        when(folderMapper.selectList(any())).thenReturn(List.of(folder(100L, "插图_1")));
        when(brandMaterialMapper.selectList(any())).thenReturn(List.of(gif, svg, empty, usable));
        when(publicUrlService.buildPublicStreamUrl(usable))
                .thenReturn("https://app.example.com/api/public/brand-materials/4/stream");

        String result = service.insertForChannel(project(), "agent_site", null, "正文", null);

        assertThat(result).contains("![可用图.webp](https://app.example.com/api/public/brand-materials/4/stream)");
        assertThat(result).doesNotContain("动图.gif");
        assertThat(result).doesNotContain("矢量图.svg");
        assertThat(result).doesNotContain("空图.png");
    }

    private Project project() {
        Project project = new Project();
        project.setBrandId(10L);
        return project;
    }

    private BrandMaterial material(Long id, String name, String fileUrl) {
        BrandMaterial material = new BrandMaterial();
        material.setId(id);
        material.setBrandId(10L);
        material.setFolderId(100L);
        material.setCategory("brand_image");
        material.setFileName(name);
        material.setFileType("png");
        material.setFileUrl(fileUrl);
        material.setObjectKey("brand/" + id + ".png");
        material.setFileSize(100L);
        return material;
    }

    private BrandMaterial material(Long id, String name, String fileType, Long fileSize) {
        BrandMaterial material = new BrandMaterial();
        material.setId(id);
        material.setBrandId(10L);
        material.setFolderId(100L);
        material.setCategory("brand_image");
        material.setFileName(name);
        material.setFileType(fileType);
        material.setFileUrl("https://cdn.example.com/" + id + "." + fileType);
        material.setObjectKey("brand/" + id + "." + fileType);
        material.setFileSize(fileSize);
        return material;
    }

    private BrandImageFolder folder(Long id, String name) {
        BrandImageFolder folder = new BrandImageFolder();
        folder.setId(id);
        folder.setBrandId(10L);
        folder.setFolderName(name);
        folder.setStatus("active");
        return folder;
    }

    private String paragraph(String text, int repeat) {
        return text.repeat(repeat);
    }

    private int countOccurrences(String value, String needle) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
