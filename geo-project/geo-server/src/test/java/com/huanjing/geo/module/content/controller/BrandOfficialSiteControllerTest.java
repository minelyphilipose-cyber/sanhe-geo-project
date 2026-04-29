package com.huanjing.geo.module.content.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.content.distribution.TargetContext;
import com.huanjing.geo.module.content.dto.BrandOfficialSiteCreateRequest;
import com.huanjing.geo.module.content.dto.BrandOfficialSiteUpdateRequest;
import com.huanjing.geo.module.content.entity.BrandOfficialSite;
import com.huanjing.geo.module.content.entity.DistributionTask;
import com.huanjing.geo.module.content.mapper.BrandOfficialSiteMapper;
import com.huanjing.geo.module.content.service.BrandOfficialSiteService;
import com.huanjing.geo.module.content.service.ContentDistributionService;
import com.huanjing.geo.module.content.service.adapter.AuthCheckResult;
import com.huanjing.geo.module.content.service.adapter.FailureKind;
import com.huanjing.geo.module.content.service.adapter.OfficialCmsSiteAdapter;
import com.huanjing.geo.module.content.vo.BrandOfficialSiteVO;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import com.huanjing.geo.module.system.service.MpCredentialCipherService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrandOfficialSiteControllerTest {

    @Mock
    private BrandOfficialSiteMapper brandOfficialSiteMapper;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private MpCredentialCipherService mpCredentialCipherService;
    @Mock
    private OfficialCmsSiteAdapter officialCmsSiteAdapter;
    @Mock
    private ContentDistributionService contentDistributionService;

    private BrandOfficialSiteController controller;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), BrandOfficialSite.class);
        BrandOfficialSiteService brandOfficialSiteService = new BrandOfficialSiteService(
                brandOfficialSiteMapper,
                currentUserService,
                mpCredentialCipherService
        );
        controller = new BrandOfficialSiteController(
                brandOfficialSiteService,
                brandOfficialSiteMapper,
                currentUserService,
                officialCmsSiteAdapter,
                contentDistributionService
        );
        objectMapper = new ObjectMapper();
    }

    @Test
    void create_success_encryptsAndPersists() {
        SysUser operator = operator();
        BrandOfficialSiteCreateRequest req = createRequest();
        when(currentUserService.requireCurrentUser()).thenReturn(operator);
        when(mpCredentialCipherService.encryptForStorage("token")).thenReturn("ENC:token");

        R<BrandOfficialSiteVO> response = controller.create(10L, req);

        ArgumentCaptor<BrandOfficialSite> captor = ArgumentCaptor.forClass(BrandOfficialSite.class);
        verify(brandOfficialSiteMapper).insert(captor.capture());
        BrandOfficialSite saved = captor.getValue();
        assertEquals(saved.getId(), response.getData().getId());
        assertEquals(10L, saved.getBrandId());
        assertEquals("Official Site", saved.getSiteName());
        assertEquals("ENC:token", saved.getCredentialsCipher());
        verify(currentUserService).ensureBrandAccess(operator, 10L, "brand_official_site");
    }

    @Test
    void create_blankCredentials_throws400() {
        SysUser operator = operator();
        BrandOfficialSiteCreateRequest req = createRequest();
        req.setCredentials("   ");
        when(currentUserService.requireCurrentUser()).thenReturn(operator);

        BizException ex = assertThrows(BizException.class, () -> controller.create(10L, req));

        assertEquals(400, ex.getCode());
        assertEquals("credentials is required", ex.getMessage());
        verify(brandOfficialSiteMapper, never()).insert(any());
    }

    @Test
    void create_noBrandAccess_throws403() {
        SysUser operator = operator();
        BizException denied = new BizException(403, "No permission to access this brand_official_site");
        when(currentUserService.requireCurrentUser()).thenReturn(operator);
        doThrow(denied).when(currentUserService).ensureBrandAccess(operator, 10L, "brand_official_site");

        BizException ex = assertThrows(BizException.class, () -> controller.create(10L, createRequest()));

        assertSame(denied, ex);
        verify(brandOfficialSiteMapper, never()).insert(any());
    }

    @Test
    void update_blankCredentials_keepsExistingCipher() {
        SysUser operator = operator();
        BrandOfficialSite entity = existingSite();
        BrandOfficialSiteUpdateRequest req = new BrandOfficialSiteUpdateRequest();
        req.setCredentials("   ");
        when(currentUserService.requireCurrentUser()).thenReturn(operator);
        when(brandOfficialSiteMapper.selectOne(any())).thenReturn(entity);
        when(brandOfficialSiteMapper.selectById(1L)).thenReturn(entity);

        R<BrandOfficialSiteVO> response = controller.update(1L, req);

        assertEquals(entity.getId(), response.getData().getId());
        assertEquals("ENC:old", entity.getCredentialsCipher());
        verify(mpCredentialCipherService, never()).encryptForStorage(any());
        verify(brandOfficialSiteMapper).updateById(entity);
    }

    @Test
    void update_nonBlankCredentials_encryptsAndOverwrites() {
        SysUser operator = operator();
        BrandOfficialSite entity = existingSite();
        BrandOfficialSiteUpdateRequest req = new BrandOfficialSiteUpdateRequest();
        req.setCredentials(" new-token ");
        when(currentUserService.requireCurrentUser()).thenReturn(operator);
        when(brandOfficialSiteMapper.selectOne(any())).thenReturn(entity);
        when(brandOfficialSiteMapper.selectById(1L)).thenReturn(entity);
        when(mpCredentialCipherService.encryptForStorage("new-token")).thenReturn("ENC:new");

        R<BrandOfficialSiteVO> response = controller.update(1L, req);

        assertEquals(entity.getId(), response.getData().getId());
        assertEquals("ENC:new", entity.getCredentialsCipher());
        verify(brandOfficialSiteMapper).updateById(entity);
    }

    @Test
    void getResponse_doesNotLeakCredentialsCipher() throws Exception {
        SysUser operator = operator();
        BrandOfficialSite entity = existingSite();
        entity.setCredentialsCipher("ENC(secret)");
        when(currentUserService.requireCurrentUser()).thenReturn(operator);
        when(brandOfficialSiteMapper.selectOne(any())).thenReturn(entity);

        String responseJson = objectMapper.writeValueAsString(controller.get(1L));

        assertFalse(responseJson.contains("credentialsCipher"));
        assertFalse(responseJson.contains("ENC(secret)"));
    }

    @Test
    void getResponse_doesNotIncludeQuotaFields() throws Exception {
        SysUser operator = operator();
        BrandOfficialSite entity = existingSite();
        when(currentUserService.requireCurrentUser()).thenReturn(operator);
        when(brandOfficialSiteMapper.selectOne(any())).thenReturn(entity);

        String responseJson = objectMapper.writeValueAsString(controller.get(1L));

        assertFalse(responseJson.contains("monthlyQuotaUsed"));
        assertFalse(responseJson.contains("monthlyLimit"));
    }

    @Test
    void getResponse_includesAllExpectedPublicFields() throws Exception {
        SysUser operator = operator();
        BrandOfficialSite entity = existingSite();
        when(currentUserService.requireCurrentUser()).thenReturn(operator);
        when(brandOfficialSiteMapper.selectOne(any())).thenReturn(entity);

        String responseJson = objectMapper.writeValueAsString(controller.get(1L));

        assertEquals(true, responseJson.contains("siteName"));
        assertEquals(true, responseJson.contains("apiEndpoint"));
        assertEquals(true, responseJson.contains("status"));
        assertEquals(true, responseJson.contains("cmsFrameworkCode"));
        assertEquals(true, responseJson.contains("tenantKey"));
        assertEquals(true, responseJson.contains("authType"));
    }

    @Test
    void checkAuth_returnsAdapterResult() {
        SysUser operator = operator();
        BrandOfficialSite entity = existingSite();
        AuthCheckResult authResult = AuthCheckResult.failure(FailureKind.AUTH_EXPIRED, "auth_failed");
        when(currentUserService.requireCurrentUser()).thenReturn(operator);
        when(brandOfficialSiteMapper.selectOne(any())).thenReturn(entity);
        when(officialCmsSiteAdapter.checkAuth(any())).thenReturn(authResult);

        R<AuthCheckResult> response = controller.checkAuth(1L);

        assertSame(authResult, response.getData());
        assertEquals(FailureKind.AUTH_EXPIRED, response.getData().getFailureKind());
    }

    @Test
    void distribute_delegatesToContentDistributionService() {
        SysUser operator = operator();
        BrandOfficialSite entity = existingSite();
        DistributionTask task = new DistributionTask();
        task.setId(300L);
        task.setStatus("submitted");
        when(currentUserService.requireCurrentUser()).thenReturn(operator);
        when(brandOfficialSiteMapper.selectOne(any())).thenReturn(entity);
        when(contentDistributionService.distributeTo(eq(20L), any())).thenReturn(task);

        R<DistributionTask> response = controller.distribute(1L, 20L);

        assertSame(task, response.getData());
        ArgumentCaptor<TargetContext> captor = ArgumentCaptor.forClass(TargetContext.class);
        verify(contentDistributionService).distributeTo(eq(20L), captor.capture());
        TargetContext target = captor.getValue();
        TargetContext.BrandOfficialSiteTarget brandTarget = (TargetContext.BrandOfficialSiteTarget) target;
        assertSame(entity, brandTarget.site());
    }

    @Test
    void get_missingSite_throws404() {
        when(brandOfficialSiteMapper.selectOne(any())).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> controller.get(1L));

        assertEquals(404, ex.getCode());
        assertEquals("Brand official site not found", ex.getMessage());
    }

    private BrandOfficialSiteCreateRequest createRequest() {
        BrandOfficialSiteCreateRequest req = new BrandOfficialSiteCreateRequest();
        req.setSiteName(" Official Site ");
        req.setSiteDomain(" https://official.example ");
        req.setCmsFrameworkCode(" " + OfficialCmsSiteAdapter.FRAMEWORK_CODE_DEFAULT + " ");
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
        entity.setCmsFrameworkCode(OfficialCmsSiteAdapter.FRAMEWORK_CODE_DEFAULT);
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
