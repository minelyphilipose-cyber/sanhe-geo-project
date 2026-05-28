package com.huanjing.geo.module.customer.controller;

import com.huanjing.geo.module.customer.entity.BrandMaterial;
import com.huanjing.geo.module.customer.service.BrandMaterialPublicUrlService;
import com.huanjing.geo.module.customer.service.BrandProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicBrandMaterialControllerTest {

    @Test
    void stream_doesNotTrustStoredFileSizeAsContentLength() {
        BrandMaterial material = new BrandMaterial();
        material.setId(10L);
        material.setBrandId(1L);
        material.setFileType("jpg");
        material.setFileSize(12L);

        BrandMaterialPublicUrlService publicUrlService = mock(BrandMaterialPublicUrlService.class);
        BrandProfileService brandProfileService = mock(BrandProfileService.class);
        when(publicUrlService.verifyPublicAccess(10L, "sig")).thenReturn(material);
        when(brandProfileService.openVerifiedMaterialStream(material))
                .thenReturn(new ByteArrayInputStream("full-image-bytes".getBytes()));

        PublicBrandMaterialController controller = new PublicBrandMaterialController(publicUrlService, brandProfileService);
        ResponseEntity<StreamingResponseBody> response = controller.stream(10L, "sig");

        assertThat(response.getHeaders().containsKey(HttpHeaders.CONTENT_LENGTH)).isFalse();
    }
}
