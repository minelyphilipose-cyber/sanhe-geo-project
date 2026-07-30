package com.huanjing.geo.module.customer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.image.ImageCompressionService;
import com.huanjing.geo.common.storage.MinioStorageService;
import com.huanjing.geo.module.customer.access.InternalScopeService;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.entity.BrandMaterial;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.customer.mapper.BrandMaterialMapper;
import com.huanjing.geo.module.customer.mapper.BrandOfferingMapper;
import com.huanjing.geo.module.customer.mapper.BrandProfileVersionMapper;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.ActivityLogService;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BrandProfileServiceTest {

    private BrandMapper brandMapper;
    private CompanyMapper companyMapper;
    private BrandMaterialMapper brandMaterialMapper;
    private BrandOfferingMapper brandOfferingMapper;
    private BrandProfileVersionMapper brandProfileVersionMapper;
    private CurrentUserService currentUserService;
    private MinioStorageService minioStorageService;
    private BrandMaterialPublicUrlService publicUrlService;
    private BrandProfileService service;

    @BeforeEach
    void setUp() {
        brandMapper = mock(BrandMapper.class);
        companyMapper = mock(CompanyMapper.class);
        brandMaterialMapper = mock(BrandMaterialMapper.class);
        brandOfferingMapper = mock(BrandOfferingMapper.class);
        brandProfileVersionMapper = mock(BrandProfileVersionMapper.class);
        currentUserService = mock(CurrentUserService.class);
        minioStorageService = mock(MinioStorageService.class);
        publicUrlService = mock(BrandMaterialPublicUrlService.class);

        service = new BrandProfileService(
                brandMapper,
                companyMapper,
                brandMaterialMapper,
                brandOfferingMapper,
                brandProfileVersionMapper,
                currentUserService,
                mock(ActivityLogService.class),
                minioStorageService,
                mock(ImageCompressionService.class),
                mock(BrandImageFolderService.class),
                publicUrlService,
                new ObjectMapper(),
                mock(InternalScopeService.class)
        );

        Brand brand = new Brand();
        brand.setId(1L);
        brand.setCompanyId(2L);
        when(brandMapper.selectById(1L)).thenReturn(brand);

        Company company = new Company();
        company.setId(2L);
        when(companyMapper.selectById(2L)).thenReturn(company);

        SysUser operator = new SysUser();
        operator.setId(9L);
        when(currentUserService.requireCurrentUser()).thenReturn(operator);
        when(brandMaterialMapper.selectList(any())).thenReturn(List.of());
        when(brandOfferingMapper.selectList(any())).thenReturn(List.of());
    }

    @Test
    void buildMaterialPreviewUrlUsesInlinePublicStreamForBrandImage() {
        BrandMaterial material = material();
        when(brandMaterialMapper.selectById(3L)).thenReturn(material);
        when(publicUrlService.buildPublicStreamUrl(material))
                .thenReturn("https://example.com/api/public/brand-materials/3/stream?sig=test");

        String url = service.buildMaterialPreviewUrl(1L, 3L);

        assertEquals("https://example.com/api/public/brand-materials/3/stream?sig=test", url);
        verify(minioStorageService, never()).buildPresignedDownloadUrl(any(), anyInt());
    }

    @Test
    void deleteMaterialAllowsAnyBrandEditorWithoutDedicatedDeletePermission() {
        BrandMaterial material = material();
        when(brandMaterialMapper.selectById(3L)).thenReturn(material);

        service.deleteMaterial(1L, 3L);

        verify(currentUserService).ensurePermission("brand.update");
        verify(currentUserService, never()).ensurePermission("brand.material.delete");
        verify(brandMaterialMapper).deleteById(3L);
        verify(minioStorageService).remove("brand/1/image.jpg");
    }

    private BrandMaterial material() {
        BrandMaterial material = new BrandMaterial();
        material.setId(3L);
        material.setBrandId(1L);
        material.setCategory("brand_image");
        material.setFileName("image.jpg");
        material.setFileType("jpg");
        material.setObjectKey("brand/1/image.jpg");
        return material;
    }
}
