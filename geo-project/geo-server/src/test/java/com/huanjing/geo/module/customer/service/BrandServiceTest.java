package com.huanjing.geo.module.customer.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.customer.dto.BrandCreateRequest;
import com.huanjing.geo.module.customer.dto.BrandUpdateRequest;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.customer.mapper.BrandMaterialMapper;
import com.huanjing.geo.module.customer.mapper.BrandProfileVersionMapper;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.SysDictItem;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.SysDictItemMapper;
import com.huanjing.geo.module.system.service.ActivityLogService;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BrandServiceTest {

    private BrandMapper brandMapper;
    private CompanyMapper companyMapper;
    private CurrentUserService currentUserService;
    private SysDictItemMapper sysDictItemMapper;
    private BrandService brandService;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Brand.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysDictItem.class);
        brandMapper = mock(BrandMapper.class);
        companyMapper = mock(CompanyMapper.class);
        currentUserService = mock(CurrentUserService.class);
        sysDictItemMapper = mock(SysDictItemMapper.class);
        brandService = new BrandService(
                brandMapper,
                mock(BrandMaterialMapper.class),
                mock(BrandProfileVersionMapper.class),
                companyMapper,
                mock(ProjectMapper.class),
                currentUserService,
                mock(ActivityLogService.class),
                mock(BrandProfileService.class),
                sysDictItemMapper
        );
        SysUser operator = new SysUser();
        operator.setId(100L);
        when(currentUserService.requireCurrentUser()).thenReturn(operator);
        when(companyMapper.selectById(10L)).thenReturn(company());
        when(sysDictItemMapper.selectList(any())).thenReturn(List.of(dict("retail")));
    }

    @Test
    void create_withGeoSiteCode_successDefaultsActive() {
        when(brandMapper.selectOne(any())).thenReturn(null);
        when(brandMapper.insert(any())).thenAnswer(invocation -> {
            Brand brand = invocation.getArgument(0);
            brand.setId(1L);
            return 1;
        });

        Brand result = brandService.create(createReq("ok", null));

        assertEquals("ok", result.getGeoSiteCode());
        assertEquals("active", result.getGeoSiteStatus());
    }

    @Test
    void create_duplicateGeoSiteCode_fails() {
        Brand duplicate = new Brand();
        duplicate.setId(2L);
        when(brandMapper.selectOne(any())).thenReturn(null, duplicate);

        BizException ex = assertThrows(BizException.class, () -> brandService.create(createReq("ok", null)));

        assertEquals(400, ex.getCode());
        assertEquals("geo_site_code already exists", ex.getMessage());
    }

    @Test
    void create_invalidGeoSiteCodeFormat_fails() {
        when(brandMapper.selectOne(any())).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> brandService.create(createReq("-bad", null)));

        assertEquals(400, ex.getCode());
        assertEquals("Invalid geo_site_code", ex.getMessage());
    }

    @Test
    void create_geoSiteStatusWithoutCode_fails() {
        when(brandMapper.selectOne(any())).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> brandService.create(createReq(null, "active")));

        assertEquals(400, ex.getCode());
        assertEquals("geo_site_status requires geo_site_code", ex.getMessage());
    }

    @Test
    void update_changeGeoSiteCode_uniqueCheck() {
        Brand existing = existingBrand();
        when(brandMapper.selectById(1L)).thenReturn(existing);
        when(brandMapper.selectOne(any())).thenReturn(null);

        brandService.update(1L, updateReq("next", "disabled"));

        ArgumentCaptor<Brand> captor = ArgumentCaptor.forClass(Brand.class);
        verify(brandMapper).updateById(captor.capture());
        assertEquals("next", captor.getValue().getGeoSiteCode());
        assertEquals("disabled", captor.getValue().getGeoSiteStatus());
    }

    @Test
    void update_blankGeoSiteCode_clearsConfig() {
        Brand existing = existingBrand();
        existing.setGeoSiteCode("old");
        existing.setGeoSiteStatus("active");
        when(brandMapper.selectById(1L)).thenReturn(existing);
        when(brandMapper.selectOne(any())).thenReturn(null);

        brandService.update(1L, updateReq("", null));

        ArgumentCaptor<Brand> captor = ArgumentCaptor.forClass(Brand.class);
        verify(brandMapper).updateById(captor.capture());
        assertNull(captor.getValue().getGeoSiteCode());
        assertNull(captor.getValue().getGeoSiteStatus());
    }

    private BrandCreateRequest createReq(String geoSiteCode, String geoSiteStatus) {
        BrandCreateRequest req = new BrandCreateRequest();
        req.setCompanyId(10L);
        req.setIndustry("retail");
        req.setBrandName("Brand");
        req.setBrandSlug("brand");
        req.setGeoSiteCode(geoSiteCode);
        req.setGeoSiteStatus(geoSiteStatus);
        req.setStatus("active");
        return req;
    }

    private BrandUpdateRequest updateReq(String geoSiteCode, String geoSiteStatus) {
        BrandUpdateRequest req = new BrandUpdateRequest();
        req.setIndustry("retail");
        req.setBrandName("Brand");
        req.setBrandSlug("brand");
        req.setGeoSiteCode(geoSiteCode);
        req.setGeoSiteStatus(geoSiteStatus);
        req.setStatus("active");
        return req;
    }

    private Brand existingBrand() {
        Brand brand = new Brand();
        brand.setId(1L);
        brand.setCompanyId(10L);
        brand.setIndustry("retail");
        brand.setBrandName("Brand");
        brand.setBrandSlug("brand");
        brand.setStatus("active");
        return brand;
    }

    private Company company() {
        Company company = new Company();
        company.setId(10L);
        company.setPartnerId(20L);
        company.setIndustryTags("[\"retail\"]");
        return company;
    }

    private SysDictItem dict(String key) {
        SysDictItem item = new SysDictItem();
        item.setDictKey(key);
        return item;
    }
}
