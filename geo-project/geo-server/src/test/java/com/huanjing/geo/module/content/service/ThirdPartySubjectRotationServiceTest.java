package com.huanjing.geo.module.content.service;

import com.huanjing.geo.module.content.constant.ArticlePromptChannels;
import com.huanjing.geo.module.content.constant.TemplatePerspectiveCodes;
import com.huanjing.geo.module.content.dto.SubjectBrandLastSelectedRow;
import com.huanjing.geo.module.content.dto.ThirdPartySubjectPoolBrandRow;
import com.huanjing.geo.module.content.dto.ThirdPartySubjectPoolPreviewResponse;
import com.huanjing.geo.module.content.mapper.BatchArticleGenerationTaskMapper;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.project.entity.Project;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ThirdPartySubjectRotationServiceTest {

    private final BrandMapper brandMapper = mock(BrandMapper.class);
    private final BatchArticleGenerationTaskMapper taskMapper = mock(BatchArticleGenerationTaskMapper.class);
    private final ThirdPartySubjectRotationService service =
            new ThirdPartySubjectRotationService(brandMapper, taskMapper, new SpecialIndustryService());

    @Test
    void resolveKeepsSourceWhenPerspectiveIsCustomer() {
        Project sourceProject = project(10L, 1L);
        Brand sourceBrand = brand(1L, "[\"__ALL__\"]");

        ThirdPartySubjectRotationService.RotationResult result = service.resolve(
                sourceProject,
                sourceBrand,
                ArticlePromptChannels.SELF_MEDIA,
                TemplatePerspectiveCodes.CUSTOMER
        );

        assertThat(result.rotated()).isFalse();
        assertThat(result.subjectBrandId()).isEqualTo(1L);
        assertThat(result.subjectProjectId()).isEqualTo(10L);
        verifyNoInteractions(brandMapper, taskMapper);
    }

    @Test
    void resolveKeepsSourceWhenPerspectiveIsNotThirdParty() {
        Project sourceProject = project(10L, 1L);
        Brand sourceBrand = brand(1L, "[\"__ALL__\"]");

        ThirdPartySubjectRotationService.RotationResult result = service.resolve(
                sourceProject,
                sourceBrand,
                ArticlePromptChannels.SELF_MEDIA,
                "some_future_non_customer_perspective"
        );

        assertThat(result.rotated()).isFalse();
        assertThat(result.subjectBrandId()).isEqualTo(1L);
        assertThat(result.subjectProjectId()).isEqualTo(10L);
        verifyNoInteractions(brandMapper, taskMapper);
    }

    @Test
    void resolvePicksNeverSelectedCandidateBeforeRecentlySelectedOne() {
        Project sourceProject = project(10L, 1L);
        Brand sourceBrand = brand(1L, "[\"__ALL__\"]");
        SubjectBrandLastSelectedRow row = new SubjectBrandLastSelectedRow();
        row.setSubjectBrandId(2L);
        row.setLastSelectedTaskId(100L);
        row.setLastSelectedAt(LocalDateTime.now().minusDays(1));

        when(brandMapper.lockActiveBrandById(1L)).thenReturn(1L);
        when(brandMapper.selectThirdPartySubjectPoolRows()).thenReturn(List.of(
                poolRow(1L, "百业观察", "综合", true, "signed", true, 10L, null),
                poolRow(2L, "已选过品牌", "消费电子", true, "signed", true, 20L, null),
                poolRow(3L, "未选过品牌", "消费电子", true, "signed", true, 30L, null)
        ));
        when(taskMapper.selectLastSelectedBySourceBrand(1L, List.of(2L, 3L))).thenReturn(List.of(row));

        ThirdPartySubjectRotationService.RotationResult result = service.resolve(
                sourceProject,
                sourceBrand,
                ArticlePromptChannels.SELF_MEDIA,
                TemplatePerspectiveCodes.INDUSTRY_NEUTRAL
        );

        assertThat(result.rotated()).isTrue();
        assertThat(result.sourceBrandId()).isEqualTo(1L);
        assertThat(result.subjectBrandId()).isEqualTo(3L);
        assertThat(result.subjectProjectId()).isEqualTo(30L);
        verify(brandMapper).lockActiveBrandById(1L);
    }

    @Test
    void resolveUsesTaskIdWhenCandidatesShareSameSelectedTime() {
        Project sourceProject = project(10L, 1L);
        Brand sourceBrand = brand(1L, "[\"__ALL__\"]");
        LocalDateTime sameTime = LocalDateTime.now().minusMinutes(5);
        SubjectBrandLastSelectedRow earlierRow = lastSelectedRow(2L, 100L, sameTime);
        SubjectBrandLastSelectedRow laterRow = lastSelectedRow(3L, 101L, sameTime);

        when(brandMapper.lockActiveBrandById(1L)).thenReturn(1L);
        when(brandMapper.selectThirdPartySubjectPoolRows()).thenReturn(List.of(
                poolRow(2L, "较早选中品牌", "消费电子", true, "signed", true, 20L, null),
                poolRow(3L, "较晚选中品牌", "消费电子", true, "signed", true, 30L, null)
        ));
        when(taskMapper.selectLastSelectedBySourceBrand(1L, List.of(2L, 3L)))
                .thenReturn(List.of(earlierRow, laterRow));

        ThirdPartySubjectRotationService.RotationResult result = service.resolve(
                sourceProject,
                sourceBrand,
                ArticlePromptChannels.SELF_MEDIA,
                TemplatePerspectiveCodes.REVIEW_RECOMMEND
        );

        assertThat(result.rotated()).isTrue();
        assertThat(result.subjectBrandId()).isEqualTo(2L);
        assertThat(result.subjectProjectId()).isEqualTo(20L);
    }

    @Test
    void previewPoolSplitsCandidatesAndExcludedRowsWithReasons() {
        Brand sourceBrand = brand(1L, "[\"消费电子\"]");
        SubjectBrandLastSelectedRow selectedRow = new SubjectBrandLastSelectedRow();
        selectedRow.setSubjectBrandId(2L);
        selectedRow.setLastSelectedTaskId(200L);
        selectedRow.setLastSelectedAt(LocalDateTime.now().minusHours(2));

        when(brandMapper.selectById(1L)).thenReturn(sourceBrand);
        when(brandMapper.selectThirdPartySubjectPoolRows()).thenReturn(List.of(
                poolRow(1L, "百业观察", "消费电子", true, "signed", true, 10L, null),
                poolRow(2L, "华为", "消费电子", true, "signed", true, 20L, null),
                poolRow(3L, "小米", "消费电子", true, "signed", true, 30L, null),
                poolRow(4L, "口腔品牌", "口腔", true, "signed", true, 40L, "oral"),
                poolRow(5L, "未签约品牌", "消费电子", true, "trial", true, 50L, null),
                poolRow(6L, "餐饮品牌", "餐饮", true, "signed", true, 60L, null),
                poolRow(7L, "关闭推广品牌", "消费电子", false, "signed", true, 70L, null),
                poolRow(8L, "无项目品牌", "消费电子", true, "signed", true, null, null)
        ));
        when(taskMapper.selectLastSelectedBySourceBrand(1L, List.of(2L, 3L))).thenReturn(List.of(selectedRow));

        ThirdPartySubjectPoolPreviewResponse response = service.previewPool(1L);

        assertThat(response.validSource()).isTrue();
        assertThat(response.candidateCount()).isEqualTo(2);
        assertThat(response.candidates()).extracting(ThirdPartySubjectPoolPreviewResponse.Item::brandId)
                .containsExactly(3L, 2L);
        assertThat(response.candidates().get(0).lastSelectedAt()).isNull();
        assertThat(response.excluded()).extracting(ThirdPartySubjectPoolPreviewResponse.Item::reasonCode)
                .contains(
                        "source_self",
                        "medical_or_oral_excluded",
                        "company_not_signed",
                        "industry_not_matched",
                        "promotion_disabled",
                        "no_active_project"
                );
    }

    @Test
    void previewPoolMarksAllRowsExcludedWhenSourceHasNoCoverage() {
        Brand sourceBrand = brand(1L, null);

        when(brandMapper.selectById(1L)).thenReturn(sourceBrand);
        when(brandMapper.selectThirdPartySubjectPoolRows()).thenReturn(List.of(
                poolRow(2L, "华为", "消费电子", true, "signed", true, 20L, null)
        ));

        ThirdPartySubjectPoolPreviewResponse response = service.previewPool(1L);

        assertThat(response.validSource()).isFalse();
        assertThat(response.candidates()).isEmpty();
        assertThat(response.excluded()).singleElement()
                .extracting(ThirdPartySubjectPoolPreviewResponse.Item::reasonCode)
                .isEqualTo("source_not_configured");
    }

    @Test
    void previewPoolKeepsTotalCountsWhenDisplayRowsAreLimited() {
        Brand sourceBrand = brand(1L, "[\"消费电子\"]");

        when(brandMapper.selectById(1L)).thenReturn(sourceBrand);
        when(brandMapper.selectThirdPartySubjectPoolRows()).thenReturn(List.of(
                poolRow(2L, "华为", "消费电子", true, "signed", true, 20L, null),
                poolRow(3L, "小米", "消费电子", true, "signed", true, 30L, null),
                poolRow(4L, "未签约品牌A", "消费电子", true, "trial", true, 40L, null),
                poolRow(5L, "未签约品牌B", "消费电子", true, "trial", true, 50L, null)
        ));
        when(taskMapper.selectLastSelectedBySourceBrand(1L, List.of(2L, 3L))).thenReturn(List.of());

        ThirdPartySubjectPoolPreviewResponse response = service.previewPool(1L, 1, 1);

        assertThat(response.candidateCount()).isEqualTo(2);
        assertThat(response.excludedCount()).isEqualTo(2);
        assertThat(response.candidateDisplayCount()).isEqualTo(1);
        assertThat(response.excludedDisplayCount()).isEqualTo(1);
        assertThat(response.candidates()).hasSize(1);
        assertThat(response.excluded()).hasSize(1);
    }

    private Project project(Long id, Long brandId) {
        Project project = new Project();
        project.setId(id);
        project.setBrandId(brandId);
        return project;
    }

    private SubjectBrandLastSelectedRow lastSelectedRow(Long subjectBrandId, Long taskId, LocalDateTime lastSelectedAt) {
        SubjectBrandLastSelectedRow row = new SubjectBrandLastSelectedRow();
        row.setSubjectBrandId(subjectBrandId);
        row.setLastSelectedTaskId(taskId);
        row.setLastSelectedAt(lastSelectedAt);
        return row;
    }

    private Brand brand(Long id, String coverableIndustries) {
        Brand brand = new Brand();
        brand.setId(id);
        brand.setCoverableIndustries(coverableIndustries);
        brand.setBrandName("brand-" + id);
        return brand;
    }

    private ThirdPartySubjectPoolBrandRow poolRow(Long id,
                                                  String name,
                                                  String industry,
                                                  boolean allowThirdPartyPromotion,
                                                  String companyStatus,
                                                  boolean hasActivePackage,
                                                  Long subjectProjectId,
                                                  String complianceIndustryCode) {
        ThirdPartySubjectPoolBrandRow row = new ThirdPartySubjectPoolBrandRow();
        row.setBrandId(id);
        row.setBrandName(name);
        row.setIndustry(industry);
        row.setAllowThirdPartyPromotion(allowThirdPartyPromotion);
        row.setCompanyStatus(companyStatus);
        row.setHasActivePackage(hasActivePackage);
        row.setSubjectProjectId(subjectProjectId);
        row.setComplianceIndustryCode(complianceIndustryCode);
        row.setCompanyId(id + 1000);
        row.setCompanyName("company-" + id);
        return row;
    }
}
