package com.huanjing.geo.module.customer.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.customer.access.InternalScopeService;
import com.huanjing.geo.module.customer.dto.BrandCreateRequest;
import com.huanjing.geo.module.customer.dto.BrandUpdateRequest;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.customer.mapper.BrandMaterialMapper;
import com.huanjing.geo.module.customer.mapper.BrandProfileVersionMapper;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.mapper.SysDictItemMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.ActivityLogService;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BrandServiceTest {

    private BrandMapper brandMapper;
    private CompanyMapper companyMapper;
    private CurrentUserService currentUserService;
    private InternalScopeService internalScopeService;
    private BrandService brandService;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Brand.class);
        brandMapper = mock(BrandMapper.class);
        companyMapper = mock(CompanyMapper.class);
        currentUserService = mock(CurrentUserService.class);
        internalScopeService = mock(InternalScopeService.class);
        brandService = new BrandService(
                brandMapper,
                mock(BrandMaterialMapper.class),
                mock(BrandProfileVersionMapper.class),
                companyMapper,
                mock(ProjectMapper.class),
                mock(SysDictItemMapper.class),
                currentUserService,
                internalScopeService,
                mock(ActivityLogService.class),
                mock(BrandProfileService.class)
        );
        SysUser operator = new SysUser();
        operator.setId(100L);
        when(currentUserService.requireCurrentUser()).thenReturn(operator);
        when(companyMapper.selectById(10L)).thenReturn(company());
    }

    @Test
    void create_withGeoSiteDomain_successDefaultsActive() {
        when(brandMapper.selectOne(any())).thenReturn(null);
        when(brandMapper.insert(any())).thenAnswer(invocation -> {
            Brand brand = invocation.getArgument(0);
            brand.setId(1L);
            return 1;
        });

        Brand result = brandService.create(createReq("官网", "https://www.example.com/path", null));

        assertNull(result.getGeoSiteCode());
        assertEquals("官网", result.getGeoSiteName());
        assertEquals("www.example.com", result.getGeoSiteDomain());
        assertEquals("active", result.getGeoSiteStatus());
    }

    @Test
    void create_geoSiteNameDefaultsWhenMissing_success() {
        when(brandMapper.selectOne(any())).thenReturn(null);
        when(brandMapper.insert(any())).thenAnswer(invocation -> {
            Brand brand = invocation.getArgument(0);
            brand.setId(1L);
            return 1;
        });

        Brand result = brandService.create(createReq(null, "www.example.com", null));

        assertEquals("Agent 官网", result.getGeoSiteName());
        assertEquals("www.example.com", result.getGeoSiteDomain());
        assertEquals("active", result.getGeoSiteStatus());
    }

    @Test
    void create_duplicateGeoSiteDomain_fails() {
        Brand duplicate = new Brand();
        duplicate.setId(2L);
        when(brandMapper.selectOne(any())).thenReturn(null, duplicate);

        BizException ex = assertThrows(BizException.class, () -> brandService.create(createReq("官网", "www.example.com", null)));

        assertEquals(400, ex.getCode());
        assertEquals("geo_site_domain already exists", ex.getMessage());
    }

    @Test
    void create_invalidGeoSiteDomainFormat_fails() {
        when(brandMapper.selectOne(any())).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> brandService.create(createReq("官网", "://bad", null)));

        assertEquals(400, ex.getCode());
        assertEquals("Invalid geo_site_domain", ex.getMessage());
    }

    @Test
    void create_geoSiteStatusWithoutDomain_fails() {
        when(brandMapper.selectOne(any())).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> brandService.create(createReq(null, null, "active")));

        assertEquals(400, ex.getCode());
        assertEquals("geo_site_status requires geo_site_domain", ex.getMessage());
    }

    @Test
    void create_withIndustrySiteConfig_successTrimsValues() {
        when(brandMapper.selectOne(any())).thenReturn(null);
        when(brandMapper.insert(any())).thenAnswer(invocation -> {
            Brand brand = invocation.getArgument(0);
            brand.setId(1L);
            return 1;
        });
        BrandCreateRequest req = createReq(null, null, null);
        req.setIndustrySiteName(" 火锅资讯站 ");
        req.setIndustrySiteCode(" hotpot_news ");

        Brand result = brandService.create(req);

        assertEquals("火锅资讯站", result.getIndustrySiteName());
        assertEquals("hotpot_news", result.getIndustrySiteCode());
    }

    @Test
    void create_industrySiteNameWithoutCode_fails() {
        when(brandMapper.selectOne(any())).thenReturn(null);
        BrandCreateRequest req = createReq(null, null, null);
        req.setIndustrySiteName("火锅资讯站");

        BizException ex = assertThrows(BizException.class, () -> brandService.create(req));

        assertEquals(400, ex.getCode());
        assertEquals("industry_site_code is required when industry site is configured", ex.getMessage());
    }

    @Test
    void create_invalidIndustrySiteCode_fails() {
        when(brandMapper.selectOne(any())).thenReturn(null);
        BrandCreateRequest req = createReq(null, null, null);
        req.setIndustrySiteName("火锅资讯站");
        req.setIndustrySiteCode("-bad");

        BizException ex = assertThrows(BizException.class, () -> brandService.create(req));

        assertEquals(400, ex.getCode());
        assertEquals("Invalid industry_site_code", ex.getMessage());
    }

    @Test
    void update_changeGeoSiteDomain_uniqueCheck() {
        Brand existing = existingBrand();
        when(brandMapper.selectById(1L)).thenReturn(existing);
        when(brandMapper.selectOne(any())).thenReturn(null);

        brandService.update(1L, updateReq("新官网", "https://next.example.com/a", "disabled"));

        ArgumentCaptor<Brand> captor = ArgumentCaptor.forClass(Brand.class);
        verify(brandMapper).updateById(captor.capture());
        assertEquals("新官网", captor.getValue().getGeoSiteName());
        assertEquals("next.example.com", captor.getValue().getGeoSiteDomain());
        assertEquals("disabled", captor.getValue().getGeoSiteStatus());
    }

    @Test
    void update_blankGeoSiteDomain_clearsConfig() {
        Brand existing = existingBrand();
        existing.setGeoSiteName("旧官网");
        existing.setGeoSiteDomain("old.example.com");
        existing.setGeoSiteStatus("active");
        when(brandMapper.selectById(1L)).thenReturn(existing);
        when(brandMapper.selectOne(any())).thenReturn(null);

        brandService.update(1L, updateReq("", "", null));

        ArgumentCaptor<Brand> captor = ArgumentCaptor.forClass(Brand.class);
        verify(brandMapper).updateById(captor.capture());
        assertNull(captor.getValue().getGeoSiteCode());
        assertNull(captor.getValue().getGeoSiteName());
        assertNull(captor.getValue().getGeoSiteDomain());
        assertNull(captor.getValue().getGeoSiteStatus());
    }

    @Test
    void update_blankIndustrySiteFields_clearsConfig() {
        Brand existing = existingBrand();
        existing.setIndustrySiteName("旧资讯站");
        existing.setIndustrySiteCode("old_site");
        when(brandMapper.selectById(1L)).thenReturn(existing);
        when(brandMapper.selectOne(any())).thenReturn(null);
        BrandUpdateRequest req = updateReq(null, null, null);
        req.setIndustrySiteName("");
        req.setIndustrySiteCode("");

        brandService.update(1L, req);

        ArgumentCaptor<Brand> captor = ArgumentCaptor.forClass(Brand.class);
        verify(brandMapper).updateById(captor.capture());
        assertNull(captor.getValue().getIndustrySiteName());
        assertNull(captor.getValue().getIndustrySiteCode());
    }

    private BrandCreateRequest createReq(String geoSiteName, String geoSiteDomain, String geoSiteStatus) {
        BrandCreateRequest req = new BrandCreateRequest();
        req.setCompanyId(10L);
        req.setIndustry("retail");
        req.setBrandName("Brand");
        req.setBrandSlug("brand");
        req.setGeoSiteName(geoSiteName);
        req.setGeoSiteDomain(geoSiteDomain);
        req.setGeoSiteStatus(geoSiteStatus);
        req.setStatus("active");
        return req;
    }

    private BrandUpdateRequest updateReq(String geoSiteName, String geoSiteDomain, String geoSiteStatus) {
        BrandUpdateRequest req = new BrandUpdateRequest();
        req.setIndustry("retail");
        req.setBrandName("Brand");
        req.setBrandSlug("brand");
        req.setGeoSiteName(geoSiteName);
        req.setGeoSiteDomain(geoSiteDomain);
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

}
