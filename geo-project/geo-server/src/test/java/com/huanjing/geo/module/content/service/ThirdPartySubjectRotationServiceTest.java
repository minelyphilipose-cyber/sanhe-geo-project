package com.huanjing.geo.module.content.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.constant.ArticlePromptChannels;
import com.huanjing.geo.module.content.constant.TemplatePerspectiveCodes;
import com.huanjing.geo.module.content.dto.SubjectBrandLastSelectedRow;
import com.huanjing.geo.module.content.dto.ThirdPartySubjectPoolBrandRow;
import com.huanjing.geo.module.content.dto.ThirdPartySubjectPoolPreviewResponse;
import com.huanjing.geo.module.content.entity.ThirdPartySubjectPoolItem;
import com.huanjing.geo.module.content.mapper.BatchArticleGenerationTaskMapper;
import com.huanjing.geo.module.content.mapper.ThirdPartySubjectPoolItemMapper;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.system.mapper.SysDictItemMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ThirdPartySubjectRotationServiceTest {

    @Mock
    private BrandMapper brandMapper;
    @Mock
    private BatchArticleGenerationTaskMapper taskMapper;
    @Mock
    private ThirdPartySubjectPoolItemMapper poolItemMapper;
    @Mock
    private SysDictItemMapper sysDictItemMapper;
    @Mock
    private SpecialIndustryService specialIndustryService;
    @Mock
    private ArticleModelResolver articleModelResolver;
    @Mock
    private CurrentUserService currentUserService;

    @Test
    void convertsStandardIndustryKeysToLabelsBeforeLlmMatching() {
        List<String> labels = service().toIndustryLabels(
                List.of("home_decoration", "全屋智能"),
                Map.of("home_decoration", "家装家居")
        );

        assertThat(labels).containsExactly("家装家居", "全屋智能");
    }

    @Test
    void ignoresLlmMatchedIndustriesOutsideCandidateSet() {
        Set<String> matched = service().parseMatchedIndustries(
                """
                        {"matchedIndustries":["智能家居","不存在行业","房产家居行业"]}
                        """,
                List.of("智能家居", "房产家居")
        );

        assertThat(matched).containsExactly("智能家居");
    }

    @Test
    void resolveKeepsSourceWhenPerspectiveIsCustomer() {
        Project sourceProject = sourceProject();
        Brand sourceBrand = sourceBrand();

        ThirdPartySubjectRotationService.RotationResult result = service().resolve(
                sourceProject,
                sourceBrand,
                ArticlePromptChannels.SELF_MEDIA,
                TemplatePerspectiveCodes.CUSTOMER
        );

        assertThat(result.rotated()).isFalse();
        assertThat(result.subjectBrandId()).isEqualTo(1L);
        assertThat(result.subjectProjectId()).isEqualTo(1001L);
        verifyNoInteractions(brandMapper, taskMapper, poolItemMapper);
    }

    @Test
    void resolveChoosesOnlyConfirmedSubjects() {
        Brand sourceBrand = sourceBrand();
        Project sourceProject = sourceProject();
        ThirdPartySubjectPoolItem confirmed = poolItem(3L, 3003L);
        ThirdPartySubjectPoolBrandRow unconfirmedEligible = eligibleRow(2L, "未确认品牌", "智能家居", 3002L);
        ThirdPartySubjectPoolBrandRow confirmedEligible = eligibleRow(3L, "已确认品牌", "装修服务", 3003L);
        stubNoMedical();
        when(poolItemMapper.selectList(any())).thenReturn(List.of(confirmed));
        when(brandMapper.selectThirdPartySubjectPoolRows()).thenReturn(List.of(unconfirmedEligible, confirmedEligible));
        when(taskMapper.selectLastSelectedBySourceBrand(anyLong(), anyList())).thenReturn(List.of());

        ThirdPartySubjectRotationService.RotationResult result = service().resolve(
                sourceProject,
                sourceBrand,
                ArticlePromptChannels.SELF_MEDIA,
                TemplatePerspectiveCodes.INDUSTRY_NEUTRAL
        );

        assertThat(result.rotated()).isTrue();
        assertThat(result.subjectBrandId()).isEqualTo(3L);
        assertThat(result.subjectProjectId()).isEqualTo(3003L);
    }

    @Test
    void resolvePicksNeverSelectedConfirmedSubjectBeforeRecentlySelectedOne() {
        Brand sourceBrand = sourceBrand();
        Project sourceProject = sourceProject();
        SubjectBrandLastSelectedRow selectedRow = lastSelectedRow(2L, 100L, LocalDateTime.now().minusDays(1));
        stubNoMedical();
        when(poolItemMapper.selectList(any())).thenReturn(List.of(poolItem(2L, 2002L), poolItem(3L, 3003L)));
        when(brandMapper.selectThirdPartySubjectPoolRows()).thenReturn(List.of(
                eligibleRow(2L, "已选过品牌", "智能家居", 2002L),
                eligibleRow(3L, "未选过品牌", "智能家居", 3003L)
        ));
        when(taskMapper.selectLastSelectedBySourceBrand(1L, List.of(2L, 3L))).thenReturn(List.of(selectedRow));

        ThirdPartySubjectRotationService.RotationResult result = service().resolve(
                sourceProject,
                sourceBrand,
                ArticlePromptChannels.SELF_MEDIA,
                TemplatePerspectiveCodes.REVIEW_RECOMMEND
        );

        assertThat(result.rotated()).isTrue();
        assertThat(result.subjectBrandId()).isEqualTo(3L);
        assertThat(result.subjectProjectId()).isEqualTo(3003L);
    }

    @Test
    void resolveFailsWhenConfirmedSubjectBecomesIneligible() {
        Brand sourceBrand = sourceBrand();
        Project sourceProject = sourceProject();
        ThirdPartySubjectPoolBrandRow unavailableConfirmed = eligibleRow(2L, "失效品牌", "智能家居", null);
        stubNoMedical();
        when(poolItemMapper.selectList(any())).thenReturn(List.of(poolItem(2L, 2002L)));
        when(brandMapper.selectThirdPartySubjectPoolRows()).thenReturn(List.of(unavailableConfirmed));

        assertThatThrownBy(() -> service().resolve(
                sourceProject,
                sourceBrand,
                ArticlePromptChannels.SELF_MEDIA,
                TemplatePerspectiveCodes.INDUSTRY_NEUTRAL
        ))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已确认的第三方主体当前均不可用");
    }

    @Test
    void previewPoolCountsConfirmedAvailableAndUnavailableSeparately() {
        Brand sourceBrand = sourceBrand();
        ThirdPartySubjectPoolItem availableItem = poolItem(2L, 2002L);
        ThirdPartySubjectPoolItem unavailableItem = poolItem(3L, 3003L);
        ThirdPartySubjectPoolBrandRow availableConfirmed = eligibleRow(2L, "可用品牌", "智能家居", 2002L);
        ThirdPartySubjectPoolBrandRow unavailableConfirmed = eligibleRow(3L, "失效品牌", "装修服务", null);
        ThirdPartySubjectPoolBrandRow availableUnconfirmed = eligibleRow(4L, "未入池品牌", "全屋智能", 4004L);
        stubNoMedical();
        when(brandMapper.selectById(1L)).thenReturn(sourceBrand);
        when(poolItemMapper.selectList(any())).thenReturn(List.of(availableItem, unavailableItem));
        when(brandMapper.selectThirdPartySubjectPoolRows()).thenReturn(List.of(
                availableConfirmed,
                unavailableConfirmed,
                availableUnconfirmed
        ));
        when(taskMapper.selectLastSelectedBySourceBrand(anyLong(), anyList())).thenReturn(List.of());

        ThirdPartySubjectPoolPreviewResponse response = service().previewPool(1L, 10, 10);

        assertThat(response.candidateCount()).isEqualTo(1);
        assertThat(response.confirmedCount()).isEqualTo(2);
        assertThat(response.unavailableCount()).isEqualTo(1);
        assertThat(response.candidates())
                .extracting(ThirdPartySubjectPoolPreviewResponse.Item::brandId)
                .containsExactly(2L, 3L);
        assertThat(response.candidates())
                .filteredOn(item -> item.brandId().equals(3L))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.available()).isFalse();
                    assertThat(item.reasonCode()).isEqualTo("no_active_project");
                });
        assertThat(response.availableSubjects())
                .extracting(ThirdPartySubjectPoolPreviewResponse.Item::brandId)
                .containsExactly(4L);
    }

    @Test
    void previewPoolKeepsConfirmedCountsWhenDisplayRowsAreLimited() {
        Brand sourceBrand = sourceBrand();
        stubNoMedical();
        when(brandMapper.selectById(1L)).thenReturn(sourceBrand);
        when(poolItemMapper.selectList(any())).thenReturn(List.of(poolItem(2L, 2002L), poolItem(3L, 3003L)));
        when(brandMapper.selectThirdPartySubjectPoolRows()).thenReturn(List.of(
                eligibleRow(2L, "可用品牌A", "智能家居", 2002L),
                eligibleRow(3L, "可用品牌B", "装修服务", 3003L),
                eligibleRow(4L, "未签约品牌", "智能家居", 4004L),
                eligibleRow(5L, "无项目品牌", "全屋智能", null)
        ));
        when(taskMapper.selectLastSelectedBySourceBrand(anyLong(), anyList())).thenReturn(List.of());

        ThirdPartySubjectPoolPreviewResponse response = service().previewPool(1L, 1, 1);

        assertThat(response.candidateCount()).isEqualTo(2);
        assertThat(response.confirmedCount()).isEqualTo(2);
        assertThat(response.candidateDisplayCount()).isEqualTo(1);
        assertThat(response.excludedDisplayCount()).isEqualTo(1);
        assertThat(response.candidates()).hasSize(1);
        assertThat(response.excluded()).hasSize(1);
    }

    private ThirdPartySubjectRotationService service() {
        return new ThirdPartySubjectRotationService(
                brandMapper,
                taskMapper,
                poolItemMapper,
                sysDictItemMapper,
                specialIndustryService,
                articleModelResolver,
                null,
                currentUserService
        );
    }

    private Brand sourceBrand() {
        Brand brand = new Brand();
        brand.setId(1L);
        brand.setBrandName("信源品牌");
        brand.setCoverableIndustries("[\"家装家居\",\"智能家居\",\"装修服务\",\"全屋智能\"]");
        return brand;
    }

    private Project sourceProject() {
        Project project = new Project();
        project.setId(1001L);
        project.setBrandId(1L);
        project.setBrandName("信源品牌");
        return project;
    }

    private SubjectBrandLastSelectedRow lastSelectedRow(Long subjectBrandId, Long taskId, LocalDateTime lastSelectedAt) {
        SubjectBrandLastSelectedRow row = new SubjectBrandLastSelectedRow();
        row.setSubjectBrandId(subjectBrandId);
        row.setLastSelectedTaskId(taskId);
        row.setLastSelectedAt(lastSelectedAt);
        return row;
    }

    private ThirdPartySubjectPoolBrandRow eligibleRow(Long brandId, String brandName, String industry, Long subjectProjectId) {
        ThirdPartySubjectPoolBrandRow row = new ThirdPartySubjectPoolBrandRow();
        row.setBrandId(brandId);
        row.setBrandName(brandName);
        row.setIndustry(industry);
        row.setComplianceIndustryCode(industry);
        row.setAllowThirdPartyPromotion(true);
        row.setCompanyId(brandId + 1000L);
        row.setCompanyName(brandName + "公司");
        row.setCompanyStatus("signed");
        row.setHasActivePackage(true);
        row.setSubjectProjectId(subjectProjectId);
        return row;
    }

    private ThirdPartySubjectPoolItem poolItem(Long subjectBrandId, Long subjectProjectId) {
        ThirdPartySubjectPoolItem item = new ThirdPartySubjectPoolItem();
        item.setId(subjectBrandId);
        item.setSourceBrandId(1L);
        item.setSubjectBrandId(subjectBrandId);
        item.setSubjectProjectId(subjectProjectId);
        item.setMatchSource("direct");
        item.setMatchedIndustry("智能家居");
        item.setConfirmedAt(LocalDateTime.now());
        return item;
    }

    private void stubNoMedical() {
        when(specialIndustryService.detectMedicalIndustryCode(any(), any())).thenReturn(Optional.empty());
    }
}
