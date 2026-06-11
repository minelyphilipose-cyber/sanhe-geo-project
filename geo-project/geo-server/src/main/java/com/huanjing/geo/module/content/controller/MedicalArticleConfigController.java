package com.huanjing.geo.module.content.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.ChannelStyleSaveRequest;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.ChannelStyleVO;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.ComplianceHitLogVO;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.ComplianceKernelSaveRequest;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.ComplianceKernelVO;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.ComplianceRuleSaveRequest;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.ComplianceRuleVO;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.TopicAngleSaveRequest;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.TopicAngleVO;
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
@RequestMapping("/api/content/medical-articles")
@RequiredArgsConstructor
public class MedicalArticleConfigController {

    private final MedicalArticleConfigService service;

    @GetMapping("/topic-angles")
    public R<Page<TopicAngleVO>> topicAngles(@RequestParam(required = false) String industryCode,
                                             @RequestParam(required = false) String categoryCode,
                                             @RequestParam(required = false) Boolean enabled,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(defaultValue = "1") long current,
                                             @RequestParam(defaultValue = "10") long size) {
        return R.ok(service.pageTopicAngles(industryCode, categoryCode, enabled, keyword, current, size));
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
                                               @RequestParam(defaultValue = "1") long current,
                                               @RequestParam(defaultValue = "10") long size) {
        return R.ok(service.pageHitLogs(articleId, batchId, taskId, current, size));
    }
}
