package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.customer.entity.BrandImageFolder;
import com.huanjing.geo.module.customer.entity.BrandImageFolderProject;
import com.huanjing.geo.module.customer.entity.BrandMaterial;
import com.huanjing.geo.module.customer.mapper.BrandImageFolderMapper;
import com.huanjing.geo.module.customer.mapper.BrandImageFolderProjectMapper;
import com.huanjing.geo.module.customer.mapper.BrandMaterialMapper;
import com.huanjing.geo.module.customer.service.BrandImageFolderService;
import com.huanjing.geo.module.project.entity.Project;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SelfMediaPublishMaterialSelectionServiceTest {
    private BrandImageFolderMapper folderMapper;
    private BrandImageFolderProjectMapper folderProjectMapper;
    private BrandMaterialMapper materialMapper;
    private SelfMediaPublishMaterialSelectionService service;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(BrandImageFolder.class);
        initTableInfo(BrandMaterial.class);
    }

    private static void initTableInfo(Class<?> entityType) {
        try {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityType);
        } catch (Exception ignored) {
            // Larger test runs may initialize table metadata first.
        }
    }

    @BeforeEach
    void setUp() {
        folderMapper = mock(BrandImageFolderMapper.class);
        folderProjectMapper = mock(BrandImageFolderProjectMapper.class);
        materialMapper = mock(BrandMaterialMapper.class);
        service = new SelfMediaPublishMaterialSelectionService(folderMapper, folderProjectMapper, materialMapper);
    }

    @Test
    void selectsArticleCoverAndMarkdownImagesByMaterialUrl() {
        BrandImageFolder folder = activeFolder(9L);
        BrandMaterial cover = material(88L, "https://cdn.local/cover.png", folder.getId());
        BrandMaterial first = material(101L, "https://cdn.local/a.png", folder.getId());
        BrandMaterial second = material(102L, "https://cdn.local/b.png", folder.getId());
        when(materialMapper.selectOne(any())).thenReturn(cover);
        when(materialMapper.selectList(any())).thenReturn(List.of(first, second));
        when(folderMapper.selectById(folder.getId())).thenReturn(folder);
        ArticleDraft article = new ArticleDraft();
        article.setCoverImageUrl("https://cdn.local/cover.png");

        SelfMediaPublishMaterialSelectionService.Selection selection = service.select(
                project(),
                article,
                "![a](https://cdn.local/a.png)\n<img src=\"https://cdn.local/b.png\">"
        );

        assertEquals(88L, selection.coverMaterialId());
        assertEquals(List.of(101L, 102L), selection.imageMaterialIds());
    }

    @Test
    void fallsBackToNewestCoverFolderImageWhenArticleHasNoImages() {
        BrandImageFolder folder = activeFolder(9L);
        BrandMaterial cover = material(88L, "https://cdn.local/cover.png", folder.getId());
        when(folderMapper.selectOne(any())).thenReturn(folder);
        when(folderMapper.selectById(folder.getId())).thenReturn(folder);
        when(materialMapper.selectList(any())).thenReturn(List.of(cover));

        SelfMediaPublishMaterialSelectionService.Selection selection = service.select(project(), new ArticleDraft(), "");

        assertEquals(88L, selection.coverMaterialId());
        assertEquals(List.of(88L), selection.imageMaterialIds());
    }

    @Test
    void automaticallyCombinesArticleCoverWithProjectIllustrationFolderImages() {
        BrandImageFolder coverFolder = activeFolder(9L);
        BrandImageFolder illustrationFolder = activeFolder(10L);
        illustrationFolder.setFolderName("插图_huawei");
        BrandMaterial cover = material(88L, "https://cdn.local/cover.png", coverFolder.getId());
        List<BrandMaterial> illustrations = List.of(
                material(101L, "https://cdn.local/1.png", illustrationFolder.getId()),
                material(102L, "https://cdn.local/2.png", illustrationFolder.getId()),
                material(103L, "https://cdn.local/3.png", illustrationFolder.getId())
        );
        ArticleDraft article = new ArticleDraft();
        article.setCoverImageUrl(cover.getFileUrl());
        when(materialMapper.selectOne(any())).thenReturn(cover);
        when(materialMapper.selectById(anyLong())).thenAnswer(invocation -> {
            long id = invocation.getArgument(0);
            if (id == cover.getId()) return cover;
            return illustrations.stream().filter(item -> item.getId() == id).findFirst().orElse(null);
        });
        when(folderMapper.selectList(any())).thenReturn(List.of(illustrationFolder));
        when(folderMapper.selectById(coverFolder.getId())).thenReturn(coverFolder);
        when(folderMapper.selectById(illustrationFolder.getId())).thenReturn(illustrationFolder);
        BrandImageFolderProject relation = new BrandImageFolderProject();
        relation.setFolderId(illustrationFolder.getId());
        relation.setProjectId(30L);
        when(folderProjectMapper.selectList(any())).thenReturn(List.of(relation));
        when(materialMapper.selectList(any())).thenReturn(illustrations);

        List<Long> selected = service.selectDouyinImageTextImages(project(), article, "");

        assertEquals(List.of(88L, 101L, 102L, 103L), selected);
    }

    private Project project() {
        Project project = new Project();
        project.setId(30L);
        project.setBrandId(50L);
        return project;
    }

    private BrandImageFolder activeFolder(Long id) {
        BrandImageFolder folder = new BrandImageFolder();
        folder.setId(id);
        folder.setBrandId(50L);
        folder.setFolderName("封面");
        folder.setStatus(BrandImageFolderService.STATUS_ACTIVE);
        return folder;
    }

    private BrandMaterial material(Long id, String fileUrl, Long folderId) {
        BrandMaterial material = new BrandMaterial();
        material.setId(id);
        material.setBrandId(50L);
        material.setFolderId(folderId);
        material.setCategory("brand_image");
        material.setFileType("png");
        material.setFileUrl(fileUrl);
        material.setObjectKey("brand/" + id + ".png");
        material.setCreatedAt(LocalDateTime.now());
        return material;
    }
}
