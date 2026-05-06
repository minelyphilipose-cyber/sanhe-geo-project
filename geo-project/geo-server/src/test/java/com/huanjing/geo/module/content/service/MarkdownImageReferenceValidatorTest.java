package com.huanjing.geo.module.content.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.customer.entity.BrandMaterial;
import com.huanjing.geo.module.customer.mapper.BrandMaterialMapper;
import com.huanjing.geo.module.project.entity.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarkdownImageReferenceValidatorTest {

    private BrandMaterialMapper brandMaterialMapper;
    private MarkdownImageReferenceValidator validator;

    @BeforeEach
    void setUp() {
        brandMaterialMapper = mock(BrandMaterialMapper.class);
        validator = new MarkdownImageReferenceValidator(brandMaterialMapper);
    }

    @Test
    void validate_brandImageUrl_passes() {
        String url = "https://cdn.example.com/geo-files/brand/1/a.png";
        when(brandMaterialMapper.selectList(any())).thenReturn(List.of(material(url, "png")));

        assertDoesNotThrow(() -> validator.validate(project(1L), "![产品图](" + url + ")"));
    }

    @Test
    void validate_externalImageUrl_rejects() {
        when(brandMaterialMapper.selectList(any())).thenReturn(List.of());

        BizException ex = assertThrows(BizException.class,
                () -> validator.validate(project(1L), "![产品图](https://other.example.com/a.png)"));
        assertEquals(400, ex.getCode());
    }

    @Test
    void validate_dataImage_rejects() {
        BizException ex = assertThrows(BizException.class,
                () -> validator.validate(project(1L), "![产品图](data:image/png;base64,abc)"));
        assertEquals(400, ex.getCode());
    }

    @Test
    void extractImageUrls_readsMarkdownAndHtmlImages() {
        Set<String> urls = validator.extractImageUrls("""
                ![a](https://cdn.example.com/a.png)
                <img src="https://cdn.example.com/b.jpg">
                """);

        assertEquals(Set.of("https://cdn.example.com/a.png", "https://cdn.example.com/b.jpg"), urls);
    }

    private Project project(Long brandId) {
        Project project = new Project();
        project.setBrandId(brandId);
        return project;
    }

    private BrandMaterial material(String fileUrl, String fileType) {
        BrandMaterial material = new BrandMaterial();
        material.setFileUrl(fileUrl);
        material.setFileType(fileType);
        material.setCategory("brand_image");
        return material;
    }
}
