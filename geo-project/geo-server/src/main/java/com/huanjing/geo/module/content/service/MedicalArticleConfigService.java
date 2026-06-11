package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.ChannelStyleSaveRequest;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.ChannelStyleVO;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.ComplianceHitLogVO;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.ComplianceKernelSaveRequest;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.ComplianceKernelVO;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.ComplianceRuleSaveRequest;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.ComplianceRuleVO;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.TopicAngleSaveRequest;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.TopicAngleVO;
import com.huanjing.geo.module.content.entity.MedicalChannelStyleModule;
import com.huanjing.geo.module.content.entity.MedicalComplianceHitLog;
import com.huanjing.geo.module.content.entity.MedicalComplianceKernel;
import com.huanjing.geo.module.content.entity.MedicalComplianceRule;
import com.huanjing.geo.module.content.entity.MedicalTopicAngle;
import com.huanjing.geo.module.content.mapper.MedicalChannelStyleModuleMapper;
import com.huanjing.geo.module.content.mapper.MedicalComplianceHitLogMapper;
import com.huanjing.geo.module.content.mapper.MedicalComplianceKernelMapper;
import com.huanjing.geo.module.content.mapper.MedicalComplianceRuleMapper;
import com.huanjing.geo.module.content.mapper.MedicalTopicAngleMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MedicalArticleConfigService {

    private final MedicalTopicAngleMapper topicAngleMapper;
    private final MedicalComplianceRuleMapper ruleMapper;
    private final MedicalComplianceKernelMapper kernelMapper;
    private final MedicalChannelStyleModuleMapper channelStyleMapper;
    private final MedicalComplianceHitLogMapper hitLogMapper;
    private final CurrentUserService currentUserService;

    public Page<TopicAngleVO> pageTopicAngles(String industryCode,
                                              String categoryCode,
                                              Boolean enabled,
                                              String keyword,
                                              long current,
                                              long size) {
        currentUserService.ensurePermission("project.read");
        LambdaQueryWrapper<MedicalTopicAngle> wrapper = new LambdaQueryWrapper<MedicalTopicAngle>()
                .eq(StringUtils.hasText(industryCode), MedicalTopicAngle::getIndustryCode, trim(industryCode))
                .eq(StringUtils.hasText(categoryCode), MedicalTopicAngle::getCategoryCode, trim(categoryCode))
                .eq(enabled != null, MedicalTopicAngle::getEnabled, enabled)
                .isNull(MedicalTopicAngle::getDeletedAt)
                .and(StringUtils.hasText(keyword), q -> q
                        .like(MedicalTopicAngle::getTopicAngle, trim(keyword))
                        .or()
                        .like(MedicalTopicAngle::getCategoryName, trim(keyword)))
                .orderByAsc(MedicalTopicAngle::getSortOrder, MedicalTopicAngle::getId);
        Page<MedicalTopicAngle> page = topicAngleMapper.selectPage(new Page<>(current, size), wrapper);
        Page<TopicAngleVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toVO).toList());
        return result;
    }

    @Transactional
    public TopicAngleVO createTopicAngle(TopicAngleSaveRequest req) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("content.prompt_template.manage");
        MedicalTopicAngle row = new MedicalTopicAngle();
        fill(row, req);
        row.setCreatedBy(operator.getId());
        topicAngleMapper.insert(row);
        return toVO(row);
    }

    @Transactional
    public TopicAngleVO updateTopicAngle(Long id, TopicAngleSaveRequest req) {
        currentUserService.ensurePermission("content.prompt_template.manage");
        MedicalTopicAngle row = requireTopicAngle(id);
        fill(row, req);
        topicAngleMapper.updateById(row);
        return toVO(row);
    }

    @Transactional
    public void deleteTopicAngle(Long id) {
        currentUserService.ensurePermission("content.prompt_template.manage");
        MedicalTopicAngle row = requireTopicAngle(id);
        row.setDeletedAt(LocalDateTime.now());
        topicAngleMapper.updateById(row);
    }

    public Page<ComplianceRuleVO> pageRules(String ruleType,
                                            String industryCode,
                                            String channelTier,
                                            Boolean enabled,
                                            long current,
                                            long size) {
        currentUserService.ensurePermission("project.read");
        LambdaQueryWrapper<MedicalComplianceRule> wrapper = new LambdaQueryWrapper<MedicalComplianceRule>()
                .eq(StringUtils.hasText(ruleType), MedicalComplianceRule::getRuleType, trim(ruleType))
                .eq(StringUtils.hasText(industryCode), MedicalComplianceRule::getIndustryCode, trim(industryCode))
                .eq(StringUtils.hasText(channelTier), MedicalComplianceRule::getChannelTier, trim(channelTier))
                .eq(enabled != null, MedicalComplianceRule::getEnabled, enabled)
                .orderByDesc(MedicalComplianceRule::getUpdatedAt, MedicalComplianceRule::getId);
        Page<MedicalComplianceRule> page = ruleMapper.selectPage(new Page<>(current, size), wrapper);
        Page<ComplianceRuleVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toVO).toList());
        return result;
    }

    @Transactional
    public ComplianceRuleVO createRule(ComplianceRuleSaveRequest req) {
        currentUserService.ensurePermission("content.prompt_template.manage");
        MedicalComplianceRule row = new MedicalComplianceRule();
        fill(row, req);
        ruleMapper.insert(row);
        return toVO(row);
    }

    @Transactional
    public ComplianceRuleVO updateRule(Long id, ComplianceRuleSaveRequest req) {
        currentUserService.ensurePermission("content.prompt_template.manage");
        MedicalComplianceRule row = requireRule(id);
        fill(row, req);
        ruleMapper.updateById(row);
        return toVO(row);
    }

    public Page<ComplianceKernelVO> pageKernels(String industryCode, String channelTier, Boolean enabled, long current, long size) {
        currentUserService.ensurePermission("project.read");
        LambdaQueryWrapper<MedicalComplianceKernel> wrapper = new LambdaQueryWrapper<MedicalComplianceKernel>()
                .eq(StringUtils.hasText(industryCode), MedicalComplianceKernel::getIndustryCode, trim(industryCode))
                .eq(StringUtils.hasText(channelTier), MedicalComplianceKernel::getChannelTier, trim(channelTier))
                .eq(enabled != null, MedicalComplianceKernel::getEnabled, enabled)
                .orderByAsc(MedicalComplianceKernel::getIndustryCode, MedicalComplianceKernel::getChannelTier)
                .orderByDesc(MedicalComplianceKernel::getVersionNo);
        Page<MedicalComplianceKernel> page = kernelMapper.selectPage(new Page<>(current, size), wrapper);
        Page<ComplianceKernelVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toVO).toList());
        return result;
    }

    @Transactional
    public ComplianceKernelVO saveKernel(ComplianceKernelSaveRequest req) {
        currentUserService.ensurePermission("content.prompt_template.manage");
        MedicalComplianceKernel row = new MedicalComplianceKernel();
        row.setIndustryCode(trim(req.industryCode()));
        row.setChannelTier(trim(req.channelTier()));
        row.setKernelName(trim(req.kernelName()));
        row.setSystemPrompt(trim(req.systemPrompt()));
        row.setBrandExposureLimit(Math.max(0, req.brandExposureLimit()));
        row.setRequireManualPublishReview(Boolean.TRUE.equals(req.requireManualPublishReview()));
        row.setEnabled(req.enabled() == null || req.enabled());
        row.setVersionNo(req.versionNo() == null ? 1 : Math.max(1, req.versionNo()));
        row.setCreatedBy(currentUserService.requireCurrentUser().getId());
        kernelMapper.insert(row);
        return toVO(row);
    }

    public Page<ChannelStyleVO> pageChannelStyles(String channelGroupCode, String channelTier, Boolean enabled, long current, long size) {
        currentUserService.ensurePermission("project.read");
        LambdaQueryWrapper<MedicalChannelStyleModule> wrapper = new LambdaQueryWrapper<MedicalChannelStyleModule>()
                .eq(StringUtils.hasText(channelGroupCode), MedicalChannelStyleModule::getChannelGroupCode, trim(channelGroupCode))
                .eq(StringUtils.hasText(channelTier), MedicalChannelStyleModule::getChannelTier, trim(channelTier))
                .eq(enabled != null, MedicalChannelStyleModule::getEnabled, enabled)
                .orderByAsc(MedicalChannelStyleModule::getChannelTier, MedicalChannelStyleModule::getChannelGroupCode);
        Page<MedicalChannelStyleModule> page = channelStyleMapper.selectPage(new Page<>(current, size), wrapper);
        Page<ChannelStyleVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toVO).toList());
        return result;
    }

    @Transactional
    public ChannelStyleVO createChannelStyle(ChannelStyleSaveRequest req) {
        currentUserService.ensurePermission("content.prompt_template.manage");
        MedicalChannelStyleModule row = new MedicalChannelStyleModule();
        fill(row, req);
        channelStyleMapper.insert(row);
        return toVO(row);
    }

    @Transactional
    public ChannelStyleVO updateChannelStyle(Long id, ChannelStyleSaveRequest req) {
        currentUserService.ensurePermission("content.prompt_template.manage");
        MedicalChannelStyleModule row = channelStyleMapper.selectById(id);
        if (row == null) {
            throw new BizException(404, "Medical channel style not found");
        }
        fill(row, req);
        channelStyleMapper.updateById(row);
        return toVO(row);
    }

    public Page<ComplianceHitLogVO> pageHitLogs(Long articleId, Long batchId, Long taskId, long current, long size) {
        currentUserService.ensurePermission("project.read");
        LambdaQueryWrapper<MedicalComplianceHitLog> wrapper = new LambdaQueryWrapper<MedicalComplianceHitLog>()
                .eq(articleId != null, MedicalComplianceHitLog::getArticleId, articleId)
                .eq(batchId != null, MedicalComplianceHitLog::getBatchId, batchId)
                .eq(taskId != null, MedicalComplianceHitLog::getTaskId, taskId)
                .orderByDesc(MedicalComplianceHitLog::getCreatedAt, MedicalComplianceHitLog::getId);
        Page<MedicalComplianceHitLog> page = hitLogMapper.selectPage(new Page<>(current, size), wrapper);
        Page<ComplianceHitLogVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toVO).toList());
        return result;
    }

    private MedicalTopicAngle requireTopicAngle(Long id) {
        MedicalTopicAngle row = topicAngleMapper.selectById(id);
        if (row == null || row.getDeletedAt() != null) {
            throw new BizException(404, "Medical topic angle not found");
        }
        return row;
    }

    private MedicalComplianceRule requireRule(Long id) {
        MedicalComplianceRule row = ruleMapper.selectById(id);
        if (row == null) {
            throw new BizException(404, "Medical compliance rule not found");
        }
        return row;
    }

    private void fill(MedicalTopicAngle row, TopicAngleSaveRequest req) {
        row.setIndustryCode(trim(req.industryCode()));
        row.setIndustryName(trim(req.industryName()));
        row.setCategoryCode(trim(req.categoryCode()));
        row.setCategoryName(trim(req.categoryName()));
        row.setTopicAngle(trim(req.topicAngle()));
        row.setRecommendedFocus(trimToNull(req.recommendedFocus()));
        row.setEnabled(req.enabled() == null || req.enabled());
        row.setSortOrder(req.sortOrder() == null ? 0 : req.sortOrder());
    }

    private void fill(MedicalComplianceRule row, ComplianceRuleSaveRequest req) {
        row.setRuleType(trim(req.ruleType()));
        row.setIndustryCode(trimToNull(req.industryCode()));
        row.setChannelTier(trimToNull(req.channelTier()));
        row.setChannelGroupCode(trimToNull(req.channelGroupCode()));
        row.setChannelSubCode(trimToNull(req.channelSubCode()));
        row.setPattern(trim(req.pattern()));
        row.setMatchMode(StringUtils.hasText(req.matchMode()) ? trim(req.matchMode()) : "contains");
        row.setSeverity(StringUtils.hasText(req.severity()) ? trim(req.severity()) : "block");
        row.setEnabled(req.enabled() == null || req.enabled());
        row.setRemark(trimToNull(req.remark()));
    }

    private void fill(MedicalChannelStyleModule row, ChannelStyleSaveRequest req) {
        row.setChannelGroupCode(trim(req.channelGroupCode()));
        row.setChannelSubCode(trimToNull(req.channelSubCode()));
        row.setChannelTier(trim(req.channelTier()));
        row.setStylePrompt(trim(req.stylePrompt()));
        row.setHighRisk(Boolean.TRUE.equals(req.highRisk()));
        row.setEnabled(req.enabled() == null || req.enabled());
    }

    private TopicAngleVO toVO(MedicalTopicAngle row) {
        return new TopicAngleVO(row.getId(), row.getIndustryCode(), row.getIndustryName(), row.getCategoryCode(),
                row.getCategoryName(), row.getTopicAngle(), row.getRecommendedFocus(), row.getEnabled(),
                row.getSortOrder(), row.getCreatedAt(), row.getUpdatedAt());
    }

    private ComplianceRuleVO toVO(MedicalComplianceRule row) {
        return new ComplianceRuleVO(row.getId(), row.getRuleType(), row.getIndustryCode(), row.getChannelTier(),
                row.getChannelGroupCode(), row.getChannelSubCode(), row.getPattern(), row.getMatchMode(),
                row.getSeverity(), row.getEnabled(), row.getRemark(), row.getCreatedAt(), row.getUpdatedAt());
    }

    private ComplianceKernelVO toVO(MedicalComplianceKernel row) {
        return new ComplianceKernelVO(row.getId(), row.getIndustryCode(), row.getChannelTier(), row.getKernelName(),
                row.getSystemPrompt(), row.getBrandExposureLimit(), row.getRequireManualPublishReview(),
                row.getEnabled(), row.getVersionNo(), row.getCreatedAt(), row.getUpdatedAt());
    }

    private ChannelStyleVO toVO(MedicalChannelStyleModule row) {
        return new ChannelStyleVO(row.getId(), row.getChannelGroupCode(), row.getChannelSubCode(),
                row.getChannelTier(), row.getStylePrompt(), row.getHighRisk(), row.getEnabled(),
                row.getCreatedAt(), row.getUpdatedAt());
    }

    private ComplianceHitLogVO toVO(MedicalComplianceHitLog row) {
        return new ComplianceHitLogVO(row.getId(), row.getArticleId(), row.getBatchId(), row.getTaskId(),
                row.getProjectId(), row.getBrandId(), row.getRuleId(), row.getRuleType(), row.getMatchedText(),
                row.getCheckStage(), row.getAction(), row.getCreatedAt());
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
