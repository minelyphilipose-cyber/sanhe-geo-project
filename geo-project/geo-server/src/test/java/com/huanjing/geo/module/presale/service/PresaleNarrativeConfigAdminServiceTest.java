package com.huanjing.geo.module.presale.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.presale.dto.request.PresaleIndustryBucketMappingUpdateRequest;
import com.huanjing.geo.module.presale.dto.request.PresaleLexiconBucketCreateRequest;
import com.huanjing.geo.module.presale.generate.PresaleEvaluationModelRouter;
import com.huanjing.geo.module.presale.generate.llm.PresaleLlmInvoker;
import com.huanjing.geo.module.presale.persist.entity.PresaleIndustryBucketMapping;
import com.huanjing.geo.module.presale.persist.entity.PresaleIndustryBucketReviewTask;
import com.huanjing.geo.module.presale.persist.entity.PresaleLexiconBucket;
import com.huanjing.geo.module.presale.persist.mapper.PresaleHeatmapSummaryMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleIndustryBucketMappingMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleIndustryBucketReviewTaskMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleLexiconBucketMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleNarrativeFindingCopyMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.ActivityLogService;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PresaleNarrativeConfigAdminServiceTest {

    private PresaleLexiconBucketMapper bucketMapper;
    private PresaleIndustryBucketMappingMapper mappingMapper;
    private PresaleIndustryBucketReviewTaskMapper taskMapper;
    private PresaleNarrativeConfigAdminService service;

    @BeforeEach
    void setUp() {
        bucketMapper = mock(PresaleLexiconBucketMapper.class);
        mappingMapper = mock(PresaleIndustryBucketMappingMapper.class);
        taskMapper = mock(PresaleIndustryBucketReviewTaskMapper.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        SysUser user = new SysUser();
        user.setId(7L);
        user.setRole("manager");
        when(currentUserService.requireCurrentUser()).thenReturn(user);

        service = new PresaleNarrativeConfigAdminService(
                mock(PresaleNarrativeFindingCopyMapper.class),
                mock(PresaleHeatmapSummaryMapper.class),
                bucketMapper,
                mappingMapper,
                taskMapper,
                mock(PresaleLlmInvoker.class),
                mock(PresaleEvaluationModelRouter.class),
                new ObjectMapper(),
                currentUserService,
                mock(ActivityLogService.class)
        );
    }

    @Test
    void approveRejectsPendingTask() {
        PresaleIndustryBucketReviewTask task = task("PENDING", null);
        when(taskMapper.selectById(1L)).thenReturn(task);

        assertThrows(BizException.class, () -> service.approveIndustryBucketTask(1L));
        verify(mappingMapper, never()).insert(any());
    }

    @Test
    void approveRejectsSuggestNewBucketDraft() {
        PresaleIndustryBucketReviewTask task = task("DRAFTED",
                "{\"bucket_code\":\"_ALL_\",\"suggest_new_bucket\":true}");
        when(taskMapper.selectById(1L)).thenReturn(task);
        when(bucketMapper.selectList(any())).thenReturn(List.of(bucket("_ALL_")));

        assertThrows(BizException.class, () -> service.approveIndustryBucketTask(1L));
        verify(mappingMapper, never()).insert(any());
    }

    @Test
    void approveRejectsDraftContainingFreeTerms() {
        PresaleIndustryBucketReviewTask task = task("DRAFTED",
                "{\"bucket_code\":\"MEDICAL\",\"customer_term\":\"患者\",\"conversion_term\":\"到诊\"}");
        when(taskMapper.selectById(1L)).thenReturn(task);

        assertThrows(BizException.class, () -> service.approveIndustryBucketTask(1L));
        verify(mappingMapper, never()).insert(any());
    }

    @Test
    void approveWritesMappingForDraftedTask() {
        PresaleIndustryBucketReviewTask task = task("DRAFTED",
                "{\"bucket_code\":\"MEDICAL\",\"industry_short\":\"口腔\",\"suggest_new_bucket\":false}");
        when(taskMapper.selectById(1L)).thenReturn(task);
        when(bucketMapper.selectList(any())).thenReturn(List.of(bucket("MEDICAL")));
        when(bucketMapper.selectOne(any())).thenReturn(bucket("MEDICAL"));
        when(mappingMapper.selectOne(any())).thenReturn(null);

        service.approveIndustryBucketTask(1L);

        verify(mappingMapper).insert(any(PresaleIndustryBucketMapping.class));
        verify(taskMapper).updateById(task);
        assertEquals("APPROVED", task.getStatus());
        assertEquals(7L, task.getApprovedBy());
    }

    @Test
    void updateBucketRejectsDisableWhenApprovedMappingReferencesIt() {
        PresaleLexiconBucket bucket = bucket("MEDICAL");
        bucket.setId(9L);
        when(bucketMapper.selectById(9L)).thenReturn(bucket);
        when(mappingMapper.selectCount(any())).thenReturn(1L);

        com.huanjing.geo.module.presale.dto.request.PresaleLexiconBucketUpdateRequest req =
                new com.huanjing.geo.module.presale.dto.request.PresaleLexiconBucketUpdateRequest();
        req.setBucketName("医疗服务");
        req.setCustomerTerm("患者");
        req.setConversionTerm("到诊");
        req.setEnabled(Boolean.FALSE);

        assertThrows(BizException.class, () -> service.updateLexiconBucket(9L, req));
        verify(bucketMapper, never()).updateById(any());
    }

    @Test
    void createBucketRejectsDuplicateCode() {
        when(bucketMapper.selectOne(any())).thenReturn(bucket("NEW_BUCKET"));

        assertThrows(BizException.class, () -> service.createLexiconBucket(createBucketRequest("NEW_BUCKET")));
        verify(bucketMapper, never()).insert(any());
    }

    @Test
    void createBucketInsertsManualBucket() {
        when(bucketMapper.selectOne(any())).thenReturn(null);

        service.createLexiconBucket(createBucketRequest("NEW_BUCKET"));

        verify(bucketMapper).insert(any(PresaleLexiconBucket.class));
    }

    @Test
    void createBucketRejectsForbiddenClaimInTerms() {
        when(bucketMapper.selectOne(any())).thenReturn(null);
        PresaleLexiconBucketCreateRequest req = createBucketRequest("NEW_BUCKET");
        req.setConversionTerm("保证到诊");

        assertThrows(BizException.class, () -> service.createLexiconBucket(req));
        verify(bucketMapper, never()).insert(any());
    }

    @Test
    void updateBucketRejectsForbiddenClaimInTerms() {
        PresaleLexiconBucket bucket = bucket("MEDICAL");
        bucket.setId(9L);
        when(bucketMapper.selectById(9L)).thenReturn(bucket);

        com.huanjing.geo.module.presale.dto.request.PresaleLexiconBucketUpdateRequest req =
                new com.huanjing.geo.module.presale.dto.request.PresaleLexiconBucketUpdateRequest();
        req.setBucketName("医疗服务");
        req.setCustomerTerm("全市第一");
        req.setConversionTerm("到诊");
        req.setEnabled(Boolean.TRUE);

        assertThrows(BizException.class, () -> service.updateLexiconBucket(9L, req));
        verify(bucketMapper, never()).updateById(any());
    }

    @Test
    void updateIndustryMappingRejectsDisabledBucket() {
        PresaleIndustryBucketMapping mapping = mapping();
        when(mappingMapper.selectById(3L)).thenReturn(mapping);
        when(bucketMapper.selectOne(any())).thenReturn(null);

        assertThrows(BizException.class, () -> service.updateIndustryBucketMapping(3L, mappingRequest("DISABLED")));
        verify(mappingMapper, never()).updateById(any());
    }

    @Test
    void updateIndustryMappingWritesManualMapping() {
        PresaleIndustryBucketMapping mapping = mapping();
        when(mappingMapper.selectById(3L)).thenReturn(mapping);
        when(bucketMapper.selectOne(any())).thenReturn(bucket("HOME_SERVICE"));

        service.updateIndustryBucketMapping(3L, mappingRequest("HOME_SERVICE"));

        assertEquals("HOME_SERVICE", mapping.getBucketCode());
        assertEquals("MANUAL_MAPPING", mapping.getSource());
        assertEquals(7L, mapping.getApprovedBy());
        verify(mappingMapper).updateById(mapping);
    }

    private PresaleIndustryBucketReviewTask task(String status, String draftJson) {
        PresaleIndustryBucketReviewTask task = new PresaleIndustryBucketReviewTask();
        task.setId(1L);
        task.setIndustry("口腔医疗");
        task.setIndustryKey("口腔医疗");
        task.setStatus(status);
        task.setDraftJson(draftJson);
        return task;
    }

    private PresaleLexiconBucket bucket(String code) {
        PresaleLexiconBucket bucket = new PresaleLexiconBucket();
        bucket.setBucketCode(code);
        bucket.setBucketName(code);
        bucket.setCustomerTerm("_ALL_".equals(code) ? "客户" : "患者");
        bucket.setConversionTerm("_ALL_".equals(code) ? "下单" : "到诊");
        bucket.setDefaultIndustryShort("_ALL_".equals(code) ? "行业" : "医疗");
        bucket.setEnabled(Boolean.TRUE);
        return bucket;
    }

    private PresaleIndustryBucketMapping mapping() {
        PresaleIndustryBucketMapping mapping = new PresaleIndustryBucketMapping();
        mapping.setId(3L);
        mapping.setIndustry("装修服务");
        mapping.setIndustryKey("装修服务");
        mapping.setBucketCode("_ALL_");
        mapping.setIndustryShort("行业");
        mapping.setApproved(Boolean.TRUE);
        mapping.setSource("APPROVED_TASK");
        return mapping;
    }

    private PresaleLexiconBucketCreateRequest createBucketRequest(String code) {
        PresaleLexiconBucketCreateRequest req = new PresaleLexiconBucketCreateRequest();
        req.setBucketCode(code);
        req.setBucketName("新服务");
        req.setCustomerTerm("客户");
        req.setConversionTerm("咨询");
        req.setDefaultIndustryShort("新行业");
        req.setEnabled(Boolean.TRUE);
        return req;
    }

    private PresaleIndustryBucketMappingUpdateRequest mappingRequest(String code) {
        PresaleIndustryBucketMappingUpdateRequest req = new PresaleIndustryBucketMappingUpdateRequest();
        req.setBucketCode(code);
        req.setIndustryShort("本地服务");
        req.setRemark("人工调整");
        return req;
    }
}
