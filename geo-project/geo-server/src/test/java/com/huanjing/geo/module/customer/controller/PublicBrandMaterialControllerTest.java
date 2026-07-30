package com.huanjing.geo.module.customer.controller;

import com.huanjing.geo.module.customer.entity.BrandMaterial;
import com.huanjing.geo.module.customer.service.BrandMaterialPublicUrlService;
import com.huanjing.geo.module.customer.service.BrandProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicBrandMaterialControllerTest {

    @Test
    void stream_usesActualObjectBytesAsContentLength() {
        BrandMaterial material = new BrandMaterial();
        material.setId(10L);
        material.setBrandId(1L);
        material.setFileType("jpg");
        material.setFileSize(12L);

        BrandMaterialPublicUrlService publicUrlService = mock(BrandMaterialPublicUrlService.class);
        BrandProfileService brandProfileService = mock(BrandProfileService.class);
        when(publicUrlService.verifyPublicAccess(10L, "sig")).thenReturn(material);
        byte[] bytes = "full-image-bytes".getBytes();
        when(brandProfileService.readVerifiedMaterialBytes(material)).thenReturn(bytes);

        PublicBrandMaterialController controller = new PublicBrandMaterialController(publicUrlService, brandProfileService);
        ResponseEntity<byte[]> response = controller.stream(10L, "sig");

        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_LENGTH)).isEqualTo(String.valueOf(bytes.length));
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).isEqualTo("inline");
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_JPEG);
        assertThat(response.getBody()).isEqualTo(bytes);
    }
}
