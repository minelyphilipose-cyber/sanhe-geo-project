package com.huanjing.geo.module.content.service;

import com.huanjing.geo.module.customer.access.BrandAccessService;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ArticleGenerationReadinessServiceTest {

    private ProjectMapper projectMapper;
    private BrandMapper brandMapper;
    private CurrentUserService currentUserService;
    private BatchArticlePromptBuilder promptBuilder;
    private ArticleGenerationReadinessService service;

    @BeforeEach
    void setUp() {
        projectMapper = mock(ProjectMapper.class);
        brandMapper = mock(BrandMapper.class);
        currentUserService = mock(CurrentUserService.class);
        promptBuilder = mock(BatchArticlePromptBuilder.class);
        service = new ArticleGenerationReadinessService(
                projectMapper,
                brandMapper,
                currentUserService,
                mock(BrandAccessService.class),
                promptBuilder
        );
    }

    @Test
    void hasContactBaseUsesFullContactBlockBuilder() {
        Brand brand = new Brand();
        when(promptBuilder.buildContactBlock(brand, "full")).thenReturn("电话：400-000");

        assertThat(service.hasContactBase(brand)).isTrue();
    }

    @Test
    void detectsDealMissingAndHiddenWarnings() {
        Brand brand = new Brand();
        when(promptBuilder.buildContactBlock(brand, "full")).thenReturn("");

        assertThat(service.detectTaskReadinessWarningCodes("deal", "full", brand))
                .containsExactly(ArticleGenerationReadinessService.WARNING_DEAL_CONTACT_MISSING);
        assertThat(service.detectTaskReadinessWarningCodes("qa", "none", brand)).isEmpty();

        when(promptBuilder.buildContactBlock(brand, "full")).thenReturn("电话：400-000");
        assertThat(service.detectTaskReadinessWarningCodes("deal", "none", brand))
                .containsExactly(ArticleGenerationReadinessService.WARNING_DEAL_CONTACT_HIDDEN);
    }

    @Test
    void detectedWarningCodesAreKnownCodes() {
        Brand brand = new Brand();
        when(promptBuilder.buildContactBlock(brand, "full")).thenReturn("");
        assertThat(service.detectTaskReadinessWarningCodes("deal", "full", brand))
                .allMatch(service::isKnownWarningCode);

        when(promptBuilder.buildContactBlock(brand, "full")).thenReturn("电话：400-000");
        assertThat(service.detectTaskReadinessWarningCodes("deal", "none", brand))
                .allMatch(service::isKnownWarningCode);
    }

    @Test
    void inspectReturnsMissingBrandItemsAndDealCriticalWhenProjectHasNoBrand() {
        SysUser user = new SysUser();
        user.setId(9L);
        Project project = new Project();
        project.setId(1L);
        project.setStatus("active");
        project.setPartnerId(3L);
        project.setTargetAudience("本地商家");
        when(currentUserService.requireCurrentUser()).thenReturn(user);
        when(projectMapper.selectById(1L)).thenReturn(project);
        when(promptBuilder.buildContactBlock(null, "full")).thenReturn("");

        var report = service.inspect(1L, List.of("deal", "qa"));

        assertThat(report.score()).isLessThan(100);
        assertThat(report.baseItems())
                .anySatisfy(item -> {
                    assertThat(item.code()).isEqualTo("brandIntro");
                    assertThat(item.status()).isEqualTo("missing");
                })
                .anySatisfy(item -> {
                    assertThat(item.code()).isEqualTo("targetAudience");
                    assertThat(item.status()).isEqualTo("ok");
                });
        assertThat(report.sceneImpacts())
                .anySatisfy(scene -> {
                    assertThat(scene.questionSceneCode()).isEqualTo("deal");
                    assertThat(scene.status()).isEqualTo("critical");
                    assertThat(scene.items()).anySatisfy(item -> {
                        assertThat(item.warningCode())
                                .isEqualTo(ArticleGenerationReadinessService.WARNING_DEAL_CONTACT_MISSING);
                        assertThat(item.requiresConfirmation()).isTrue();
                    });
                })
                .anySatisfy(scene -> {
                    assertThat(scene.questionSceneCode()).isEqualTo("qa");
                    assertThat(scene.items()).extracting("code").contains("category", "mainBusiness");
                });
    }
}
