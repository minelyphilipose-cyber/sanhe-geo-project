package com.huanjing.geo.module.content.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.ChannelStyleSaveRequest;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.ChannelStyleVO;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.ComplianceHitLogVO;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.ComplianceKernelSaveRequest;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.ComplianceKernelVO;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.ComplianceRuleTestRequest;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.ComplianceRuleTestResultVO;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.ComplianceRuleSaveRequest;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.ComplianceRuleVO;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.BatchTraceVO;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.GenerationHistoryVO;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.SpecialIndustryProfileSaveRequest;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.SpecialIndustryProfileVO;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.TopicAngleCategoryVO;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.TopicAngleSaveRequest;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.TopicAngleVO;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.WorkbenchOverviewVO;
import com.huanjing.geo.module.content.service.MedicalArticleConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/content/special-industry")
@RequiredArgsConstructor
public class SpecialIndustryConfigController {

    private final MedicalArticleConfigService service;

    @GetMapping("/overview")
    public R<WorkbenchOverviewVO> overview() {
        return R.ok(service.overview());
    }

    @GetMapping("/profiles")
    public R<Page<SpecialIndustryProfileVO>> profiles(@RequestParam(required = false) Boolean enabled,
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(defaultValue = "1") long current,
                                                      @RequestParam(defaultValue = "10") long size) {
        return R.ok(service.pageProfiles(enabled, keyword, current, size));
    }

    @GetMapping("/profiles/options")
    public R<java.util.List<SpecialIndustryProfileVO>> profileOptions(@RequestParam(required = false) Boolean enabled) {
        return R.ok(service.listProfiles(enabled == null ? true : enabled));
    }

    @PostMapping("/profiles")
    public R<SpecialIndustryProfileVO> createProfile(@Valid @RequestBody SpecialIndustryProfileSaveRequest req) {
        return R.ok(service.createProfile(req));
    }

    @PutMapping("/profiles/{id}")
    public R<SpecialIndustryProfileVO> updateProfile(@PathVariable Long id,
                                                     @Valid @RequestBody SpecialIndustryProfileSaveRequest req) {
        return R.ok(service.updateProfile(id, req));
    }

    @GetMapping("/topic-angles")
    public R<Page<TopicAngleVO>> topicAngles(@RequestParam(required = false) String industryCode,
                                             @RequestParam(required = false) String categoryCode,
                                             @RequestParam(required = false) Boolean enabled,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(defaultValue = "1") long current,
                                             @RequestParam(defaultValue = "10") long size) {
        return R.ok(service.pageTopicAngles(industryCode, categoryCode, enabled, keyword, current, size));
    }

    @GetMapping("/topic-angle-categories")
    public R<java.util.List<TopicAngleCategoryVO>> topicAngleCategories(@RequestParam(required = false) String industryCode,
                                                                        @RequestParam(required = false) Boolean enabled) {
        return R.ok(service.listTopicAngleCategories(industryCode, enabled == null ? true : enabled));
    }

    @PostMapping("/topic-angles")
    public R<TopicAngleVO> createTopicAngle(@Valid @RequestBody TopicAngleSaveRequest req) {
        return R.ok(service.createTopicAngle(req));
    }

    @PutMapping("/topic-angles/{id}")
    public R<TopicAngleVO> updateTopicAngle(@PathVariable Long id, @Valid @RequestBody TopicAngleSaveRequest req) {
        return R.ok(service.updateTopicAngle(id, req));
    }

    @DeleteMapping("/topic-angles/{id}")
    public R<Void> deleteTopicAngle(@PathVariable Long id) {
        service.deleteTopicAngle(id);
        return R.ok();
    }

    @GetMapping("/rules")
    public R<Page<ComplianceRuleVO>> rules(@RequestParam(required = false) String ruleType,
                                           @RequestParam(required = false) String industryCode,
                                           @RequestParam(required = false) String channelTier,
                                           @RequestParam(required = false) Boolean enabled,
                                           @RequestParam(defaultValue = "1") long current,
                                           @RequestParam(defaultValue = "10") long size) {
        return R.ok(service.pageRules(ruleType, industryCode, channelTier, enabled, current, size));
    }

    @PostMapping("/rules")
    public R<ComplianceRuleVO> createRule(@Valid @RequestBody ComplianceRuleSaveRequest req) {
        return R.ok(service.createRule(req));
    }

    @PutMapping("/rules/{id}")
    public R<ComplianceRuleVO> updateRule(@PathVariable Long id, @Valid @RequestBody ComplianceRuleSaveRequest req) {
        return R.ok(service.updateRule(id, req));
    }

    @PostMapping("/rules/test")
    public R<ComplianceRuleTestResultVO> testRule(@Valid @RequestBody ComplianceRuleTestRequest req) {
        return R.ok(service.testRules(req));
    }

    @GetMapping("/kernels")
    public R<Page<ComplianceKernelVO>> kernels(@RequestParam(required = false) String industryCode,
                                               @RequestParam(required = false) String channelTier,
                                               @RequestParam(required = false) Boolean enabled,
                                               @RequestParam(defaultValue = "1") long current,
                                               @RequestParam(defaultValue = "10") long size) {
        return R.ok(service.pageKernels(industryCode, channelTier, enabled, current, size));
    }

    @PostMapping("/kernels")
    public R<ComplianceKernelVO> saveKernel(@Valid @RequestBody ComplianceKernelSaveRequest req) {
        return R.ok(service.saveKernel(req));
    }

    @GetMapping("/channel-styles")
    public R<Page<ChannelStyleVO>> channelStyles(@RequestParam(required = false) String channelGroupCode,
                                                 @RequestParam(required = false) String channelTier,
                                                 @RequestParam(required = false) Boolean enabled,
                                                 @RequestParam(defaultValue = "1") long current,
                                                 @RequestParam(defaultValue = "10") long size) {
        return R.ok(service.pageChannelStyles(channelGroupCode, channelTier, enabled, current, size));
    }

    @PostMapping("/channel-styles")
    public R<ChannelStyleVO> createChannelStyle(@Valid @RequestBody ChannelStyleSaveRequest req) {
        return R.ok(service.createChannelStyle(req));
    }

    @PutMapping("/channel-styles/{id}")
    public R<ChannelStyleVO> updateChannelStyle(@PathVariable Long id, @Valid @RequestBody ChannelStyleSaveRequest req) {
        return R.ok(service.updateChannelStyle(id, req));
    }

    @GetMapping("/hit-logs")
    public R<Page<ComplianceHitLogVO>> hitLogs(@RequestParam(required = false) Long articleId,
                                               @RequestParam(required = false) Long batchId,
                                               @RequestParam(required = false) Long taskId,
                                               @RequestParam(required = false) Long projectId,
                                               @RequestParam(required = false) Long brandId,
                                               @RequestParam(required = false) String projectName,
                                               @RequestParam(required = false) String brandName,
                                               @RequestParam(required = false) String articleTitle,
                                               @RequestParam(required = false) String ruleType,
                                               @RequestParam(required = false) String action,
                                               @RequestParam(required = false) String createdStartDate,
                                               @RequestParam(required = false) String createdEndDate,
                                               @RequestParam(defaultValue = "1") long current,
                                               @RequestParam(defaultValue = "10") long size) {
        return R.ok(service.pageHitLogs(articleId, batchId, taskId, projectId, brandId,
                projectName, brandName, articleTitle, ruleType, action,
                createdStartDate, createdEndDate, current, size));
    }

    @GetMapping("/generation-history")
    public R<Page<GenerationHistoryVO>> generationHistory(@RequestParam(required = false) Long projectId,
                                                          @RequestParam(required = false) Long brandId,
                                                          @RequestParam(required = false) Long articleId,
                                                          @RequestParam(required = false) Long topicAngleId,
                                                          @RequestParam(required = false) String projectName,
                                                          @RequestParam(required = false) String brandName,
                                                          @RequestParam(required = false) String articleTitle,
                                                          @RequestParam(required = false) String topicKeyword,
                                                          @RequestParam(defaultValue = "1") long current,
                                                          @RequestParam(defaultValue = "10") long size) {
        return R.ok(service.pageGenerationHistory(projectId, brandId, articleId, topicAngleId,
                projectName, brandName, articleTitle, topicKeyword, current, size));
    }

    @GetMapping("/batches")
    public R<Page<BatchTraceVO>> batches(@RequestParam(required = false) String status,
                                         @RequestParam(required = false) String industryCode,
                                         @RequestParam(required = false) String projectName,
                                         @RequestParam(required = false) String brandName,
                                         @RequestParam(required = false) String topicKeyword,
                                         @RequestParam(defaultValue = "1") long current,
                                         @RequestParam(defaultValue = "10") long size) {
        return R.ok(service.pageBatches(status, industryCode, projectName, brandName, topicKeyword, current, size));
    }
}
