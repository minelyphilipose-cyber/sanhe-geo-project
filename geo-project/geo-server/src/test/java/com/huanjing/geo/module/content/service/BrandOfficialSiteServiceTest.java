package com.huanjing.geo.module.content.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.dto.BrandOfficialSiteCreateRequest;
import com.huanjing.geo.module.content.dto.BrandOfficialSiteUpdateRequest;
import com.huanjing.geo.module.content.entity.BrandOfficialSite;
import com.huanjing.geo.module.content.mapper.BrandOfficialSiteMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import com.huanjing.geo.module.system.service.MpCredentialCipherService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrandOfficialSiteServiceTest {

    @Mock
    private BrandOfficialSiteMapper brandOfficialSiteMapper;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private MpCredentialCipherService mpCredentialCipherService;

    @InjectMocks
    private BrandOfficialSiteService brandOfficialSiteService;

    @Test
    void createSite_credentialsNonBlank_encryptsAndPersists() {
        SysUser operator = operator();
        BrandOfficialSiteCreateRequest req = createRequest();
        when(currentUserService.requireCurrentUser()).thenReturn(operator);
        when(mpCredentialCipherService.encryptForStorage("token")).thenReturn("ENC:token");

        BrandOfficialSite result = brandOfficialSiteService.createSite(10L, req);

        ArgumentCaptor<BrandOfficialSite> captor = ArgumentCaptor.forClass(BrandOfficialSite.class);
        verify(brandOfficialSiteMapper).insert(captor.capture());
        BrandOfficialSite saved = captor.getValue();
        assertSame(saved, result);
        assertEquals(10L, saved.getBrandId());
        assertEquals("Official Site", saved.getSiteName());
        assertEquals("official_cms_v1", saved.getCmsFrameworkCode());
        assertEquals("tenant-a", saved.getTenantKey());
        assertEquals("https://cms.example/api", saved.getApiEndpoint());
        assertEquals("bearer_token", saved.getAuthType());
        assertEquals("ENC:token", saved.getCredentialsCipher());
        assertEquals("active", saved.getStatus());
        assertEquals(100L, saved.getCreatedBy());
    }

    @Test
    void createSite_blankCredentials_throws400() {
        SysUser operator = operator();
        BrandOfficialSiteCreateRequest req = createRequest();
        req.setCredentials("   ");
        when(currentUserService.requireCurrentUser()).thenReturn(operator);

        BizException ex = assertThrows(BizException.class, () -> brandOfficialSiteService.createSite(10L, req));

        assertEquals(400, ex.getCode());
        assertEquals("credentials is required", ex.getMessage());
        verify(brandOfficialSiteMapper, never()).insert(any());
    }

    @Test
    void updateSite_credentialsNull_keepsExistingCipher() {
        BrandOfficialSite entity = existingSite();
        BrandOfficialSiteUpdateRequest req = new BrandOfficialSiteUpdateRequest();
        when(brandOfficialSiteMapper.selectById(1L)).thenReturn(entity);
        when(currentUserService.requireCurrentUser()).thenReturn(operator());

        brandOfficialSiteService.updateSite(1L, req);

        assertEquals("ENC:old", entity.getCredentialsCipher());
        verify(mpCredentialCipherService, never()).encryptForStorage(any());
        verify(brandOfficialSiteMapper).updateById(entity);
    }

    @Test
    void updateSite_blankCredentials_keepsExistingCipher() {
        BrandOfficialSite entity = existingSite();
        BrandOfficialSiteUpdateRequest req = new BrandOfficialSiteUpdateRequest();
        req.setCredentials("   ");
        when(brandOfficialSiteMapper.selectById(1L)).thenReturn(entity);
        when(currentUserService.requireCurrentUser()).thenReturn(operator());

        brandOfficialSiteService.updateSite(1L, req);

        assertEquals("ENC:old", entity.getCredentialsCipher());
        verify(mpCredentialCipherService, never()).encryptForStorage(any());
        verify(brandOfficialSiteMapper).updateById(entity);
    }

    @Test
    void updateSite_nonBlankCredentials_encryptsAndOverwrites() {
        BrandOfficialSite entity = existingSite();
        BrandOfficialSiteUpdateRequest req = new BrandOfficialSiteUpdateRequest();
        req.setCredentials(" new-token ");
        when(brandOfficialSiteMapper.selectById(1L)).thenReturn(entity);
        when(currentUserService.requireCurrentUser()).thenReturn(operator());
        when(mpCredentialCipherService.encryptForStorage("new-token")).thenReturn("ENC:new");

        brandOfficialSiteService.updateSite(1L, req);

        assertEquals("ENC:new", entity.getCredentialsCipher());
        verify(brandOfficialSiteMapper).updateById(entity);
    }

    @Test
    void updateSite_otherFields_updateNormally() {
        BrandOfficialSite entity = existingSite();
        BrandOfficialSiteUpdateRequest req = new BrandOfficialSiteUpdateRequest();
        req.setSiteName(" New Name ");
        req.setSiteDomain(" https://brand.example ");
        req.setCmsFrameworkCode(" official_cms_v2 ");
        req.setTenantKey(" tenant-b ");
        req.setApiEndpoint(" https://cms.example/v2 ");
        req.setAuthType(" api_key ");
        req.setRemark(" remark ");
        when(brandOfficialSiteMapper.selectById(1L)).thenReturn(entity);
        when(currentUserService.requireCurrentUser()).thenReturn(operator());

        brandOfficialSiteService.updateSite(1L, req);

        assertEquals("New Name", entity.getSiteName());
        assertEquals("https://brand.example", entity.getSiteDomain());
        assertEquals("official_cms_v2", entity.getCmsFrameworkCode());
        assertEquals("tenant-b", entity.getTenantKey());
        assertEquals("https://cms.example/v2", entity.getApiEndpoint());
        assertEquals("api_key", entity.getAuthType());
        assertEquals("remark", entity.getRemark());
        verify(brandOfficialSiteMapper).updateById(entity);
    }

    @Test
    void deleteSite_deletesRow() {
        BrandOfficialSite entity = existingSite();
        when(brandOfficialSiteMapper.selectById(1L)).thenReturn(entity);
        when(currentUserService.requireCurrentUser()).thenReturn(operator());

        brandOfficialSiteService.deleteSite(1L);

        verify(brandOfficialSiteMapper).deleteById(1L);
    }

    @Test
    void disableSite_setsDisabled() {
        BrandOfficialSite entity = existingSite();
        when(brandOfficialSiteMapper.selectById(1L)).thenReturn(entity);
        when(currentUserService.requireCurrentUser()).thenReturn(operator());

        BrandOfficialSite result = brandOfficialSiteService.disableSite(1L);

        assertSame(entity, result);
        assertEquals("disabled", entity.getStatus());
        assertEquals("ENC:old", entity.getCredentialsCipher());
        verify(brandOfficialSiteMapper).updateById(entity);
    }

    @Test
    void enableSite_setsActive() {
        BrandOfficialSite entity = existingSite();
        entity.setStatus("disabled");
        when(brandOfficialSiteMapper.selectById(1L)).thenReturn(entity);
        when(currentUserService.requireCurrentUser()).thenReturn(operator());

        BrandOfficialSite result = brandOfficialSiteService.enableSite(1L);

        assertSame(entity, result);
        assertEquals("active", entity.getStatus());
        verify(brandOfficialSiteMapper).updateById(entity);
    }

    @Test
    void getSite_returnsEntity() {
        BrandOfficialSite entity = existingSite();
        when(brandOfficialSiteMapper.selectById(1L)).thenReturn(entity);
        when(currentUserService.requireCurrentUser()).thenReturn(operator());

        BrandOfficialSite result = brandOfficialSiteService.getSite(1L);

        assertSame(entity, result);
    }

    @Test
    void listByBrand_returnsSites() {
        BrandOfficialSite entity = existingSite();
        List<BrandOfficialSite> rows = List.of(entity);
        when(currentUserService.requireCurrentUser()).thenReturn(operator());
        when(brandOfficialSiteMapper.selectList(any())).thenReturn(rows);

        List<BrandOfficialSite> result = brandOfficialSiteService.listByBrand(10L);

        assertSame(rows, result);
        verify(currentUserService).ensureBrandAccess(any(SysUser.class), eq(10L), eq("brand"));
    }

    @Test
    void disableSite_noPermission_throws403() {
        BrandOfficialSite entity = existingSite();
        SysUser operator = operator();
        BizException denied = new BizException(403, "No permission to access this brand");
        when(brandOfficialSiteMapper.selectById(1L)).thenReturn(entity);
        when(currentUserService.requireCurrentUser()).thenReturn(operator);
        doThrow(denied).when(currentUserService).ensureBrandAccess(operator, 10L, "brand");

        BizException ex = assertThrows(BizException.class, () -> brandOfficialSiteService.disableSite(1L));

        assertSame(denied, ex);
        verify(brandOfficialSiteMapper, never()).updateById(any());
    }

    private BrandOfficialSiteCreateRequest createRequest() {
        BrandOfficialSiteCreateRequest req = new BrandOfficialSiteCreateRequest();
        req.setSiteName(" Official Site ");
        req.setSiteDomain(" https://official.example ");
        req.setCmsFrameworkCode(" official_cms_v1 ");
        req.setTenantKey(" tenant-a ");
        req.setApiEndpoint(" https://cms.example/api ");
        req.setCredentials(" token ");
        return req;
    }

    private BrandOfficialSite existingSite() {
        BrandOfficialSite entity = new BrandOfficialSite();
        entity.setId(1L);
        entity.setBrandId(10L);
        entity.setSiteName("Old Name");
        entity.setSiteDomain("https://old.example");
        entity.setCmsFrameworkCode("official_cms_v1");
        entity.setTenantKey("tenant-a");
        entity.setApiEndpoint("https://cms.example/api");
        entity.setAuthType("bearer_token");
        entity.setCredentialsCipher("ENC:old");
        entity.setStatus("active");
        return entity;
    }

    private SysUser operator() {
        SysUser operator = new SysUser();
        operator.setId(100L);
        operator.setRole("super_admin");
        operator.setIsActive(true);
        return operator;
    }
}
