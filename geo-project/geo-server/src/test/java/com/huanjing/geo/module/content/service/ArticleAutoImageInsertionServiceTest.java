package com.huanjing.geo.module.content.service;

import com.huanjing.geo.module.customer.entity.BrandMaterial;
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

    private BrandMaterialMapper brandMaterialMapper;
    private BrandMaterialPublicUrlService publicUrlService;
    private ArticleAutoImageInsertionService service;

    @BeforeEach
    void setUp() {
        brandMaterialMapper = mock(BrandMaterialMapper.class);
        publicUrlService = mock(BrandMaterialPublicUrlService.class);
        service = new ArticleAutoImageInsertionService(brandMaterialMapper, publicUrlService);
    }

    @Test
    void insertForChannel_addsPublicImagesAfterFrontAndBackParagraphs() {
        BrandMaterial first = material(1L, "第一张.png", "https://cdn.example.com/raw-a.png");
        BrandMaterial second = material(2L, "第二张.png", "https://cdn.example.com/raw-b.png");
        when(brandMaterialMapper.selectList(any())).thenReturn(List.of(first, second));
        when(publicUrlService.buildPublicStreamUrl(first))
                .thenReturn("https://app.example.com/api/public/brand-materials/1/stream?sig=a");
        when(publicUrlService.buildPublicStreamUrl(second))
                .thenReturn("https://app.example.com/api/public/brand-materials/2/stream?sig=b");

        String result = service.insertForChannel(project(), "industry_site", """
                # 标题

                第一段文字。

                第二段文字。

                第三段文字。

                第四段文字。
                """);

        assertThat(result).contains("api/public/brand-materials/1/stream?sig=a");
        assertThat(result).contains("api/public/brand-materials/2/stream?sig=b");
        assertThat(result).contains("![第一张.png](https://app.example.com/api/public/brand-materials/1/stream?sig=a)");
        assertThat(result).contains("![第二张.png](https://app.example.com/api/public/brand-materials/2/stream?sig=b)");
        assertThat(result).doesNotContain("<img");
        assertThat(result).doesNotContain("<p>");
        assertThat(result.indexOf("api/public/brand-materials/"))
                .isGreaterThan(result.indexOf("第二段文字。"))
                .isLessThan(result.indexOf("第三段文字。"));
        assertThat(result.lastIndexOf("api/public/brand-materials/"))
                .isGreaterThan(result.indexOf("第四段文字。"));
    }

    @Test
    void insertForChannel_ignoresNonTargetChannels() {
        String markdown = "正文";

        String result = service.insertForChannel(project(), "self_media", markdown);

        assertThat(result).isEqualTo(markdown);
        verify(brandMaterialMapper, never()).selectList(any());
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
        material.setCategory("brand_image");
        material.setFileName(name);
        material.setFileType("png");
        material.setFileUrl(fileUrl);
        material.setObjectKey("brand/" + id + ".png");
        return material;
    }
}
