package com.huanjing.geo.module.content.service;

import com.huanjing.geo.module.customer.entity.BrandMaterial;
import com.huanjing.geo.module.customer.mapper.BrandMaterialMapper;
import com.huanjing.geo.module.customer.service.BrandMaterialPublicUrlService;
import com.huanjing.geo.module.project.entity.Project;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ArticleImagePublicUrlRewriterTest {

    @Test
    void rewriteUrlRefreshesSignedPublicBrandMaterialUrl() {
        BrandMaterialMapper materialMapper = mock(BrandMaterialMapper.class);
        BrandMaterialPublicUrlService publicUrlService = mock(BrandMaterialPublicUrlService.class);
        ArticleImagePublicUrlRewriter rewriter = new ArticleImagePublicUrlRewriter(materialMapper, publicUrlService);

        Project project = new Project();
        project.setBrandId(8L);
        BrandMaterial material = new BrandMaterial();
        material.setId(48L);
        material.setBrandId(8L);
        material.setCategory("brand_image");
        material.setObjectKey("brands/8/cover.jpg");

        when(materialMapper.selectById(48L)).thenReturn(material);
        when(publicUrlService.buildPublicStreamUrl(material))
                .thenReturn("http://127.0.0.1:8080/api/public/brand-materials/48/stream?sig=fresh");

        String oldUrl = "http://127.0.0.1:8080/api/public/brand-materials/48/stream?sig=stale&v=1";

        assertEquals(
                "http://127.0.0.1:8080/api/public/brand-materials/48/stream?sig=fresh",
                rewriter.rewriteUrl(project, oldUrl)
        );
    }
}
