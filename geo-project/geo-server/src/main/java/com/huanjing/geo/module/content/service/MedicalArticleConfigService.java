package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.ChannelStyleSaveRequest;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.ChannelStyleVO;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.BatchTraceVO;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.ComplianceIssueVO;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.ComplianceHitLogVO;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.ComplianceKernelSaveRequest;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.ComplianceKernelVO;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.ComplianceRuleTestRequest;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.ComplianceRuleTestResultVO;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.ComplianceRuleSaveRequest;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.ComplianceRuleVO;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.GenerationHistoryVO;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.RuleHitSummaryVO;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.TopicAngleCategoryVO;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.TopicAngleSaveRequest;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.TopicAngleVO;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.WorkbenchOverviewVO;
import com.huanjing.geo.module.content.constant.MedicalArticleConstants;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.entity.BatchArticleGenerationBatch;
import com.huanjing.geo.module.content.entity.BatchArticleGenerationTask;
import com.huanjing.geo.module.content.entity.MedicalChannelStyleModule;
import com.huanjing.geo.module.content.entity.MedicalComplianceHitLog;
import com.huanjing.geo.module.content.entity.MedicalComplianceKernel;
import com.huanjing.geo.module.content.entity.MedicalComplianceRule;
import com.huanjing.geo.module.content.entity.MedicalGenerationHistory;
import com.huanjing.geo.module.content.entity.MedicalTopicAngle;
import com.huanjing.geo.module.content.mapper.ArticleDraftMapper;
import com.huanjing.geo.module.content.mapper.BatchArticleGenerationBatchMapper;
import com.huanjing.geo.module.content.mapper.BatchArticleGenerationTaskMapper;
import com.huanjing.geo.module.content.mapper.MedicalChannelStyleModuleMapper;
import com.huanjing.geo.module.content.mapper.MedicalComplianceHitLogMapper;
import com.huanjing.geo.module.content.mapper.MedicalComplianceKernelMapper;
import com.huanjing.geo.module.content.mapper.MedicalComplianceRuleMapper;
import com.huanjing.geo.module.content.mapper.MedicalGenerationHistoryMapper;
import com.huanjing.geo.module.content.mapper.MedicalTopicAngleMapper;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MedicalArticleConfigService {

    private final MedicalTopicAngleMapper topicAngleMapper;
    private final MedicalComplianceRuleMapper ruleMapper;
    private final MedicalComplianceKernelMapper kernelMapper;
    private final MedicalChannelStyleModuleMapper channelStyleMapper;
    private final MedicalComplianceHitLogMapper hitLogMapper;
    private final MedicalGenerationHistoryMapper generationHistoryMapper;
    private final ProjectMapper projectMapper;
    private final BrandMapper brandMapper;
    private final ArticleDraftMapper articleDraftMapper;
    private final BatchArticleGenerationBatchMapper batchMapper;
    private final BatchArticleGenerationTaskMapper taskMapper;
    private final MedicalArticleComplianceChecker complianceChecker;
    private final CurrentUserService currentUserService;
    private final SpecialIndustryService specialIndustryService;

    public WorkbenchOverviewVO overview() {
        currentUserService.ensurePermission("project.read");
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime sevenDaysAgo = now.minusDays(7);
        List<MedicalComplianceHitLog> recentHits = hitLogMapper.selectList(new LambdaQueryWrapper<MedicalComplianceHitLog>()
                .ge(MedicalComplianceHitLog::getCreatedAt, sevenDaysAgo)
                .orderByDesc(MedicalComplianceHitLog::getCreatedAt, MedicalComplianceHitLog::getId));
        List<RuleHitSummaryVO> topRuleHits = recentHits.stream()
                .collect(Collectors.groupingBy(row -> StringUtils.hasText(row.getRuleType()) ? row.getRuleType() : "unknown", Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(8)
                .map(entry -> new RuleHitSummaryVO(entry.getKey(), entry.getValue()))
                .toList();
        return new WorkbenchOverviewVO(
                countArticlesByReviewStatus("pending"),
                countArticlesByReviewStatus("rejected"),
                countArticlesByComplianceStatuses(List.of("failed", MedicalArticleConstants.COMPLIANCE_DISCARDED)),
                countArticlesByComplianceStatuses(List.of(MedicalArticleConstants.COMPLIANCE_DISCARDED)),
                countOfficialPendingArticles(),
                recentHits.stream().filter(row -> row.getCreatedAt() != null && !row.getCreatedAt().isBefore(todayStart)).count(),
                (long) recentHits.size(),
                countDiscardedTasksSince(sevenDaysAgo),
                topRuleHits,
                recentProblemBatches()
        );
    }

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

    public List<TopicAngleCategoryVO> listTopicAngleCategories(String industryCode, Boolean enabled) {
        currentUserService.ensurePermission("project.read");
        List<MedicalTopicAngle> rows = topicAngleMapper.selectList(new LambdaQueryWrapper<MedicalTopicAngle>()
                .eq(StringUtils.hasText(industryCode), MedicalTopicAngle::getIndustryCode, trim(industryCode))
                .eq(enabled != null, MedicalTopicAngle::getEnabled, enabled)
                .isNull(MedicalTopicAngle::getDeletedAt)
                .orderByAsc(MedicalTopicAngle::getIndustryCode, MedicalTopicAngle::getSortOrder, MedicalTopicAngle::getId));
        Map<String, TopicAngleCategoryAccumulator> categories = new LinkedHashMap<>();
        for (MedicalTopicAngle row : rows) {
            if (!StringUtils.hasText(row.getIndustryCode()) || !StringUtils.hasText(row.getCategoryCode())) {
                continue;
            }
            String key = row.getIndustryCode().trim() + "::" + row.getCategoryCode().trim();
            categories.computeIfAbsent(key, ignored -> new TopicAngleCategoryAccumulator(
                    row.getIndustryCode(),
                    row.getIndustryName(),
                    row.getCategoryCode(),
                    row.getCategoryName()
            )).increment();
        }
        return categories.values().stream().map(TopicAngleCategoryAccumulator::toVO).toList();
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

    public Page<ComplianceHitLogVO> pageHitLogs(Long articleId,
                                                Long batchId,
                                                Long taskId,
                                                Long projectId,
                                                Long brandId,
                                                String projectName,
                                                String brandName,
                                                String articleTitle,
                                                String ruleType,
                                                String action,
                                                String createdStartDate,
                                                String createdEndDate,
                                                long current,
                                                long size) {
        currentUserService.ensurePermission("project.read");
        List<Long> projectIds = projectId == null ? idsByProjectName(projectName) : List.of(projectId);
        List<Long> brandIds = brandId == null ? idsByBrandName(brandName) : List.of(brandId);
        List<Long> articleIds = articleId == null ? idsByArticleTitle(articleTitle) : List.of(articleId);
        if ((StringUtils.hasText(projectName) && projectIds.isEmpty())
                || (StringUtils.hasText(brandName) && brandIds.isEmpty())
                || (StringUtils.hasText(articleTitle) && articleIds.isEmpty())) {
            return emptyPage(current, size);
        }
        LambdaQueryWrapper<MedicalComplianceHitLog> wrapper = new LambdaQueryWrapper<MedicalComplianceHitLog>()
                .in(!articleIds.isEmpty(), MedicalComplianceHitLog::getArticleId, articleIds)
                .eq(batchId != null, MedicalComplianceHitLog::getBatchId, batchId)
                .eq(taskId != null, MedicalComplianceHitLog::getTaskId, taskId)
                .in(!projectIds.isEmpty(), MedicalComplianceHitLog::getProjectId, projectIds)
                .in(!brandIds.isEmpty(), MedicalComplianceHitLog::getBrandId, brandIds)
                .eq(StringUtils.hasText(ruleType), MedicalComplianceHitLog::getRuleType, trim(ruleType))
                .eq(StringUtils.hasText(action), MedicalComplianceHitLog::getAction, trim(action))
                .orderByDesc(MedicalComplianceHitLog::getCreatedAt, MedicalComplianceHitLog::getId);
        applyCreatedDateFilter(wrapper, MedicalComplianceHitLog::getCreatedAt, createdStartDate, createdEndDate);
        Page<MedicalComplianceHitLog> page = hitLogMapper.selectPage(new Page<>(current, size), wrapper);
        Page<ComplianceHitLogVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        Map<Long, String> projectNames = projectNames(page.getRecords().stream().map(MedicalComplianceHitLog::getProjectId).toList());
        Map<Long, String> brandNames = brandNames(page.getRecords().stream().map(MedicalComplianceHitLog::getBrandId).toList());
        result.setRecords(page.getRecords().stream().map(row -> toVO(row, projectNames, brandNames)).toList());
        return result;
    }

    public Page<GenerationHistoryVO> pageGenerationHistory(Long projectId,
                                                           Long brandId,
                                                           Long articleId,
                                                           Long topicAngleId,
                                                           String projectName,
                                                           String brandName,
                                                           String articleTitle,
                                                           String topicKeyword,
                                                           long current,
                                                           long size) {
        currentUserService.ensurePermission("project.read");
        List<Long> projectIds = projectId == null ? idsByProjectName(projectName) : List.of(projectId);
        List<Long> brandIds = brandId == null ? idsByBrandName(brandName) : List.of(brandId);
        List<Long> articleIds = articleId == null ? idsByArticleTitle(articleTitle) : List.of(articleId);
        List<Long> topicAngleIds = topicAngleId == null ? idsByTopicKeyword(topicKeyword) : List.of(topicAngleId);
        if ((StringUtils.hasText(projectName) && projectIds.isEmpty())
                || (StringUtils.hasText(brandName) && brandIds.isEmpty())
                || (StringUtils.hasText(articleTitle) && articleIds.isEmpty())
                || (StringUtils.hasText(topicKeyword) && topicAngleIds.isEmpty())) {
            return emptyPage(current, size);
        }
        LambdaQueryWrapper<MedicalGenerationHistory> wrapper = new LambdaQueryWrapper<MedicalGenerationHistory>()
                .in(!projectIds.isEmpty(), MedicalGenerationHistory::getProjectId, projectIds)
                .in(!brandIds.isEmpty(), MedicalGenerationHistory::getBrandId, brandIds)
                .in(!articleIds.isEmpty(), MedicalGenerationHistory::getArticleId, articleIds)
                .in(!topicAngleIds.isEmpty(), MedicalGenerationHistory::getTopicAngleId, topicAngleIds)
                .orderByDesc(MedicalGenerationHistory::getCreatedAt, MedicalGenerationHistory::getId);
        Page<MedicalGenerationHistory> page = generationHistoryMapper.selectPage(new Page<>(current, size), wrapper);
        Page<GenerationHistoryVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        List<MedicalGenerationHistory> records = page.getRecords();
        Map<Long, String> projectNames = projectNames(records.stream().map(MedicalGenerationHistory::getProjectId).toList());
        Map<Long, String> brandNames = brandNames(records.stream().map(MedicalGenerationHistory::getBrandId).toList());
        Map<Long, String> topicAngles = topicAngles(records.stream().map(MedicalGenerationHistory::getTopicAngleId).toList());
        Map<Long, String> articleTitles = articleTitles(records.stream().map(MedicalGenerationHistory::getArticleId).toList());
        result.setRecords(records.stream().map(row -> toVO(row, projectNames, brandNames, topicAngles, articleTitles)).toList());
        return result;
    }

    public Page<BatchTraceVO> pageBatches(String status,
                                          String industryCode,
                                          String projectName,
                                          String brandName,
                                          String topicKeyword,
                                          long current,
                                          long size) {
        currentUserService.ensurePermission("project.read");
        List<Long> projectIds = idsByProjectName(projectName);
        List<Long> brandIds = idsByBrandName(brandName);
        if ((StringUtils.hasText(projectName) && projectIds.isEmpty())
                || (StringUtils.hasText(brandName) && brandIds.isEmpty())) {
            return emptyPage(current, size);
        }
        LambdaQueryWrapper<BatchArticleGenerationBatch> wrapper = new LambdaQueryWrapper<BatchArticleGenerationBatch>()
                .isNotNull(BatchArticleGenerationBatch::getMedicalIndustryCode)
                .eq(StringUtils.hasText(status), BatchArticleGenerationBatch::getStatus, trim(status))
                .eq(StringUtils.hasText(industryCode), BatchArticleGenerationBatch::getMedicalIndustryCode, trim(industryCode))
                .in(!projectIds.isEmpty(), BatchArticleGenerationBatch::getProjectId, projectIds)
                .in(!brandIds.isEmpty(), BatchArticleGenerationBatch::getBrandId, brandIds)
                .like(StringUtils.hasText(topicKeyword), BatchArticleGenerationBatch::getTopic, trim(topicKeyword))
                .orderByDesc(BatchArticleGenerationBatch::getCreatedAt, BatchArticleGenerationBatch::getId);
        Page<BatchArticleGenerationBatch> page = batchMapper.selectPage(new Page<>(current, size), wrapper);
        List<BatchArticleGenerationBatch> records = page.getRecords();
        Map<Long, String> projectNames = projectNames(records.stream().map(BatchArticleGenerationBatch::getProjectId).toList());
        Map<Long, String> brandNames = brandNames(records.stream().map(BatchArticleGenerationBatch::getBrandId).toList());
        Map<Long, List<BatchArticleGenerationTask>> taskMap = records.isEmpty()
                ? Collections.emptyMap()
                : taskMapper.selectList(new LambdaQueryWrapper<BatchArticleGenerationTask>()
                        .in(BatchArticleGenerationTask::getBatchId, records.stream().map(BatchArticleGenerationBatch::getId).toList()))
                .stream()
                .collect(Collectors.groupingBy(BatchArticleGenerationTask::getBatchId));
        Page<BatchTraceVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(records.stream().map(row -> toBatchTrace(row, projectNames, brandNames, taskMap)).toList());
        return result;
    }

    public ComplianceRuleTestResultVO testRules(ComplianceRuleTestRequest req) {
        currentUserService.ensurePermission("content.prompt_template.manage");
        Brand brand = null;
        if (StringUtils.hasText(req.brandName())) {
            brand = new Brand();
            brand.setBrandName(trim(req.brandName()));
        }
        MedicalArticleGenerationService.MedicalPromptContext context = new MedicalArticleGenerationService.MedicalPromptContext(
                StringUtils.hasText(req.industryCode()) ? trim(req.industryCode()) : "medical_beauty",
                StringUtils.hasText(req.channelTier()) ? trim(req.channelTier()) : "education",
                null,
                null,
                null,
                null,
                null,
                null,
                "",
                req.brandExposureLimit() == null ? 2 : Math.max(0, req.brandExposureLimit()),
                false,
                "",
                Boolean.TRUE.equals(req.highRiskChannel()),
                null,
                null,
                null,
                null
        );
        MedicalArticleComplianceChecker.CheckResult result = complianceChecker.check(new MedicalArticleComplianceChecker.CheckInput(
                null,
                null,
                null,
                null,
                trimToNull(req.channelGroupCode()),
                trimToNull(req.channelSubCode()),
                req.title(),
                req.content(),
                brand,
                context
        ));
        return new ComplianceRuleTestResultVO(result.passed(), result.issues().stream()
                .map(issue -> new ComplianceIssueVO(issue.ruleId(), issue.ruleType(), issue.severity(), issue.matchedText(), issue.message()))
                .toList());
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
        String industryCode = trim(req.industryCode());
        row.setIndustryCode(industryCode);
        row.setIndustryName(StringUtils.hasText(req.industryName()) ? trim(req.industryName()) : specialIndustryService.industryLabel(industryCode));
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

    private ComplianceHitLogVO toVO(MedicalComplianceHitLog row, Map<Long, String> projectNames, Map<Long, String> brandNames) {
        return new ComplianceHitLogVO(row.getId(), row.getArticleId(), row.getBatchId(), row.getTaskId(),
                row.getProjectId(), projectNames.get(row.getProjectId()), row.getBrandId(), brandNames.get(row.getBrandId()),
                row.getRuleId(), row.getRuleType(), row.getMatchedText(),
                row.getCheckStage(), row.getAction(), row.getCreatedAt());
    }

    private GenerationHistoryVO toVO(MedicalGenerationHistory row,
                                     Map<Long, String> projectNames,
                                     Map<Long, String> brandNames,
                                     Map<Long, String> topicAngles,
                                     Map<Long, String> articleTitles) {
        return new GenerationHistoryVO(row.getId(), row.getProjectId(), projectNames.get(row.getProjectId()),
                row.getBrandId(), brandNames.get(row.getBrandId()), row.getTopicAngleId(),
                topicAngles.get(row.getTopicAngleId()), row.getStructureSkeleton(), row.getFocus(),
                row.getArticleId(), articleTitles.get(row.getArticleId()), row.getCreatedAt());
    }

    private BatchTraceVO toBatchTrace(BatchArticleGenerationBatch row,
                                      Map<Long, String> projectNames,
                                      Map<Long, String> brandNames,
                                      Map<Long, List<BatchArticleGenerationTask>> taskMap) {
        List<BatchArticleGenerationTask> tasks = taskMap.getOrDefault(row.getId(), List.of());
        int discarded = (int) tasks.stream()
                .filter(task -> MedicalArticleConstants.COMPLIANCE_DISCARDED.equals(task.getComplianceStatus()))
                .count();
        int retried = (int) tasks.stream()
                .filter(task -> task.getRetryCount() != null && task.getRetryCount() > 0)
                .count();
        return new BatchTraceVO(row.getId(), row.getProjectId(), projectNames.get(row.getProjectId()),
                row.getBrandId(), brandNames.get(row.getBrandId()), row.getMedicalIndustryCode(),
                row.getMedicalChannelTier(), row.getTopic(), row.getStatus(), row.getTotalCount(),
                row.getSuccessCount(), row.getFailedCount(), discarded, retried, row.getCreatedAt(),
                row.getFinishedAt(), row.getErrorMessage());
    }

    private Long countArticlesByReviewStatus(String status) {
        return articleDraftMapper.selectCount(new LambdaQueryWrapper<ArticleDraft>()
                .isNotNull(ArticleDraft::getMedicalIndustryCode)
                .eq(ArticleDraft::getPublishReviewStatus, status));
    }

    private Long countArticlesByComplianceStatuses(List<String> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return 0L;
        }
        return articleDraftMapper.selectCount(new LambdaQueryWrapper<ArticleDraft>()
                .isNotNull(ArticleDraft::getMedicalIndustryCode)
                .in(ArticleDraft::getComplianceStatus, statuses));
    }

    private Long countOfficialPendingArticles() {
        return articleDraftMapper.selectCount(new LambdaQueryWrapper<ArticleDraft>()
                .isNotNull(ArticleDraft::getMedicalIndustryCode)
                .eq(ArticleDraft::getMedicalChannelTier, "official_site")
                .eq(ArticleDraft::getPublishReviewStatus, "pending"));
    }

    private Long countDiscardedTasksSince(LocalDateTime since) {
        return taskMapper.selectCount(new LambdaQueryWrapper<BatchArticleGenerationTask>()
                .eq(BatchArticleGenerationTask::getComplianceStatus, MedicalArticleConstants.COMPLIANCE_DISCARDED)
                .ge(BatchArticleGenerationTask::getUpdatedAt, since));
    }

    private List<BatchTraceVO> recentProblemBatches() {
        List<BatchArticleGenerationBatch> batches = batchMapper.selectList(new LambdaQueryWrapper<BatchArticleGenerationBatch>()
                .isNotNull(BatchArticleGenerationBatch::getMedicalIndustryCode)
                .and(wrapper -> wrapper
                        .gt(BatchArticleGenerationBatch::getFailedCount, 0)
                        .or()
                        .eq(BatchArticleGenerationBatch::getStatus, "failed")
                        .or()
                        .eq(BatchArticleGenerationBatch::getStatus, "partial_success"))
                .orderByDesc(BatchArticleGenerationBatch::getUpdatedAt, BatchArticleGenerationBatch::getId)
                .last("limit 5"));
        if (batches.isEmpty()) {
            return List.of();
        }
        Map<Long, String> projectNames = projectNames(batches.stream().map(BatchArticleGenerationBatch::getProjectId).toList());
        Map<Long, String> brandNames = brandNames(batches.stream().map(BatchArticleGenerationBatch::getBrandId).toList());
        Map<Long, List<BatchArticleGenerationTask>> taskMap = taskMapper.selectList(new LambdaQueryWrapper<BatchArticleGenerationTask>()
                        .in(BatchArticleGenerationTask::getBatchId, batches.stream().map(BatchArticleGenerationBatch::getId).toList()))
                .stream()
                .collect(Collectors.groupingBy(BatchArticleGenerationTask::getBatchId));
        return batches.stream().map(row -> toBatchTrace(row, projectNames, brandNames, taskMap)).toList();
    }

    private <T> void applyCreatedDateFilter(LambdaQueryWrapper<T> wrapper,
                                            com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, LocalDateTime> column,
                                            String createdStartDate,
                                            String createdEndDate) {
        LocalDate startDate = parseDate(createdStartDate);
        LocalDate endDate = parseDate(createdEndDate);
        if (startDate != null) {
            wrapper.ge(column, startDate.atStartOfDay());
        }
        if (endDate != null) {
            wrapper.lt(column, endDate.plusDays(1).atStartOfDay());
        }
    }

    private LocalDate parseDate(String value) {
        String text = trimToNull(value);
        return text == null ? null : LocalDate.parse(text);
    }

    private Map<Long, String> projectNames(List<Long> ids) {
        return entityMap(ids, projectMapper::selectBatchIds, Project::getId, Project::getProjectName);
    }

    private Map<Long, String> brandNames(List<Long> ids) {
        return entityMap(ids, brandMapper::selectBatchIds, Brand::getId, Brand::getBrandName);
    }

    private Map<Long, String> topicAngles(List<Long> ids) {
        return entityMap(ids, topicAngleMapper::selectBatchIds, MedicalTopicAngle::getId, MedicalTopicAngle::getTopicAngle);
    }

    private Map<Long, String> articleTitles(List<Long> ids) {
        return entityMap(ids, articleDraftMapper::selectBatchIds, com.huanjing.geo.module.content.entity.ArticleDraft::getId,
                com.huanjing.geo.module.content.entity.ArticleDraft::getTitle);
    }

    private List<Long> idsByProjectName(String projectName) {
        String keyword = trimToNull(projectName);
        if (keyword == null) {
            return List.of();
        }
        return projectMapper.selectList(new LambdaQueryWrapper<Project>()
                        .like(Project::getProjectName, keyword))
                .stream()
                .map(Project::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private List<Long> idsByBrandName(String brandName) {
        String keyword = trimToNull(brandName);
        if (keyword == null) {
            return List.of();
        }
        return brandMapper.selectList(new LambdaQueryWrapper<Brand>()
                        .like(Brand::getBrandName, keyword))
                .stream()
                .map(Brand::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private List<Long> idsByArticleTitle(String articleTitle) {
        String keyword = trimToNull(articleTitle);
        if (keyword == null) {
            return List.of();
        }
        return articleDraftMapper.selectList(new LambdaQueryWrapper<ArticleDraft>()
                        .like(ArticleDraft::getTitle, keyword))
                .stream()
                .map(ArticleDraft::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private List<Long> idsByTopicKeyword(String topicKeyword) {
        String keyword = trimToNull(topicKeyword);
        if (keyword == null) {
            return List.of();
        }
        return topicAngleMapper.selectList(new LambdaQueryWrapper<MedicalTopicAngle>()
                        .isNull(MedicalTopicAngle::getDeletedAt)
                        .and(wrapper -> wrapper
                                .like(MedicalTopicAngle::getTopicAngle, keyword)
                                .or()
                                .like(MedicalTopicAngle::getCategoryName, keyword)))
                .stream()
                .map(MedicalTopicAngle::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private <T> Page<T> emptyPage(long current, long size) {
        Page<T> page = new Page<>(current, size, 0);
        page.setRecords(List.of());
        return page;
    }

    private <T> Map<Long, String> entityMap(List<Long> ids,
                                           Function<List<Long>, List<T>> loader,
                                           Function<T, Long> idGetter,
                                           Function<T, String> labelGetter) {
        List<Long> safeIds = ids == null ? List.of() : ids.stream().filter(Objects::nonNull).distinct().toList();
        if (safeIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return loader.apply(safeIds).stream()
                .collect(Collectors.toMap(idGetter, labelGetter, (left, right) -> left));
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static final class TopicAngleCategoryAccumulator {
        private final String industryCode;
        private final String industryName;
        private final String categoryCode;
        private final String categoryName;
        private long topicAngleCount;

        private TopicAngleCategoryAccumulator(String industryCode,
                                              String industryName,
                                              String categoryCode,
                                              String categoryName) {
            this.industryCode = trimStatic(industryCode);
            this.industryName = trimStatic(industryName);
            this.categoryCode = trimStatic(categoryCode);
            this.categoryName = trimStatic(categoryName);
        }

        private void increment() {
            topicAngleCount++;
        }

        private TopicAngleCategoryVO toVO() {
            return new TopicAngleCategoryVO(industryCode, industryName, categoryCode, categoryName, topicAngleCount);
        }

        private static String trimStatic(String value) {
            return StringUtils.hasText(value) ? value.trim() : "";
        }
    }
}
