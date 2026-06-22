package com.huanjing.geo.module.customer.controller;

import com.huanjing.geo.module.content.service.ThirdPartySubjectRotationService;
import com.huanjing.geo.module.customer.entity.BrandMaterial;
import com.huanjing.geo.module.customer.service.BrandImageFolderService;
import com.huanjing.geo.module.customer.service.BrandOfferingService;
import com.huanjing.geo.module.customer.service.BrandProfileService;
import com.huanjing.geo.module.customer.service.BrandService;
import com.huanjing.geo.module.customer.service.BrandStatementService;
import com.huanjing.geo.module.dispatch.service.BrandStatementDispatchService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BrandControllerTest {

    @Test
    void streamMaterial_usesActualObjectBytesAsContentLength() {
        BrandMaterial material = new BrandMaterial();
        material.setId(10L);
        material.setBrandId(1L);
        material.setFileName("image.jpg");
        material.setFileSize(508_518L);

        byte[] bytes = "complete-image-bytes".getBytes();
        BrandProfileService brandProfileService = mock(BrandProfileService.class);
        when(brandProfileService.materialDetail(1L, 10L)).thenReturn(material);
        when(brandProfileService.readVerifiedMaterialBytes(material)).thenReturn(bytes);

        BrandController controller = new BrandController(
                mock(BrandService.class),
                mock(BrandOfferingService.class),
                mock(BrandImageFolderService.class),
                brandProfileService,
                mock(BrandStatementService.class),
                mock(BrandStatementDispatchService.class),
                mock(ThirdPartySubjectRotationService.class)
        );

        ResponseEntity<byte[]> response = controller.streamMaterial(1L, 10L, true);

        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_LENGTH)).isEqualTo(String.valueOf(bytes.length));
        assertThat(response.getBody()).isEqualTo(bytes);
    }
}
