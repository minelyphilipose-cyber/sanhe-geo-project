package com.huanjing.geo.module.customer.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.customer.access.InternalScopeService;
import com.huanjing.geo.module.customer.dto.BrandImageFolderRequest;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.entity.BrandImageFolder;
import com.huanjing.geo.module.customer.entity.BrandImageFolderProject;
import com.huanjing.geo.module.customer.entity.BrandImageFolderTag;
import com.huanjing.geo.module.customer.entity.BrandMaterial;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.mapper.BrandImageFolderMapper;
import com.huanjing.geo.module.customer.mapper.BrandImageFolderProjectMapper;
import com.huanjing.geo.module.customer.mapper.BrandImageFolderTagMapper;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.customer.mapper.BrandMaterialMapper;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BrandImageFolderServiceTest {

    private BrandMapper brandMapper;
    private CompanyMapper companyMapper;
    private BrandMaterialMapper brandMaterialMapper;
    private BrandImageFolderMapper folderMapper;
    private BrandImageFolderProjectMapper folderProjectMapper;
    private BrandImageFolderTagMapper folderTagMapper;
    private CurrentUserService currentUserService;
    private InternalScopeService internalScopeService;
    private BrandImageFolderService service;

    @BeforeEach
    void setUp() {
        brandMapper = mock(BrandMapper.class);
        companyMapper = mock(CompanyMapper.class);
        ProjectMapper projectMapper = mock(ProjectMapper.class);
        brandMaterialMapper = mock(BrandMaterialMapper.class);
        folderMapper = mock(BrandImageFolderMapper.class);
        folderProjectMapper = mock(BrandImageFolderProjectMapper.class);
        folderTagMapper = mock(BrandImageFolderTagMapper.class);
        currentUserService = mock(CurrentUserService.class);
        internalScopeService = mock(InternalScopeService.class);
        BrandMaterialPublicUrlService publicUrlService = mock(BrandMaterialPublicUrlService.class);
        service = new BrandImageFolderService(
                brandMapper,
                companyMapper,
                projectMapper,
                brandMaterialMapper,
                folderMapper,
                folderProjectMapper,
                folderTagMapper,
                currentUserService,
                publicUrlService,
                internalScopeService
        );

        SysUser user = new SysUser();
        user.setId(7L);
        when(currentUserService.requireCurrentUser()).thenReturn(user);
        when(brandMapper.selectById(10L)).thenReturn(brand());
        when(companyMapper.selectById(20L)).thenReturn(company());
    }

    @Test
    void deleteFolder_requiresDisabledFolder() {
        when(folderMapper.selectById(100L)).thenReturn(folder(BrandImageFolderService.STATUS_ACTIVE, false));

        assertThatThrownBy(() -> service.deleteFolder(10L, 100L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("请先停用");

        verify(brandMaterialMapper, never()).update(any(), any(Wrapper.class));
        verify(folderMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void deleteFolder_unbindsMaterialsAndRelationsBeforeDeletingFolder() {
        when(folderMapper.selectById(100L)).thenReturn(folder(100L, "临时素材", BrandImageFolderService.STATUS_DISABLED, false));
        when(folderMapper.selectList(any())).thenReturn(List.of(
                folder(100L, "临时素材", BrandImageFolderService.STATUS_DISABLED, false),
                folder(101L, "插图", BrandImageFolderService.STATUS_ACTIVE, false),
                folder(102L, "封面", BrandImageFolderService.STATUS_ACTIVE, false)
        ));

        service.deleteFolder(10L, 100L);

        verify(brandMaterialMapper).update(eq(null), any(Wrapper.class));
        verify(folderProjectMapper).delete(any(Wrapper.class));
        verify(folderTagMapper).delete(any(Wrapper.class));
        verify(folderMapper).deleteById(100L);
    }

    @Test
    void deleteFolder_rejectsDefaultFolder() {
        when(folderMapper.selectById(100L)).thenReturn(folder(BrandImageFolderService.STATUS_DISABLED, true));

        assertThatThrownBy(() -> service.deleteFolder(10L, 100L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("默认图库");

        verify(folderMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void updateFolder_rejectsDisablingLastIllustrationFolder() {
        when(folderMapper.selectById(100L)).thenReturn(folder(100L, "插图", BrandImageFolderService.STATUS_ACTIVE, false));
        when(folderMapper.selectList(any())).thenReturn(List.of(
                folder(100L, "插图", BrandImageFolderService.STATUS_ACTIVE, false),
                folder(102L, "封面", BrandImageFolderService.STATUS_ACTIVE, false)
        ));
        BrandImageFolderRequest req = folderRequest("插图", BrandImageFolderService.STATUS_DISABLED);

        assertThatThrownBy(() -> service.updateFolder(10L, 100L, req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("图片资产中需至少一个名称以“插图”开头的文件夹");

        verify(folderMapper, never()).updateById(any(BrandImageFolder.class));
    }

    @Test
    void updateFolder_rejectsRenamingCoverFolder() {
        when(folderMapper.selectById(102L)).thenReturn(folder(102L, "封面", BrandImageFolderService.STATUS_ACTIVE, false));
        when(folderMapper.selectList(any())).thenReturn(List.of(
                folder(101L, "插图_门店", BrandImageFolderService.STATUS_ACTIVE, false),
                folder(102L, "封面", BrandImageFolderService.STATUS_ACTIVE, false)
        ));
        BrandImageFolderRequest req = folderRequest("首图", BrandImageFolderService.STATUS_ACTIVE);

        assertThatThrownBy(() -> service.updateFolder(10L, 102L, req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("图片资产中需至少一个名称以“插图”开头的文件夹");

        verify(folderMapper, never()).updateById(any(BrandImageFolder.class));
    }

    private Brand brand() {
        Brand brand = new Brand();
        brand.setId(10L);
        brand.setCompanyId(20L);
        return brand;
    }

    private Company company() {
        Company company = new Company();
        company.setId(20L);
        return company;
    }

    private BrandImageFolder folder(String status, boolean defaultFlag) {
        return folder(100L, null, status, defaultFlag);
    }

    private BrandImageFolder folder(Long id, String folderName, String status, boolean defaultFlag) {
        BrandImageFolder folder = new BrandImageFolder();
        folder.setId(id);
        folder.setBrandId(10L);
        folder.setFolderName(folderName);
        folder.setStatus(status);
        folder.setDefaultFlag(defaultFlag);
        return folder;
    }

    private BrandImageFolderRequest folderRequest(String folderName, String status) {
        BrandImageFolderRequest req = new BrandImageFolderRequest();
        req.setFolderName(folderName);
        req.setStatus(status);
        return req;
    }
}
