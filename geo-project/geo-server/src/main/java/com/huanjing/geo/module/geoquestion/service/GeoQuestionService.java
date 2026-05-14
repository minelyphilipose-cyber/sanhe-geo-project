package com.huanjing.geo.module.geoquestion.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.llm.LlmInvokeResult;
import com.huanjing.geo.common.llm.LlmInvoker;
import com.huanjing.geo.common.llm.LlmModelConfig;
import com.huanjing.geo.common.llm.LlmProperties;
import com.huanjing.geo.module.customer.dto.CompanyKeywordGroupQuotaVO;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.customer.service.CompanyService;
import com.huanjing.geo.module.geoquestion.dto.GeoQuestionDtos.*;
import com.huanjing.geo.module.geoquestion.entity.*;
import com.huanjing.geo.module.geoquestion.mapper.*;
import com.huanjing.geo.module.project.entity.KeywordGroup;
import com.huanjing.geo.module.project.entity.KeywordGroupResult;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.entity.ProjectCustomerRequirement;
import com.huanjing.geo.module.project.entity.ProjectKeywordGroupRel;
import com.huanjing.geo.module.project.mapper.KeywordGroupMapper;
import com.huanjing.geo.module.project.mapper.KeywordGroupResultMapper;
import com.huanjing.geo.module.project.mapper.ProjectCustomerRequirementMapper;
import com.huanjing.geo.module.project.mapper.ProjectKeywordGroupRelMapper;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import com.huanjing.geo.module.system.service.PlatformCredentialService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GeoQuestionService {
    private static final int BATCH_LIMIT = 50;
    private static final int QUESTION_GENERATION_TIMEOUT_MS = 180_000;
    private static final List<String> SCENES = List.of("brand", "decision", "deal", "compare", "qa", "function");
    private static final String SYSTEM_PROMPT = "prompts/geo-question/system-prompt.txt";
    private static final String ABC_TIER_PROMPT = "prompts/geo-question/abc-tier-definition.txt";
    private static final String USER_INPUT_TEMPLATE = "prompts/geo-question/user-input-template.txt";
    private static final TypeReference<List<Map<String, Object>>> QUESTION_MAP_LIST = new TypeReference<>() {};

    private final CompanyMapper companyMapper;
    private final BrandMapper brandMapper;
    private final CompanyService companyService;
    private final AiPlatformConfigMapper aiPlatformConfigMapper;
    private final PlatformCredentialService platformCredentialService;
    private final LlmInvoker llmInvoker;
    private final LlmProperties llmProperties;
    private final GeoQuestionWorkorderMapper workorderMapper;
    private final GeoQuestionProfileDraftMapper draftMapper;
    private final GeoQuestionBatchMapper batchMapper;
    private final GeoQuestionItemMapper itemMapper;
    private final GeoQuestionReplaceHistoryMapper replaceHistoryMapper;
    private final GeoQuestionVersionMapper versionMapper;
    private final GeoQuestionBatchLogMapper logMapper;
    private final KeywordGroupMapper keywordGroupMapper;
    private final KeywordGroupResultMapper keywordGroupResultMapper;
    private final ProjectKeywordGroupRelMapper projectKeywordGroupRelMapper;
    private final ProjectMapper projectMapper;
    private final ProjectCustomerRequirementMapper projectCustomerRequirementMapper;
    private final ObjectMapper objectMapper;
    @Autowired
    @Qualifier("presaleGenerateExecutor")
    private Executor generateExecutor;

    public List<CustomerSearchItem> searchCustomers(String keyword) {
        LambdaQueryWrapper<Company> wrapper = new LambdaQueryWrapper<Company>()
                .isNull(Company::getDeletedAt)
                .orderByDesc(Company::getUpdatedAt)
                .last("LIMIT 20");
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(Company::getCompanyName, kw).or().like(Company::getContactName, kw));
        }
        return companyMapper.selectList(wrapper).stream().map(company -> {
            Brand brand = brandMapper.selectOne(new LambdaQueryWrapper<Brand>()
                    .eq(Brand::getCompanyId, company.getId())
                    .isNull(Brand::getDeletedAt)
                    .last("LIMIT 1"));
            CompanyKeywordGroupQuotaVO quota = companyService.keywordGroupQuota(company.getId());
            CustomerSearchItem item = new CustomerSearchItem();
            item.setCompanyId(company.getId());
            item.setCompanyName(company.getCompanyName());
            item.setBrandId(brand == null ? null : brand.getId());
            item.setBrandName(brand == null ? null : brand.getBrandName());
            item.setIndustry(StringUtils.hasText(company.getIndustry()) ? company.getIndustry() : brand == null ? null : brand.getIndustry());
            item.setPackageName(quota.getPackageName());
            item.setActiveBinding(Boolean.TRUE.equals(quota.getActiveBinding()));
            return item;
        }).collect(Collectors.toList());
    }

    @Transactional
    public WorkorderVO createOrGet(Long companyId) {
        CompanyKeywordGroupQuotaVO quota = companyService.keywordGroupQuota(companyId);
        if (!Boolean.TRUE.equals(quota.getActiveBinding())) {
            throw new BizException(400, "客户未绑定套餐，不能进入分层拓词管理");
        }
        GeoQuestionWorkorder existing = workorderMapper.selectOne(new LambdaQueryWrapper<GeoQuestionWorkorder>()
                .eq(GeoQuestionWorkorder::getCompanyId, companyId)
                .isNull(GeoQuestionWorkorder::getProjectId)
                .eq(GeoQuestionWorkorder::getStatus, "draft")
                .last("LIMIT 1"));
        if (existing == null) {
            existing = new GeoQuestionWorkorder();
            existing.setCompanyId(companyId);
            existing.setPackageBindingId(quota.getPackageBindingId());
            existing.setPackageName(quota.getPackageName());
            existing.setStatus("draft");
            existing.setVersionLabel("v1.0");
            existing.setTargetA(n(quota.getQuotaLimitA()));
            existing.setTargetB(n(quota.getQuotaLimitB()));
            existing.setTargetC(n(quota.getQuotaLimitC()));
            existing.setVersionNo(0);
            existing.setCreatedAt(LocalDateTime.now());
            existing.setUpdatedAt(LocalDateTime.now());
            workorderMapper.insert(existing);
        }
        return toWorkorderVO(existing, quotaSnapshot(companyId, existing.getId()));
    }

    @Transactional
    public WorkorderVO createOrGetByProject(Long projectId) {
        Project project = requireProject(projectId);
        if (!"paused".equals(project.getStatus())) {
            throw new BizException(400, "只有未启动项目可以新增分层拓词组");
        }
        KeywordAllocation allocation = projectKeywordAllocation(project);
        if (allocation.total() <= 0) {
            throw new BizException(400, "当前项目未配置问题额度，不能进入分层拓词管理");
        }
        CompanyKeywordGroupQuotaVO quota = companyService.keywordGroupQuota(project.getCompanyId());
        GeoQuestionWorkorder existing = workorderMapper.selectOne(new LambdaQueryWrapper<GeoQuestionWorkorder>()
                .eq(GeoQuestionWorkorder::getProjectId, projectId)
                .eq(GeoQuestionWorkorder::getStatus, "draft")
                .last("LIMIT 1"));
        if (existing == null) {
            existing = new GeoQuestionWorkorder();
            existing.setCompanyId(project.getCompanyId());
            existing.setProjectId(projectId);
            existing.setPackageBindingId(quota.getPackageBindingId());
            existing.setPackageName(quota.getPackageName());
            existing.setStatus("draft");
            existing.setVersionLabel("v1.0");
            existing.setTargetA(allocation.a());
            existing.setTargetB(allocation.b());
            existing.setTargetC(allocation.c());
            existing.setVersionNo(0);
            existing.setCreatedAt(LocalDateTime.now());
            existing.setUpdatedAt(LocalDateTime.now());
            workorderMapper.insert(existing);
        }
        return toWorkorderVO(existing, quotaSnapshotByProject(projectId, existing.getId()));
    }

    public QuotaSnapshot quotaSnapshot(Long companyId, Long workorderId) {
        CompanyKeywordGroupQuotaVO base = companyService.keywordGroupQuota(companyId);
        QuotaSnapshot snapshot = new QuotaSnapshot();
        snapshot.setCompanyId(companyId);
        snapshot.setWorkorderId(workorderId);
        snapshot.setPackageName(base.getPackageName());
        snapshot.setQuotaA(n(base.getQuotaLimitA()));
        snapshot.setQuotaB(n(base.getQuotaLimitB()));
        snapshot.setQuotaC(n(base.getQuotaLimitC()));
        snapshot.setQuotaTotal(snapshot.getQuotaA() + snapshot.getQuotaB() + snapshot.getQuotaC());
        snapshot.setActiveUsedA(n(base.getUsedCountA()));
        snapshot.setActiveUsedB(n(base.getUsedCountB()));
        snapshot.setActiveUsedC(n(base.getUsedCountC()));
        snapshot.setActiveUsedTotal(snapshot.getActiveUsedA() + snapshot.getActiveUsedB() + snapshot.getActiveUsedC());
        Map<String, Integer> counts = itemCounts(workorderId);
        snapshot.setWorkorderCountA(counts.get("A"));
        snapshot.setWorkorderCountB(counts.get("B"));
        snapshot.setWorkorderCountC(counts.get("C"));
        snapshot.setWorkorderCountTotal(snapshot.getWorkorderCountA() + snapshot.getWorkorderCountB() + snapshot.getWorkorderCountC());
        GeoQuestionBatch running = runningBatch(workorderId);
        snapshot.setRunningReservedA(running == null ? 0 : n(running.getReservedA()));
        snapshot.setRunningReservedB(running == null ? 0 : n(running.getReservedB()));
        snapshot.setRunningReservedC(running == null ? 0 : n(running.getReservedC()));
        snapshot.setRunningReservedTotal(snapshot.getRunningReservedA() + snapshot.getRunningReservedB() + snapshot.getRunningReservedC());
        snapshot.setRemainingA(Math.max(snapshot.getQuotaA() - snapshot.getActiveUsedA() - snapshot.getWorkorderCountA() - snapshot.getRunningReservedA(), 0));
        snapshot.setRemainingB(Math.max(snapshot.getQuotaB() - snapshot.getActiveUsedB() - snapshot.getWorkorderCountB() - snapshot.getRunningReservedB(), 0));
        snapshot.setRemainingC(Math.max(snapshot.getQuotaC() - snapshot.getActiveUsedC() - snapshot.getWorkorderCountC() - snapshot.getRunningReservedC(), 0));
        snapshot.setRemainingTotal(snapshot.getRemainingA() + snapshot.getRemainingB() + snapshot.getRemainingC());
        return snapshot;
    }

    public QuotaSnapshot quotaSnapshotByProject(Long projectId, Long workorderId) {
        Project project = requireProject(projectId);
        KeywordAllocation allocation = projectKeywordAllocation(project);
        CompanyKeywordGroupQuotaVO base = companyService.keywordGroupQuota(project.getCompanyId());
        QuotaSnapshot snapshot = new QuotaSnapshot();
        snapshot.setCompanyId(project.getCompanyId());
        snapshot.setProjectId(projectId);
        snapshot.setWorkorderId(workorderId);
        snapshot.setPackageName(base.getPackageName());
        snapshot.setQuotaA(allocation.a());
        snapshot.setQuotaB(allocation.b());
        snapshot.setQuotaC(allocation.c());
        snapshot.setQuotaTotal(allocation.total());
        snapshot.setActiveUsedA(0);
        snapshot.setActiveUsedB(0);
        snapshot.setActiveUsedC(0);
        snapshot.setActiveUsedTotal(0);
        Map<String, Integer> counts = itemCounts(workorderId);
        snapshot.setWorkorderCountA(counts.get("A"));
        snapshot.setWorkorderCountB(counts.get("B"));
        snapshot.setWorkorderCountC(counts.get("C"));
        snapshot.setWorkorderCountTotal(snapshot.getWorkorderCountA() + snapshot.getWorkorderCountB() + snapshot.getWorkorderCountC());
        GeoQuestionBatch running = runningBatch(workorderId);
        snapshot.setRunningReservedA(running == null ? 0 : n(running.getReservedA()));
        snapshot.setRunningReservedB(running == null ? 0 : n(running.getReservedB()));
        snapshot.setRunningReservedC(running == null ? 0 : n(running.getReservedC()));
        snapshot.setRunningReservedTotal(snapshot.getRunningReservedA() + snapshot.getRunningReservedB() + snapshot.getRunningReservedC());
        snapshot.setRemainingA(Math.max(snapshot.getQuotaA() - snapshot.getWorkorderCountA() - snapshot.getRunningReservedA(), 0));
        snapshot.setRemainingB(Math.max(snapshot.getQuotaB() - snapshot.getWorkorderCountB() - snapshot.getRunningReservedB(), 0));
        snapshot.setRemainingC(Math.max(snapshot.getQuotaC() - snapshot.getWorkorderCountC() - snapshot.getRunningReservedC(), 0));
        snapshot.setRemainingTotal(snapshot.getRemainingA() + snapshot.getRemainingB() + snapshot.getRemainingC());
        return snapshot;
    }

    private QuotaSnapshot quotaSnapshot(GeoQuestionWorkorder workorder) {
        if (workorder.getProjectId() != null) {
            return quotaSnapshotByProject(workorder.getProjectId(), workorder.getId());
        }
        return quotaSnapshot(workorder.getCompanyId(), workorder.getId());
    }

    public List<WorkorderListItemVO> workorders(Long companyId) {
        return workorderMapper.selectList(new LambdaQueryWrapper<GeoQuestionWorkorder>()
                        .eq(GeoQuestionWorkorder::getCompanyId, companyId)
                        .isNull(GeoQuestionWorkorder::getProjectId)
                        .orderByDesc(GeoQuestionWorkorder::getUpdatedAt))
                .stream()
                .map(this::toWorkorderListItemVO)
                .filter(item -> n(item.getBatchCount()) > 0 || n(item.getCountTotal()) > 0)
                .collect(Collectors.toList());
    }

    public List<WorkorderListItemVO> workordersByProject(Long projectId) {
        requireProject(projectId);
        return workorderMapper.selectList(new LambdaQueryWrapper<GeoQuestionWorkorder>()
                        .eq(GeoQuestionWorkorder::getProjectId, projectId)
                        .orderByDesc(GeoQuestionWorkorder::getUpdatedAt))
                .stream()
                .map(this::toWorkorderListItemVO)
                .filter(item -> n(item.getBatchCount()) > 0 || n(item.getCountTotal()) > 0)
                .collect(Collectors.toList());
    }

    public ProfileVO profile(Long companyId) {
        Company company = companyMapper.selectById(companyId);
        if (company == null || company.getDeletedAt() != null) {
            throw new BizException(404, "Company not found");
        }
        Brand brand = brandMapper.selectOne(new LambdaQueryWrapper<Brand>()
                .eq(Brand::getCompanyId, companyId)
                .isNull(Brand::getDeletedAt)
                .last("LIMIT 1"));
        ProfileVO vo = new ProfileVO();
        vo.setCompanyId(companyId);
        vo.setCompanyName(company.getCompanyName());
        vo.setBrandName(brand == null ? "" : brand.getBrandName());
        vo.setBrandRelation("自营");
        vo.setCoreBusiness(splitTags(StringUtils.hasText(company.getIndustryTags()) ? company.getIndustryTags() : company.getBusinessDirection()));
        vo.setTargetRegion(buildRegion(company));
        vo.setIndustry(StringUtils.hasText(company.getIndustry()) ? company.getIndustry() : brand == null ? "" : brand.getIndustry());
        vo.setTargetCustomer(defaultText(company.getBusinessDirection(), "请补充目标客户画像，至少 20 字。"));
        vo.setCoreAdvantage(defaultText(brand == null ? null : brand.getDescription(), company.getRemark()));
        vo.setBenchmarkSpecs("");
        vo.setCompetitors(defaultCompetitors(company.getCompetitors()));
        vo.setCoreNeeds(new ArrayList<>());
        return vo;
    }

    public ProfileVO profileByProject(Long projectId) {
        Project project = requireProject(projectId);
        Company company = companyMapper.selectById(project.getCompanyId());
        if (company == null || company.getDeletedAt() != null) {
            throw new BizException(404, "Company not found");
        }
        Brand brand = project.getBrandId() == null ? null : brandMapper.selectById(project.getBrandId());
        if (brand == null || brand.getDeletedAt() != null) {
            brand = brandMapper.selectOne(new LambdaQueryWrapper<Brand>()
                    .eq(Brand::getCompanyId, project.getCompanyId())
                    .isNull(Brand::getDeletedAt)
                    .last("LIMIT 1"));
        }
        ProfileVO vo = profile(project.getCompanyId());
        vo.setProjectId(projectId);
        vo.setProjectName(project.getProjectName());
        vo.setBrandName(defaultText(project.getBrandName(), brand == null ? vo.getBrandName() : brand.getBrandName()));
        vo.setTargetRegion(projectRegion(project, vo.getTargetRegion()));
        vo.setTargetCustomer(defaultText(project.getTargetAudience(), vo.getTargetCustomer()));
        vo.setBenchmarkSpecs(defaultText(project.getCustomStatement(), vo.getBenchmarkSpecs()));
        vo.setCoreNeeds(projectCoreNeeds(projectId));
        return vo;
    }

    @Transactional
    public DraftVO saveDraft(DraftSaveRequest req) {
        if (req.getWorkorderId() == null) {
            throw new BizException(400, "workorderId is required");
        }
        GeoQuestionProfileDraft draft = draftMapper.selectOne(new LambdaQueryWrapper<GeoQuestionProfileDraft>()
                .eq(GeoQuestionProfileDraft::getWorkorderId, req.getWorkorderId())
                .last("LIMIT 1"));
        if (draft == null) {
            draft = new GeoQuestionProfileDraft();
            draft.setWorkorderId(req.getWorkorderId());
            draft.setCreatedAt(LocalDateTime.now());
        }
        draft.setProfileJson(req.getProfileJson());
        draft.setSyncToCustomerProfile(Boolean.TRUE.equals(req.getSyncToCustomerProfile()));
        draft.setValidationStatus(defaultText(req.getValidationStatus(), "draft"));
        draft.setAutoSavedAt(LocalDateTime.now());
        draft.setUpdatedAt(LocalDateTime.now());
        if (draft.getId() == null) {
            draftMapper.insert(draft);
        } else {
            draftMapper.updateById(draft);
        }
        return toDraftVO(draft);
    }

    public DraftVO getDraft(Long workorderId) {
        GeoQuestionProfileDraft draft = draftMapper.selectOne(new LambdaQueryWrapper<GeoQuestionProfileDraft>()
                .eq(GeoQuestionProfileDraft::getWorkorderId, workorderId)
                .last("LIMIT 1"));
        return draft == null ? null : toDraftVO(draft);
    }

    public List<ProviderVO> providers() {
        return aiPlatformConfigMapper.selectList(new LambdaQueryWrapper<AiPlatformConfig>()
                .eq(AiPlatformConfig::getEnabled, true)
                .isNotNull(AiPlatformConfig::getLowModelId)
                .apply("TRIM(low_model_id) <> ''")
                .orderByAsc(AiPlatformConfig::getPlatformCode)).stream().map(cfg -> {
            ProviderVO vo = new ProviderVO();
            vo.setId(cfg.getId());
            vo.setPlatformCode(cfg.getPlatformCode());
            vo.setPlatformName(cfg.getPlatformName());
            String modelId = generationModelId(cfg);
            vo.setModelId(modelId);
            vo.setModelName(generationModelName(cfg, modelId));
            return vo;
        }).collect(Collectors.toList());
    }

    @Transactional
    public BatchVO startBatch(BatchStartRequest req) {
        int a = n(req.getBatchA());
        int b = n(req.getBatchB());
        int c = n(req.getBatchC());
        int total = a + b + c;
        if (total < 1) throw new BizException(400, "本批合计必须至少 1 条");
        if (total > BATCH_LIMIT) throw new BizException(400, "单批合计不得超过 50");
        validateSceneWeights(req.getSceneWeights(), total);
        GeoQuestionWorkorder workorder = workorderMapper.selectById(req.getWorkorderId());
        if (workorder == null || !"draft".equals(workorder.getStatus())) {
            throw new BizException(404, "进行中的问题池工单不存在");
        }
        workorderMapper.lockById(workorder.getId());
        releaseStaleRunningFlags(workorder.getId());
        QuotaSnapshot snapshot = quotaSnapshot(workorder);
        if (a > snapshot.getRemainingA()) throw new BizException(400, "A 类剩余仅 " + snapshot.getRemainingA());
        if (b > snapshot.getRemainingB()) throw new BizException(400, "B 类剩余仅 " + snapshot.getRemainingB());
        if (c > snapshot.getRemainingC()) throw new BizException(400, "C 类剩余仅 " + snapshot.getRemainingC());
        if (runningBatch(workorder.getId()) != null) {
            throw new BizException(400, "当前工单已有运行中批次，请等待完成后继续生成");
        }
        AiPlatformConfig modelConfig = resolveRequestedModel(req);
        String modelId = generationModelId(modelConfig);

        GeoQuestionBatch batch = new GeoQuestionBatch();
        batch.setWorkorderId(workorder.getId());
        batch.setBatchNo("BAT-" + System.currentTimeMillis());
        batch.setRequestA(a);
        batch.setRequestB(b);
        batch.setRequestC(c);
        batch.setActualA(0);
        batch.setActualB(0);
        batch.setActualC(0);
        batch.setReservedA(a);
        batch.setReservedB(b);
        batch.setReservedC(c);
        batch.setActiveRunningFlag(1);
        batch.setModelProvider(modelConfig.getPlatformCode());
        batch.setModelId(modelId);
        batch.setModelName(generationModelName(modelConfig, modelId));
        batch.setSceneWeightsJson(writeJson(req.getSceneWeights()));
        batch.setTemperature(req.getTemperature() == null ? new BigDecimal("0.70") : req.getTemperature());
        batch.setParamSnapshot(writeJson(req));
        batch.setPromptSnapshot(buildPromptSnapshot(workorder, req));
        batch.setStatus("pending");
        batch.setProgressJson(progress("pending", null, null, 0, total, 0, "批次已创建，等待生成"));
        batch.setPartialFlag(false);
        batch.setCancelRequested(false);
        batch.setCreatedAt(LocalDateTime.now());
        batch.setUpdatedAt(LocalDateTime.now());
        try {
            batchMapper.insert(batch);
        } catch (DuplicateKeyException ex) {
            throw new BizException(400, "当前工单已有运行中批次，请等待完成后继续生成");
        }
        log(batch.getId(), "batch_created", "批次创建 " + batch.getBatchNo());
        log(batch.getId(), "quota_reserved", "预占额度 A=" + a + " B=" + b + " C=" + c);
        scheduleBatchAfterCommit(batch.getId());
        return batch(batch.getId());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void resumePendingBatches() {
        batchMapper.selectList(new LambdaQueryWrapper<GeoQuestionBatch>()
                .eq(GeoQuestionBatch::getStatus, "pending")
                .eq(GeoQuestionBatch::getActiveRunningFlag, 1)
                .orderByAsc(GeoQuestionBatch::getCreatedAt))
                .forEach(batch -> {
                    log(batch.getId(), "batch_resumed", "服务启动后恢复未执行批次 " + batch.getBatchNo());
                    submitBatch(batch.getId());
                });
    }

    public void runBatch(Long batchId) {
        GeoQuestionBatch batch = batchMapper.selectById(batchId);
        if (batch == null) return;
        try {
            markBatchRunning(batch);
            int total = n(batch.getRequestA()) + n(batch.getRequestB()) + n(batch.getRequestC());
            List<GeneratedQuestionSpec> specs = invokeQuestionGeneration(batch, total);
            persistGeneratedQuestions(batch, specs, total);
            GeoQuestionBatch latest = batchMapper.selectById(batch.getId());
            if (latest != null && "cancelled".equals(latest.getStatus())) {
                return;
            }
            finishBatch(batch.getId(), "success", false, null);
        } catch (Exception ex) {
            finishBatch(batch.getId(), "failed", true, ex.getMessage());
        }
    }

    private void scheduleBatchAfterCommit(Long batchId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            submitBatch(batchId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                submitBatch(batchId);
            }
        });
    }

    private void submitBatch(Long batchId) {
        try {
            generateExecutor.execute(() -> runBatch(batchId));
        } catch (RuntimeException ex) {
            finishBatch(batchId, "failed", true, "异步执行器提交失败: " + ex.getMessage());
        }
    }

    public BatchVO batch(Long id) {
        GeoQuestionBatch batch = batchMapper.selectById(id);
        if (batch == null) throw new BizException(404, "Batch not found");
        return toBatchVO(batch);
    }

    @Transactional
    public void cancelBatch(Long id) {
        GeoQuestionBatch batch = batchMapper.selectById(id);
        if (batch == null) throw new BizException(404, "Batch not found");
        batch.setCancelRequested(true);
        batch.setUpdatedAt(LocalDateTime.now());
        batchMapper.updateById(batch);
        log(id, "cancelled_requested", "用户请求中断批次");
    }

    @Transactional
    public void deleteBatch(Long id) {
        GeoQuestionBatch batch = batchMapper.selectById(id);
        if (batch == null) throw new BizException(404, "Batch not found");
        batch.setStatus("deleted");
        batch.setActiveRunningFlag(null);
        batch.setReservedA(0);
        batch.setReservedB(0);
        batch.setReservedC(0);
        batch.setUpdatedAt(LocalDateTime.now());
        batchMapper.update(null, new LambdaUpdateWrapper<GeoQuestionBatch>()
                .eq(GeoQuestionBatch::getId, id)
                .set(GeoQuestionBatch::getStatus, "deleted")
                .set(GeoQuestionBatch::getActiveRunningFlag, null)
                .set(GeoQuestionBatch::getReservedA, 0)
                .set(GeoQuestionBatch::getReservedB, 0)
                .set(GeoQuestionBatch::getReservedC, 0)
                .set(GeoQuestionBatch::getUpdatedAt, LocalDateTime.now()));
        itemMapper.update(null, new LambdaUpdateWrapper<GeoQuestionItem>()
                .eq(GeoQuestionItem::getBatchId, id)
                .ne(GeoQuestionItem::getStatus, "deleted")
                .set(GeoQuestionItem::getStatus, "deleted")
                .set(GeoQuestionItem::getDeletedAt, LocalDateTime.now()));
        log(id, "batch_deleted", "批次软删除，按未删除题目数口径释放额度");
    }

    @Transactional
    public void deleteQuestion(Long id) {
        GeoQuestionItem item = itemMapper.selectById(id);
        if (item == null) throw new BizException(404, "Question not found");
        item.setStatus("deleted");
        item.setDeletedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        itemMapper.updateById(item);
    }

    @Transactional
    public RegenerateQuestionVO regenerateQuestion(Long id, RegenerateQuestionRequest req) {
        GeoQuestionItem item = itemMapper.selectById(id);
        if (item == null || "deleted".equals(item.getStatus())) throw new BizException(404, "Question not found");
        String oldText = item.getQuestionText();
        GeneratedQuestionSpec replacement = invokeQuestionReplacement(item);
        item.setQuestionText(replacement.questionText());
        item.setSceneCode(replacement.sceneCode());
        item.setPriority(replacement.priority());
        item.setMonitorFrequency(replacement.monitorFrequency());
        item.setScoreRelevance(replacement.scoreRelevance());
        item.setScoreIntent(replacement.scoreIntent());
        item.setScoreCompetition(replacement.scoreCompetition());
        item.setScoreConversion(replacement.scoreConversion());
        item.setScoreCoverage(replacement.scoreCoverage());
        item.setTotalScore(replacement.totalScore());
        item.setRelatedNeedText(replacement.relatedNeedText());
        item.setDesignReason(replacement.designReason());
        item.setReplaceCount(n(item.getReplaceCount()) + 1);
        item.setUpdatedAt(LocalDateTime.now());
        itemMapper.updateById(item);
        GeoQuestionReplaceHistory history = new GeoQuestionReplaceHistory();
        history.setQuestionId(id);
        history.setOldQuestionText(oldText);
        history.setNewQuestionText(replacement.questionText());
        history.setReason(req == null ? null : req.getReason());
        history.setCreatedAt(LocalDateTime.now());
        replaceHistoryMapper.insert(history);

        RegenerateQuestionVO vo = new RegenerateQuestionVO();
        vo.setQuestion(toQuestionVO(item));
        vo.setSoftWarning(item.getReplaceCount() > 3);
        vo.setWarningMessage(item.getReplaceCount() > 3 ? "该题已替换超过 3 次，建议人工确认方向" : null);
        return vo;
    }

    @Transactional
    public QuestionVO updateQuestion(Long id, QuestionUpdateRequest req) {
        GeoQuestionItem item = itemMapper.selectById(id);
        if (item == null || "deleted".equals(item.getStatus())) throw new BizException(404, "Question not found");
        if (req == null || !StringUtils.hasText(req.getQuestionText())) throw new BizException(400, "问题文本不能为空");
        item.setQuestionText(req.getQuestionText().trim());
        item.setSceneCode(defaultText(req.getSceneCode(), item.getSceneCode()));
        item.setPriority(defaultText(req.getPriority(), item.getPriority()));
        item.setMonitorFrequency(defaultText(req.getMonitorFrequency(), item.getMonitorFrequency()));
        item.setScoreRelevance(defaultBigDecimal(req.getScoreRelevance(), item.getScoreRelevance()));
        item.setScoreIntent(defaultBigDecimal(req.getScoreIntent(), item.getScoreIntent()));
        item.setScoreCompetition(defaultBigDecimal(req.getScoreCompetition(), item.getScoreCompetition()));
        item.setScoreConversion(defaultBigDecimal(req.getScoreConversion(), item.getScoreConversion()));
        item.setScoreCoverage(defaultBigDecimal(req.getScoreCoverage(), item.getScoreCoverage()));
        item.setTotalScore(defaultBigDecimal(req.getTotalScore(), item.getTotalScore()));
        item.setRelatedNeedText(defaultText(req.getRelatedNeedText(), item.getRelatedNeedText()));
        item.setDesignReason(defaultText(req.getDesignReason(), item.getDesignReason()));
        item.setUpdatedAt(LocalDateTime.now());
        itemMapper.updateById(item);
        return toQuestionVO(item);
    }

    public ReviewVO review(Long workorderId) {
        GeoQuestionWorkorder workorder = workorderMapper.selectById(workorderId);
        if (workorder == null) throw new BizException(404, "Workorder not found");
        ReviewVO vo = new ReviewVO();
        vo.setWorkorder(toWorkorderVO(workorder, quotaSnapshot(workorder)));
        List<BatchVO> batches = batchMapper.selectList(new LambdaQueryWrapper<GeoQuestionBatch>()
                .eq(GeoQuestionBatch::getWorkorderId, workorderId)
                .ne(GeoQuestionBatch::getStatus, "deleted")
                .orderByDesc(GeoQuestionBatch::getCreatedAt)).stream().map(this::toBatchVO).collect(Collectors.toList());
        batches.forEach(batch -> batch.setReplaceCountTotal(sumBatchReplaceCount(batch.getId())));
        vo.setBatches(batches);
        vo.setQuestions(allQuestions(workorderId).stream().map(this::toQuestionVO).collect(Collectors.toList()));
        return vo;
    }

    public QuestionPageVO questionPage(Long workorderId, String tier, long current, long size) {
        GeoQuestionWorkorder workorder = workorderMapper.selectById(workorderId);
        if (workorder == null) throw new BizException(404, "Workorder not found");
        long safeCurrent = Math.max(1L, current);
        long safeSize = Math.max(1L, Math.min(size <= 0 ? 20L : size, 100L));
        LambdaQueryWrapper<GeoQuestionItem> wrapper = new LambdaQueryWrapper<GeoQuestionItem>()
                .eq(GeoQuestionItem::getWorkorderId, workorderId)
                .ne(GeoQuestionItem::getStatus, "deleted")
                .orderByAsc(GeoQuestionItem::getTier)
                .orderByAsc(GeoQuestionItem::getSortOrder)
                .orderByAsc(GeoQuestionItem::getId);
        if (StringUtils.hasText(tier) && !"all".equalsIgnoreCase(tier)) {
            wrapper.eq(GeoQuestionItem::getTier, tier.trim().toUpperCase(Locale.ROOT));
        }
        Page<GeoQuestionItem> page = itemMapper.selectPage(new Page<>(safeCurrent, safeSize), wrapper);
        QuestionPageVO vo = new QuestionPageVO();
        vo.setRecords(page.getRecords().stream().map(this::toQuestionVO).collect(Collectors.toList()));
        vo.setTotal(page.getTotal());
        vo.setCurrent(page.getCurrent());
        vo.setSize(page.getSize());
        vo.setPages(page.getPages());
        return vo;
    }

    @Transactional
    public ReviewVO createManualQuestions(Long workorderId, ManualQuestionCreateRequest req) {
        GeoQuestionWorkorder workorder = workorderMapper.selectById(workorderId);
        if (workorder == null || !List.of("draft", "paused").contains(workorder.getStatus())) {
            throw new BizException(404, "可录入的问题池工单不存在");
        }
        if (req == null || req.getItems() == null || req.getItems().isEmpty()) {
            throw new BizException(400, "请至少录入 1 条问题");
        }
        if (req.getItems().size() > 100) {
            throw new BizException(400, "单次手动录入最多 100 条");
        }

        workorderMapper.lockById(workorder.getId());
        releaseStaleRunningFlags(workorder.getId());
        if (runningBatch(workorder.getId()) != null) {
            throw new BizException(400, "当前工单已有运行中批次，请等待完成后再手动录入");
        }

        List<ManualQuestionItemRequest> items = normalizeManualItems(req.getItems());
        Map<String, Integer> tierCounts = countManualTiers(items);
        QuotaSnapshot snapshot = quotaSnapshot(workorder);
        if (tierCounts.get("A") > snapshot.getRemainingA()) throw new BizException(400, "A 类剩余仅 " + snapshot.getRemainingA());
        if (tierCounts.get("B") > snapshot.getRemainingB()) throw new BizException(400, "B 类剩余仅 " + snapshot.getRemainingB());
        if (tierCounts.get("C") > snapshot.getRemainingC()) throw new BizException(400, "C 类剩余仅 " + snapshot.getRemainingC());
        validateManualQuestionDuplicates(workorder.getId(), items);

        GeoQuestionBatch batch = new GeoQuestionBatch();
        batch.setWorkorderId(workorder.getId());
        batch.setBatchNo("MAN-" + System.currentTimeMillis());
        batch.setRequestA(tierCounts.get("A"));
        batch.setRequestB(tierCounts.get("B"));
        batch.setRequestC(tierCounts.get("C"));
        batch.setActualA(tierCounts.get("A"));
        batch.setActualB(tierCounts.get("B"));
        batch.setActualC(tierCounts.get("C"));
        batch.setReservedA(0);
        batch.setReservedB(0);
        batch.setReservedC(0);
        batch.setActiveRunningFlag(null);
        batch.setModelProvider("manual");
        batch.setModelId("manual");
        batch.setModelName("手动录入");
        batch.setSceneWeightsJson(writeJson(countManualScenes(items)));
        batch.setTemperature(new BigDecimal("0.00"));
        batch.setParamSnapshot(writeJson(req));
        batch.setPromptSnapshot(defaultText(req.getManualReason(), "手动录入问题"));
        batch.setStatus("completed");
        batch.setProgressJson(progress("completed", null, null, items.size(), items.size(), 0, "手动录入完成"));
        batch.setPartialFlag(false);
        batch.setCancelRequested(false);
        batch.setStartedAt(LocalDateTime.now());
        batch.setFinishedAt(LocalDateTime.now());
        batch.setCreatedAt(LocalDateTime.now());
        batch.setUpdatedAt(LocalDateTime.now());
        batchMapper.insert(batch);

        int sort = maxQuestionSortOrder(workorder.getId()) + 1;
        for (ManualQuestionItemRequest itemReq : items) {
            GeoQuestionItem item = new GeoQuestionItem();
            item.setWorkorderId(workorder.getId());
            item.setBatchId(batch.getId());
            item.setTier(itemReq.getTier());
            item.setSceneCode(itemReq.getSceneCode());
            item.setQuestionText(itemReq.getQuestionText());
            item.setPriority(defaultText(itemReq.getPriority(), "medium"));
            item.setMonitorFrequency(defaultText(itemReq.getMonitorFrequency(), "weekly"));
            item.setScoreRelevance(itemReq.getScoreRelevance());
            item.setScoreIntent(itemReq.getScoreIntent());
            item.setScoreCompetition(itemReq.getScoreCompetition());
            item.setScoreConversion(itemReq.getScoreConversion());
            item.setScoreCoverage(itemReq.getScoreCoverage());
            item.setTotalScore(defaultBigDecimal(itemReq.getTotalScore(), manualTotalScore(itemReq)));
            item.setRelatedNeedText(defaultText(itemReq.getRelatedNeedText(), ""));
            item.setDesignReason(defaultText(itemReq.getDesignReason(), defaultText(req.getManualReason(), "手动录入")));
            item.setStatus("pending_review");
            item.setReplaceCount(0);
            item.setSortOrder(sort++);
            item.setCreatedAt(LocalDateTime.now());
            item.setUpdatedAt(LocalDateTime.now());
            itemMapper.insert(item);
        }
        workorder.setUpdatedAt(LocalDateTime.now());
        workorderMapper.updateById(workorder);
        log(batch.getId(), "manual_questions_created", "手动录入问题 " + items.size() + " 条");
        return review(workorder.getId());
    }

    private List<GeoQuestionItem> allQuestions(Long workorderId) {
        return itemMapper.selectList(new LambdaQueryWrapper<GeoQuestionItem>()
                .eq(GeoQuestionItem::getWorkorderId, workorderId)
                .ne(GeoQuestionItem::getStatus, "deleted")
                .orderByAsc(GeoQuestionItem::getTier)
                .orderByAsc(GeoQuestionItem::getSortOrder)
                .orderByAsc(GeoQuestionItem::getId));
    }

    private void validateNoDuplicateQuestions(List<GeoQuestionItem> questions) {
        Set<String> seen = new HashSet<>();
        List<String> duplicates = new ArrayList<>();
        for (GeoQuestionItem item : questions) {
            String key = dedupeQuestionText(item.getQuestionText());
            if (!seen.add(key)) {
                duplicates.add(item.getQuestionText());
            }
        }
        if (!duplicates.isEmpty()) {
            String duplicateText = duplicates.stream().distinct().limit(10).collect(Collectors.joining("；"));
            throw new BizException(400, "问题池存在重复问题，请替换后再入库：" + duplicateText);
        }
    }

    private int sumBatchReplaceCount(Long batchId) {
        if (batchId == null) {
            return 0;
        }
        return itemMapper.selectList(new LambdaQueryWrapper<GeoQuestionItem>()
                        .eq(GeoQuestionItem::getBatchId, batchId)
                        .ne(GeoQuestionItem::getStatus, "deleted"))
                .stream()
                .mapToInt(item -> n(item.getReplaceCount()))
                .sum();
    }

    public byte[] exportCsv(Long workorderId) {
        List<QuestionVO> questions = allQuestions(workorderId).stream().map(this::toQuestionVO).collect(Collectors.toList());
        StringBuilder sb = new StringBuilder("\uFEFF");
        sb.append("问题ID,问题文本,场景,分级,优先级,监测频率,相关性,意图强度,竞争度,转化价值,覆盖价值,总分,对应需求,设计理由,所属批次,状态\n");
        for (QuestionVO q : questions) {
            sb.append(csv(q.getTier() + "-" + q.getId())).append(',')
                    .append(csv(q.getQuestionText())).append(',')
                    .append(csv(q.getSceneCode())).append(',')
                    .append(csv(q.getTier())).append(',')
                    .append(csv(q.getPriority())).append(',')
                    .append(csv(q.getMonitorFrequency())).append(',')
                    .append(csv(q.getScoreRelevance())).append(',')
                    .append(csv(q.getScoreIntent())).append(',')
                    .append(csv(q.getScoreCompetition())).append(',')
                    .append(csv(q.getScoreConversion())).append(',')
                    .append(csv(q.getScoreCoverage())).append(',')
                    .append(csv(q.getTotalScore())).append(',')
                    .append(csv(q.getRelatedNeedText())).append(',')
                    .append(csv(q.getDesignReason())).append(',')
                    .append(csv(q.getBatchId())).append(',')
                    .append(csv(q.getStatus())).append('\n');
        }
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    @Transactional
    public GeoQuestionVersion commit(Long workorderId, CommitRequest req) {
        GeoQuestionWorkorder workorder = workorderMapper.selectById(workorderId);
        if (workorder == null) throw new BizException(404, "Workorder not found");
        if (!List.of("draft", "paused").contains(workorder.getStatus())) {
            throw new BizException(400, "当前工单已入库，不能重复提交");
        }
        QuotaSnapshot snapshot = quotaSnapshot(workorder);
        if (!Objects.equals(snapshot.getWorkorderCountA(), snapshot.getQuotaA() - snapshot.getActiveUsedA())
                || !Objects.equals(snapshot.getWorkorderCountB(), snapshot.getQuotaB() - snapshot.getActiveUsedB())
                || !Objects.equals(snapshot.getWorkorderCountC(), snapshot.getQuotaC() - snapshot.getActiveUsedC())) {
            throw new BizException(400, "三级配额未满，无法入库");
        }
        List<GeoQuestionItem> questions = allQuestions(workorderId);
        validateNoDuplicateQuestions(questions);
        GeoQuestionVersion version = new GeoQuestionVersion();
        version.setWorkorderId(workorderId);
        version.setCompanyId(workorder.getCompanyId());
        version.setProjectId(workorder.getProjectId());
        version.setVersionLabel(defaultText(req == null ? null : req.getVersionLabel(), "v1.0"));
        version.setStatus("active");
        version.setCountA(snapshot.getWorkorderCountA());
        version.setCountB(snapshot.getWorkorderCountB());
        version.setCountC(snapshot.getWorkorderCountC());
        version.setIsPartial(false);
        version.setCommitMode("strict");
        ReviewVO reviewSnapshot = review(workorderId);
        reviewSnapshot.setQuestions(questions.stream().map(this::toQuestionVO).collect(Collectors.toList()));
        version.setSnapshotJson(writeJson(reviewSnapshot));
        version.setCommittedAt(LocalDateTime.now());
        version.setCreatedAt(LocalDateTime.now());
        version.setUpdatedAt(LocalDateTime.now());
        versionMapper.insert(version);

        KeywordGroup group = new KeywordGroup();
        group.setCompanyId(workorder.getCompanyId());
        group.setProjectId(workorder.getProjectId());
        group.setName("问题池工单-" + workorderId + "-" + version.getVersionLabel());
        group.setType("imported");
        group.setAreaEnabled(false);
        group.setRemark("由分层拓词管理入库生成");
        group.setDeleted(false);
        group.setCreatedAt(LocalDateTime.now());
        group.setUpdatedAt(LocalDateTime.now());
        keywordGroupMapper.insert(group);

        bindKeywordGroupToProject(workorder.getProjectId(), group.getId());

        int sort = 1;
        for (GeoQuestionItem q : questions) {
            KeywordGroupResult result = new KeywordGroupResult();
            result.setGroupId(group.getId());
            result.setKeywordText(q.getQuestionText());
            result.setSourceType("geo_question_pool");
            result.setSeedText(q.getRelatedNeedText());
            result.setQuestionTier(q.getTier());
            result.setSourceWorkorderId(workorderId);
            result.setSourceBatchId(q.getBatchId());
            result.setSourceQuestionId(q.getId());
            result.setSourceVersionId(version.getId());
            result.setSceneCode(q.getSceneCode());
            result.setPriority(q.getPriority());
            result.setMonitorFrequency(q.getMonitorFrequency());
            result.setTotalScore(q.getTotalScore());
            result.setRelatedNeed(q.getRelatedNeedText());
            result.setDesignReason(q.getDesignReason());
            result.setSortOrder(sort++);
            result.setCreatedAt(LocalDateTime.now());
            result.setUpdatedAt(LocalDateTime.now());
            keywordGroupResultMapper.insert(result);
        }
        version.setLegacyKeywordGroupId(group.getId());
        versionMapper.updateById(version);
        workorder.setStatus("committed");
        workorder.setCommittedVersionId(version.getId());
        workorder.setLegacyKeywordGroupId(group.getId());
        workorder.setUpdatedAt(LocalDateTime.now());
        workorderMapper.updateById(workorder);
        return version;
    }

    private void bindKeywordGroupToProject(Long projectId, Long groupId) {
        if (projectId == null || groupId == null) {
            return;
        }
        Long count = projectKeywordGroupRelMapper.selectCount(
                new LambdaQueryWrapper<ProjectKeywordGroupRel>()
                        .eq(ProjectKeywordGroupRel::getProjectId, projectId)
                        .eq(ProjectKeywordGroupRel::getKeywordGroupId, groupId)
        );
        if (count != null && count > 0) {
            return;
        }
        ProjectKeywordGroupRel rel = new ProjectKeywordGroupRel();
        rel.setProjectId(projectId);
        rel.setKeywordGroupId(groupId);
        rel.setCreatedAt(LocalDateTime.now());
        projectKeywordGroupRelMapper.insert(rel);
    }

    private GeneratedQuestionSpec invokeQuestionReplacement(GeoQuestionItem item) {
        GeoQuestionBatch batch = batchMapper.selectById(item.getBatchId());
        if (batch == null || "deleted".equals(batch.getStatus())) {
            throw new BizException(404, "Batch not found");
        }
        AiPlatformConfig config = resolveBatchModel(batch);
        String modelId = generationModelId(config);
        String apiKey = platformCredentialService.resolveApiKey(config.getPlatformCode(), config.getPrimaryKeyRef(), config.getApiKey());
        if (!StringUtils.hasText(apiKey)) {
            throw new BizException(400, "当前模型未配置 API Key，无法重生成真实问题");
        }
        String prompt = """
                请基于以下问题池上下文，重生成 1 条同层级、同用途的新问题。
                输出必须是 JSON 数组，且只能包含 1 个对象。字段：
                questionText, tier, sceneCode, priority, monitorFrequency,
                scoreRelevance, scoreIntent, scoreCompetition, scoreConversion, scoreCoverage, totalScore,
                relatedNeedText, designReason。

                约束：
                1. tier 固定为 %s。
                2. sceneCode 优先保持 %s，若确需调整只能使用 brand、decision、deal、compare、qa、function。
                3. questionText 必须是新的具体中文问题，不能复用原问题句式，不能输出模板句。
                4. 该操作是原地替换，不新增题目，不改变工单累计数量。

                原问题：%s
                原设计理由：%s

                工单上下文：
                %s
                """.formatted(
                item.getTier(),
                item.getSceneCode(),
                defaultText(item.getQuestionText(), ""),
                defaultText(item.getDesignReason(), ""),
                defaultText(batch.getPromptSnapshot(), "")
        );
        try {
            LlmInvokeResult result = llmInvoker.invoke(prompt, new LlmModelConfig(
                    config.getPlatformCode(),
                    config.getPlatformName(),
                    modelId,
                    generationModelName(config, modelId),
                    config.getApiUrl(),
                    apiKey,
                    "你是 GEO 服务的分层问题池生成助手，必须只输出 JSON。",
                    batch.getTemperature() == null ? 0.7D : batch.getTemperature().doubleValue(),
                    llmProperties.getConnectTimeoutMs(),
                    config.getTimeoutMs() == null ? llmProperties.getRequestTimeoutMs() : config.getTimeoutMs(),
                    Math.max(0, n(config.getMaxRetry())),
                    Math.max(1, n(config.getRateLimitQps()) == 0 ? 1 : n(config.getRateLimitQps())),
                    null,
                    true,
                    LlmModelConfig.LONG_FORM_MAX_REQUEST_TIMEOUT_MS
            ));
            List<GeneratedQuestionSpec> specs = parseGeneratedQuestions(result.responseText(), batch);
            if (specs.isEmpty()) {
                throw new BizException(500, "模型未返回可用问题");
            }
            GeneratedQuestionSpec spec = specs.get(0);
            return new GeneratedQuestionSpec(
                    spec.questionText(),
                    item.getTier(),
                    StringUtils.hasText(spec.sceneCode()) ? spec.sceneCode() : item.getSceneCode(),
                    spec.priority(),
                    spec.monitorFrequency(),
                    spec.scoreRelevance(),
                    spec.scoreIntent(),
                    spec.scoreCompetition(),
                    spec.scoreConversion(),
                    spec.scoreCoverage(),
                    spec.totalScore(),
                    spec.relatedNeedText(),
                    spec.designReason()
            );
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(500, "重生成问题失败: " + ex.getMessage());
        }
    }

    private void markBatchRunning(GeoQuestionBatch batch) {
        batch.setStatus("running");
        batch.setStartedAt(LocalDateTime.now());
        batch.setProgressJson(progress("generating", null, null, 0, n(batch.getRequestA()) + n(batch.getRequestB()) + n(batch.getRequestC()), 0, "开始生成"));
        batchMapper.updateById(batch);
        log(batch.getId(), "llm_request_started", "开始生成问题");
    }

    private List<GeneratedQuestionSpec> invokeQuestionGeneration(GeoQuestionBatch batch, int total) throws Exception {
        AiPlatformConfig config = resolveBatchModel(batch);
        String modelId = generationModelId(config);
        String apiKey = platformCredentialService.resolveApiKey(config.getPlatformCode(), config.getPrimaryKeyRef(), config.getApiKey());
        if (!StringUtils.hasText(apiKey)) {
            throw new BizException(400, "当前模型未配置 API Key，无法生成真实问题");
        }
        String prompt = buildQuestionGenerationPrompt(batch);
        LlmInvokeResult result = llmInvoker.invoke(prompt, new LlmModelConfig(
                config.getPlatformCode(),
                config.getPlatformName(),
                modelId,
                generationModelName(config, modelId),
                config.getApiUrl(),
                apiKey,
                "你是 GEO 服务的分层问题池生成助手，必须只输出 JSON。",
                batch.getTemperature() == null ? 0.7D : batch.getTemperature().doubleValue(),
                llmProperties.getConnectTimeoutMs(),
                QUESTION_GENERATION_TIMEOUT_MS,
                Math.max(0, n(config.getMaxRetry())),
                Math.max(1, n(config.getRateLimitQps()) == 0 ? 1 : n(config.getRateLimitQps())),
                null,
                true,
                LlmModelConfig.LONG_FORM_MAX_REQUEST_TIMEOUT_MS
        ));
        saveLlmResponse(batch.getId(), result.responseText());
        List<GeneratedQuestionSpec> specs = parseGeneratedQuestions(result.responseText(), batch);
        validateGeneratedCounts(specs, batch, total);
        log(batch.getId(), "llm_response_received", "模型返回问题 " + specs.size() + " 条");
        return specs;
    }

    private void saveLlmResponse(Long batchId, String responseText) {
        batchMapper.update(null, new LambdaUpdateWrapper<GeoQuestionBatch>()
                .eq(GeoQuestionBatch::getId, batchId)
                .set(GeoQuestionBatch::getLlmResponseSnapshot, responseText)
                .set(GeoQuestionBatch::getUpdatedAt, LocalDateTime.now()));
        log(batchId, "llm_response_saved", "模型原始返回已保存");
    }

    private AiPlatformConfig resolveBatchModel(GeoQuestionBatch batch) {
        LambdaQueryWrapper<AiPlatformConfig> wrapper = new LambdaQueryWrapper<AiPlatformConfig>()
                .eq(AiPlatformConfig::getEnabled, true)
                .isNotNull(AiPlatformConfig::getLowModelId)
                .apply("TRIM(low_model_id) <> ''")
                .last("LIMIT 1");
        if (StringUtils.hasText(batch.getModelProvider())) {
            wrapper.eq(AiPlatformConfig::getPlatformCode, batch.getModelProvider());
        }
        AiPlatformConfig config = aiPlatformConfigMapper.selectOne(wrapper);
        if (config == null || !StringUtils.hasText(config.getApiUrl()) || !StringUtils.hasText(generationModelId(config))) {
            throw new BizException(400, "当前批次选择的模型配置不可用");
        }
        return config;
    }

    private AiPlatformConfig resolveRequestedModel(BatchStartRequest req) {
        LambdaQueryWrapper<AiPlatformConfig> wrapper = new LambdaQueryWrapper<AiPlatformConfig>()
                .eq(AiPlatformConfig::getEnabled, true)
                .isNotNull(AiPlatformConfig::getLowModelId)
                .apply("TRIM(low_model_id) <> ''")
                .last("LIMIT 1");
        if (req.getModelConfigId() != null) {
            wrapper.eq(AiPlatformConfig::getId, req.getModelConfigId());
        } else if (StringUtils.hasText(req.getModelProvider())) {
            wrapper.eq(AiPlatformConfig::getPlatformCode, req.getModelProvider());
        }
        AiPlatformConfig config = aiPlatformConfigMapper.selectOne(wrapper);
        if (config == null || !StringUtils.hasText(config.getApiUrl()) || !StringUtils.hasText(generationModelId(config))) {
            throw new BizException(400, "当前批次选择的模型配置不可用");
        }
        return config;
    }

    private String generationModelId(AiPlatformConfig config) {
        if (config == null) {
            return null;
        }
        return defaultText(config.getLowModelId(), null);
    }

    private String generationModelName(AiPlatformConfig config, String modelId) {
        if (config == null) {
            return modelId;
        }
        return defaultText(config.getModelName(), modelId);
    }

    private String buildQuestionGenerationPrompt(GeoQuestionBatch batch) {
        return """
                请基于以下输入生成分层 GEO 问题池。本批必须生成且仅生成 %d 条，其中 A 类 %d 条、B 类 %d 条、C 类 %d 条。

                场景枚举只能使用：brand、decision、deal、compare、qa、function。
                输出必须是 JSON 数组，不要 Markdown，不要解释。每个对象字段：
                questionText, tier, sceneCode, priority, monitorFrequency,
                scoreRelevance, scoreIntent, scoreCompetition, scoreConversion, scoreCoverage, totalScore,
                relatedNeedText, designReason。

                质量要求：
                1. questionText 必须是可直接用于搜索或 AI 问答监测的中文问题，不能是模板句。
                2. A 类围绕承诺考核和核心转化，B 类围绕重点观察和竞品对比，C 类围绕长尾铺底。
                3. designReason 说明该题为什么属于该层级和场景。
                4. 分数使用 0-5 的一位小数。

                输入资料：
                %s
                """.formatted(
                n(batch.getRequestA()) + n(batch.getRequestB()) + n(batch.getRequestC()),
                n(batch.getRequestA()),
                n(batch.getRequestB()),
                n(batch.getRequestC()),
                defaultText(batch.getPromptSnapshot(), "")
        );
    }

    private List<GeneratedQuestionSpec> parseGeneratedQuestions(String responseText, GeoQuestionBatch batch) {
        String json = extractJsonArray(responseText);
        try {
            List<Map<String, Object>> rows = objectMapper.readValue(json, QUESTION_MAP_LIST);
            List<GeneratedQuestionSpec> specs = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                String text = firstText(row, "questionText", "question_text", "问题文本");
                if (!StringUtils.hasText(text)) continue;
                specs.add(new GeneratedQuestionSpec(
                        text.trim(),
                        normalizeTier(firstText(row, "tier", "分级")),
                        normalizeScene(firstText(row, "sceneCode", "scene_code", "场景")),
                        defaultText(firstText(row, "priority", "优先级"), "中"),
                        defaultText(firstText(row, "monitorFrequency", "monitor_frequency", "监测频率"), "每周"),
                        decimal(firstValue(row, "scoreRelevance", "商业价值"), "4.0"),
                        decimal(firstValue(row, "scoreIntent", "成交距离"), "4.0"),
                        decimal(firstValue(row, "scoreCompetition", "品牌绑定"), "4.0"),
                        decimal(firstValue(row, "scoreConversion", "地域行业"), "4.0"),
                        decimal(firstValue(row, "scoreCoverage", "一期可达"), "4.0"),
                        decimal(firstValue(row, "totalScore", "总分"), "4.0"),
                        defaultText(firstText(row, "relatedNeedText", "related_need_text", "对应需求"), "需求"),
                        defaultText(firstText(row, "designReason", "design_reason", "设计理由"), "基于客户资料与分层标准生成。")
                ));
            }
            return specs;
        } catch (JsonProcessingException e) {
            log(batch.getId(), "llm_parse_failed", "模型返回不是合法 JSON 数组");
            throw new BizException(500, "模型返回格式错误，请重试或调整提示词");
        }
    }

    private String extractJsonArray(String responseText) {
        if (!StringUtils.hasText(responseText)) {
            throw new BizException(500, "模型未返回内容");
        }
        String text = responseText.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```(?:json)?", "").replaceFirst("```$", "").trim();
        }
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start < 0 || end <= start) {
            throw new BizException(500, "模型返回缺少 JSON 数组");
        }
        return text.substring(start, end + 1);
    }

    private void validateGeneratedCounts(List<GeneratedQuestionSpec> specs, GeoQuestionBatch batch, int total) {
        validateGeneratedQuestionDuplicates(specs);
        long a = specs.stream().filter(item -> "A".equals(item.tier())).count();
        long b = specs.stream().filter(item -> "B".equals(item.tier())).count();
        long c = specs.stream().filter(item -> "C".equals(item.tier())).count();
        if (specs.size() < total || a < n(batch.getRequestA()) || b < n(batch.getRequestB()) || c < n(batch.getRequestC())) {
            throw new BizException(500, "模型返回数量不足：A=" + a + " B=" + b + " C=" + c + "，请重试");
        }
    }

    private void validateGeneratedQuestionDuplicates(List<GeneratedQuestionSpec> specs) {
        Set<String> seen = new HashSet<>();
        for (GeneratedQuestionSpec spec : specs) {
            String key = dedupeQuestionText(spec.questionText());
            if (!seen.add(key)) {
                throw new BizException(500, "模型返回重复问题，请重试：" + spec.questionText());
            }
        }
    }

    private void persistGeneratedQuestions(GeoQuestionBatch batch, List<GeneratedQuestionSpec> specs, int total) throws InterruptedException {
        Map<String, Integer> need = new HashMap<>();
        need.put("A", n(batch.getRequestA()));
        need.put("B", n(batch.getRequestB()));
        need.put("C", n(batch.getRequestC()));
        Map<String, Integer> actual = new HashMap<>();
        actual.put("A", 0); actual.put("B", 0); actual.put("C", 0);
        int generated = 0;
        for (GeneratedQuestionSpec spec : specs) {
            if (actual.getOrDefault(spec.tier(), 0) >= need.getOrDefault(spec.tier(), 0)) continue;
            GeoQuestionBatch latest = batchMapper.selectById(batch.getId());
            if (Boolean.TRUE.equals(latest.getCancelRequested())) {
                finishBatch(batch.getId(), "cancelled", generated > 0, null);
                return;
            }
            generated++;
            actual.put(spec.tier(), actual.getOrDefault(spec.tier(), 0) + 1);
            GeoQuestionItem item = new GeoQuestionItem();
            item.setWorkorderId(batch.getWorkorderId());
            item.setBatchId(batch.getId());
            item.setTier(spec.tier());
            item.setSceneCode(spec.sceneCode());
            item.setQuestionText(spec.questionText());
            item.setPriority(spec.priority());
            item.setMonitorFrequency(spec.monitorFrequency());
            item.setScoreRelevance(spec.scoreRelevance());
            item.setScoreIntent(spec.scoreIntent());
            item.setScoreCompetition(spec.scoreCompetition());
            item.setScoreConversion(spec.scoreConversion());
            item.setScoreCoverage(spec.scoreCoverage());
            item.setTotalScore(spec.totalScore());
            item.setRelatedNeedText(spec.relatedNeedText());
            item.setDesignReason(spec.designReason());
            item.setStatus("pending_review");
            item.setReplaceCount(0);
            item.setSortOrder(generated);
            item.setCreatedAt(LocalDateTime.now());
            item.setUpdatedAt(LocalDateTime.now());
            itemMapper.insert(item);
            batchMapper.update(null, new LambdaUpdateWrapper<GeoQuestionBatch>()
                    .eq(GeoQuestionBatch::getId, batch.getId())
                    .set(GeoQuestionBatch::getActualA, actual.get("A"))
                    .set(GeoQuestionBatch::getActualB, actual.get("B"))
                    .set(GeoQuestionBatch::getActualC, actual.get("C"))
                    .set(GeoQuestionBatch::getPartialFlag, true)
                    .set(GeoQuestionBatch::getProgressJson, progress("generating", spec.tier(), spec.sceneCode(), generated, total, 0, "正在保存模型生成问题"))
                    .set(GeoQuestionBatch::getUpdatedAt, LocalDateTime.now()));
            if (generated >= total) break;
        }
        log(batch.getId(), "questions_saved", "模型生成问题已落库 " + generated + "/" + total);
    }

    private void finishBatch(Long batchId, String status, boolean partial, String error) {
        GeoQuestionBatch batch = batchMapper.selectById(batchId);
        if (batch == null || "deleted".equals(batch.getStatus())) return;
        int total = n(batch.getRequestA()) + n(batch.getRequestB()) + n(batch.getRequestC());
        int actual = n(batch.getActualA()) + n(batch.getActualB()) + n(batch.getActualC());
        batchMapper.update(null, new LambdaUpdateWrapper<GeoQuestionBatch>()
                .eq(GeoQuestionBatch::getId, batchId)
                .set(GeoQuestionBatch::getStatus, status)
                .set(GeoQuestionBatch::getReservedA, 0)
                .set(GeoQuestionBatch::getReservedB, 0)
                .set(GeoQuestionBatch::getReservedC, 0)
                .set(GeoQuestionBatch::getActiveRunningFlag, null)
                .set(GeoQuestionBatch::getPartialFlag, partial || actual < total)
                .set(GeoQuestionBatch::getErrorMessage, error)
                .set(GeoQuestionBatch::getFinishedAt, LocalDateTime.now())
                .set(GeoQuestionBatch::getProgressJson, progress(status, null, null, actual, total, 0, error == null ? "批次结束" : error))
                .set(GeoQuestionBatch::getUpdatedAt, LocalDateTime.now()));
        log(batchId, "quota_reconciled", "按实际生成题数结算并释放未使用预占");
        log(batchId, "batch_" + status, "批次状态变更为 " + status);
    }

    private void validateSceneWeights(Map<String, Integer> weights, int total) {
        if (weights == null || weights.isEmpty()) throw new BizException(400, "场景权重不能为空");
        int sum = weights.values().stream().mapToInt(this::n).sum();
        if (sum != total) throw new BizException(400, "场景权重总和必须等于本批合计");
    }

    private List<ManualQuestionItemRequest> normalizeManualItems(List<ManualQuestionItemRequest> rawItems) {
        List<ManualQuestionItemRequest> items = new ArrayList<>();
        for (ManualQuestionItemRequest raw : rawItems) {
            if (raw == null) continue;
            String questionText = defaultText(raw.getQuestionText(), "").trim();
            if (!StringUtils.hasText(questionText)) {
                throw new BizException(400, "问题文本不能为空");
            }
            if (questionText.length() > 500) {
                throw new BizException(400, "问题文本最多 500 字");
            }
            ManualQuestionItemRequest item = new ManualQuestionItemRequest();
            item.setQuestionText(questionText);
            item.setTier(normalizeTier(raw.getTier()));
            item.setSceneCode(normalizeScene(raw.getSceneCode()));
            item.setPriority(defaultText(raw.getPriority(), "medium").trim());
            item.setMonitorFrequency(defaultText(raw.getMonitorFrequency(), "weekly").trim());
            item.setScoreRelevance(raw.getScoreRelevance());
            item.setScoreIntent(raw.getScoreIntent());
            item.setScoreCompetition(raw.getScoreCompetition());
            item.setScoreConversion(raw.getScoreConversion());
            item.setScoreCoverage(raw.getScoreCoverage());
            item.setTotalScore(raw.getTotalScore());
            item.setRelatedNeedText(defaultText(raw.getRelatedNeedText(), "").trim());
            item.setDesignReason(defaultText(raw.getDesignReason(), "").trim());
            items.add(item);
        }
        if (items.isEmpty()) {
            throw new BizException(400, "请至少录入 1 条有效问题");
        }
        return items;
    }

    private Map<String, Integer> countManualTiers(List<ManualQuestionItemRequest> items) {
        Map<String, Integer> counts = new HashMap<>();
        counts.put("A", 0); counts.put("B", 0); counts.put("C", 0);
        for (ManualQuestionItemRequest item : items) {
            counts.put(item.getTier(), counts.getOrDefault(item.getTier(), 0) + 1);
        }
        return counts;
    }

    private Map<String, Integer> countManualScenes(List<ManualQuestionItemRequest> items) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String scene : SCENES) {
            counts.put(scene, 0);
        }
        for (ManualQuestionItemRequest item : items) {
            counts.put(item.getSceneCode(), counts.getOrDefault(item.getSceneCode(), 0) + 1);
        }
        return counts;
    }

    private void validateManualQuestionDuplicates(Long workorderId, List<ManualQuestionItemRequest> items) {
        Set<String> existing = allQuestions(workorderId).stream()
                .map(GeoQuestionItem::getQuestionText)
                .map(this::dedupeQuestionText)
                .collect(Collectors.toSet());
        Set<String> current = new HashSet<>();
        for (ManualQuestionItemRequest item : items) {
            String key = dedupeQuestionText(item.getQuestionText());
            if (existing.contains(key)) {
                throw new BizException(400, "问题已存在：" + item.getQuestionText());
            }
            if (!current.add(key)) {
                throw new BizException(400, "本次录入存在重复问题：" + item.getQuestionText());
            }
        }
    }

    private String dedupeQuestionText(String value) {
        return defaultText(value, "").trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private int maxQuestionSortOrder(Long workorderId) {
        GeoQuestionItem latest = itemMapper.selectOne(new LambdaQueryWrapper<GeoQuestionItem>()
                .eq(GeoQuestionItem::getWorkorderId, workorderId)
                .ne(GeoQuestionItem::getStatus, "deleted")
                .orderByDesc(GeoQuestionItem::getSortOrder)
                .last("LIMIT 1"));
        return latest == null ? 0 : n(latest.getSortOrder());
    }

    private BigDecimal manualTotalScore(ManualQuestionItemRequest req) {
        List<BigDecimal> scores = new ArrayList<>();
        if (req.getScoreRelevance() != null) scores.add(req.getScoreRelevance());
        if (req.getScoreIntent() != null) scores.add(req.getScoreIntent());
        if (req.getScoreCompetition() != null) scores.add(req.getScoreCompetition());
        if (req.getScoreConversion() != null) scores.add(req.getScoreConversion());
        if (req.getScoreCoverage() != null) scores.add(req.getScoreCoverage());
        if (scores.isEmpty()) return null;
        return scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<String, Integer> itemCounts(Long workorderId) {
        Map<String, Integer> counts = new HashMap<>();
        counts.put("A", 0); counts.put("B", 0); counts.put("C", 0);
        if (workorderId == null) return counts;
        itemMapper.selectList(new LambdaQueryWrapper<GeoQuestionItem>()
                .eq(GeoQuestionItem::getWorkorderId, workorderId)
                .ne(GeoQuestionItem::getStatus, "deleted"))
                .forEach(item -> counts.put(item.getTier(), counts.getOrDefault(item.getTier(), 0) + 1));
        return counts;
    }

    private void releaseStaleRunningFlags(Long workorderId) {
        if (workorderId == null) return;
        batchMapper.update(null, new LambdaUpdateWrapper<GeoQuestionBatch>()
                .eq(GeoQuestionBatch::getWorkorderId, workorderId)
                .isNotNull(GeoQuestionBatch::getActiveRunningFlag)
                .notIn(GeoQuestionBatch::getStatus, List.of("pending", "running"))
                .set(GeoQuestionBatch::getActiveRunningFlag, null)
                .set(GeoQuestionBatch::getReservedA, 0)
                .set(GeoQuestionBatch::getReservedB, 0)
                .set(GeoQuestionBatch::getReservedC, 0)
                .set(GeoQuestionBatch::getUpdatedAt, LocalDateTime.now()));
    }

    private GeoQuestionBatch runningBatch(Long workorderId) {
        if (workorderId == null) return null;
        return batchMapper.selectOne(new LambdaQueryWrapper<GeoQuestionBatch>()
                .eq(GeoQuestionBatch::getWorkorderId, workorderId)
                .eq(GeoQuestionBatch::getActiveRunningFlag, 1)
                .in(GeoQuestionBatch::getStatus, List.of("pending", "running"))
                .last("LIMIT 1"));
    }

    private void log(Long batchId, String eventCode, String message) {
        GeoQuestionBatchLog log = new GeoQuestionBatchLog();
        log.setBatchId(batchId);
        log.setEventCode(eventCode);
        log.setMessage(message);
        log.setCreatedAt(LocalDateTime.now());
        logMapper.insert(log);
    }

    private BatchVO toBatchVO(GeoQuestionBatch batch) {
        BatchVO vo = new BatchVO();
        vo.setId(batch.getId());
        vo.setWorkorderId(batch.getWorkorderId());
        vo.setBatchNo(batch.getBatchNo());
        vo.setRequestA(n(batch.getRequestA()));
        vo.setRequestB(n(batch.getRequestB()));
        vo.setRequestC(n(batch.getRequestC()));
        vo.setActualA(n(batch.getActualA()));
        vo.setActualB(n(batch.getActualB()));
        vo.setActualC(n(batch.getActualC()));
        vo.setBatchType("manual".equals(batch.getModelProvider()) ? "manual" : "ai");
        vo.setModelName(batch.getModelName());
        vo.setStatus(batch.getStatus());
        vo.setProgressJson(batch.getProgressJson());
        vo.setErrorMessage(batch.getErrorMessage());
        vo.setLlmResponseSnapshot(batch.getLlmResponseSnapshot());
        vo.setPartialFlag(Boolean.TRUE.equals(batch.getPartialFlag()));
        vo.setCancelRequested(Boolean.TRUE.equals(batch.getCancelRequested()));
        vo.setCreatedAt(batch.getCreatedAt());
        vo.setStartedAt(batch.getStartedAt());
        vo.setFinishedAt(batch.getFinishedAt());
        vo.setLogs(logMapper.selectList(new LambdaQueryWrapper<GeoQuestionBatchLog>()
                .eq(GeoQuestionBatchLog::getBatchId, batch.getId())
                .orderByAsc(GeoQuestionBatchLog::getCreatedAt)).stream().map(log -> {
            BatchLogVO l = new BatchLogVO();
            l.setEventCode(log.getEventCode());
            l.setMessage(log.getMessage());
            l.setCreatedAt(log.getCreatedAt());
            return l;
        }).collect(Collectors.toList()));
        return vo;
    }

    private WorkorderVO toWorkorderVO(GeoQuestionWorkorder workorder, QuotaSnapshot quota) {
        Company company = companyMapper.selectById(workorder.getCompanyId());
        Project project = workorder.getProjectId() == null ? null : projectMapper.selectById(workorder.getProjectId());
        Brand brand = project != null && project.getBrandId() != null ? brandMapper.selectById(project.getBrandId()) : null;
        WorkorderVO vo = new WorkorderVO();
        vo.setId(workorder.getId());
        vo.setCompanyId(workorder.getCompanyId());
        vo.setCompanyName(company == null ? "" : company.getCompanyName());
        vo.setBrandId(project == null ? null : project.getBrandId());
        vo.setBrandName(project == null ? null : defaultText(project.getBrandName(), brand == null ? null : brand.getBrandName()));
        vo.setProjectId(workorder.getProjectId());
        vo.setProjectName(project == null ? null : project.getProjectName());
        vo.setPackageName(workorder.getPackageName());
        vo.setStatus(workorder.getStatus());
        vo.setTargetA(n(workorder.getTargetA()));
        vo.setTargetB(n(workorder.getTargetB()));
        vo.setTargetC(n(workorder.getTargetC()));
        vo.setQuota(quota);
        return vo;
    }

    private WorkorderListItemVO toWorkorderListItemVO(GeoQuestionWorkorder workorder) {
        WorkorderListItemVO vo = new WorkorderListItemVO();
        vo.setId(workorder.getId());
        vo.setCompanyId(workorder.getCompanyId());
        vo.setProjectId(workorder.getProjectId());
        if (workorder.getProjectId() != null) {
            Project project = projectMapper.selectById(workorder.getProjectId());
            vo.setProjectName(project == null ? null : project.getProjectName());
        }
        vo.setWorkorderNo("WO-" + workorder.getId());
        vo.setPackageName(workorder.getPackageName());
        vo.setStatus(workorder.getStatus());
        vo.setTargetA(n(workorder.getTargetA()));
        vo.setTargetB(n(workorder.getTargetB()));
        vo.setTargetC(n(workorder.getTargetC()));
        Map<String, Integer> counts = itemCounts(workorder.getId());
        vo.setCountA(counts.get("A"));
        vo.setCountB(counts.get("B"));
        vo.setCountC(counts.get("C"));
        vo.setCountTotal(n(vo.getCountA()) + n(vo.getCountB()) + n(vo.getCountC()));
        List<GeoQuestionBatch> batches = batchMapper.selectList(new LambdaQueryWrapper<GeoQuestionBatch>()
                .eq(GeoQuestionBatch::getWorkorderId, workorder.getId())
                .ne(GeoQuestionBatch::getStatus, "deleted")
                .orderByDesc(GeoQuestionBatch::getCreatedAt));
        vo.setBatchCount(batches.size());
        if (!batches.isEmpty()) {
            GeoQuestionBatch latest = batches.get(0);
            vo.setLatestBatchStatus(latest.getStatus());
            vo.setLatestBatchAt(latest.getCreatedAt());
        }
        vo.setCreatedAt(workorder.getCreatedAt());
        vo.setUpdatedAt(workorder.getUpdatedAt());
        return vo;
    }

    private QuestionVO toQuestionVO(GeoQuestionItem item) {
        QuestionVO vo = new QuestionVO();
        vo.setId(item.getId());
        vo.setBatchId(item.getBatchId());
        vo.setQuestionText(item.getQuestionText());
        vo.setSceneCode(item.getSceneCode());
        vo.setTier(item.getTier());
        vo.setPriority(item.getPriority());
        vo.setMonitorFrequency(item.getMonitorFrequency());
        vo.setScoreRelevance(item.getScoreRelevance());
        vo.setScoreIntent(item.getScoreIntent());
        vo.setScoreCompetition(item.getScoreCompetition());
        vo.setScoreConversion(item.getScoreConversion());
        vo.setScoreCoverage(item.getScoreCoverage());
        vo.setTotalScore(item.getTotalScore());
        vo.setRelatedNeedText(item.getRelatedNeedText());
        vo.setDesignReason(item.getDesignReason());
        vo.setStatus(item.getStatus());
        vo.setReplaceCount(n(item.getReplaceCount()));
        return vo;
    }

    private DraftVO toDraftVO(GeoQuestionProfileDraft draft) {
        DraftVO vo = new DraftVO();
        vo.setWorkorderId(draft.getWorkorderId());
        vo.setProfileJson(draft.getProfileJson());
        vo.setSyncToCustomerProfile(Boolean.TRUE.equals(draft.getSyncToCustomerProfile()));
        vo.setValidationStatus(draft.getValidationStatus());
        vo.setAutoSavedAt(draft.getAutoSavedAt());
        return vo;
    }

    private Project requireProject(Long projectId) {
        if (projectId == null) {
            throw new BizException(400, "projectId is required");
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null || project.getDeletedAt() != null) {
            throw new BizException(404, "Project not found");
        }
        if (project.getCompanyId() == null) {
            throw new BizException(400, "项目未绑定客户，不能进入分层拓词管理");
        }
        return project;
    }

    private KeywordAllocation projectKeywordAllocation(Project project) {
        int a = n(project.getPlanKeywordGroupLimitA());
        int b = n(project.getPlanKeywordGroupLimitB());
        int c = n(project.getPlanKeywordGroupLimitC());
        if (a == 0 && b == 0 && c == 0) {
            a = n(project.getPlanKeywordGroupLimit());
        }
        return new KeywordAllocation(a, b, c);
    }

    private String projectRegion(Project project, String fallback) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(project.getProvinceName())) parts.add(project.getProvinceName());
        if (StringUtils.hasText(project.getCityName())) parts.add(project.getCityName());
        if (StringUtils.hasText(project.getDistrictName())) parts.add(project.getDistrictName());
        return parts.isEmpty() ? fallback : String.join(" / ", parts);
    }

    private List<Map<String, Object>> projectCoreNeeds(Long projectId) {
        List<ProjectCustomerRequirement> requirements = projectCustomerRequirementMapper.selectList(
                new LambdaQueryWrapper<ProjectCustomerRequirement>()
                        .eq(ProjectCustomerRequirement::getProjectId, projectId)
                        .orderByDesc(ProjectCustomerRequirement::getCreatedAt)
                        .orderByDesc(ProjectCustomerRequirement::getId)
        );
        List<Map<String, Object>> coreNeeds = new ArrayList<>();
        for (ProjectCustomerRequirement requirement : requirements) {
            if (!StringUtils.hasText(requirement.getRequirementText())) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("text", requirement.getRequirementText());
            item.put("scene", "brand");
            item.put("urgent", false);
            coreNeeds.add(item);
        }
        return coreNeeds;
    }

    private String buildPromptSnapshot(GeoQuestionWorkorder workorder, BatchStartRequest req) {
        String userInput = renderUserInputPrompt(workorder, req);
        return readPromptResource(SYSTEM_PROMPT)
                + "\n\n"
                + readPromptResource(ABC_TIER_PROMPT)
                + "\n\n"
                + userInput;
    }

    private String renderUserInputPrompt(GeoQuestionWorkorder workorder, BatchStartRequest req) {
        String template = readPromptResource(USER_INPUT_TEMPLATE);
        Map<String, Object> profile = loadProfileSnapshot(workorder.getId());
        int batchA = n(req.getBatchA());
        int batchB = n(req.getBatchB());
        int batchC = n(req.getBatchC());
        int total = batchA + batchB + batchC;
        Map<String, String> values = new LinkedHashMap<>();
        values.put("company_name", value(profile, "companyName"));
        values.put("brand_name", value(profile, "brandName"));
        values.put("brand_relation", value(profile, "brandRelation"));
        values.put("core_business", renderList(profile.get("coreBusiness")));
        values.put("target_region", value(profile, "targetRegion"));
        values.put("industry", value(profile, "industry"));
        values.put("target_customer", value(profile, "targetCustomer"));
        values.put("main_competitors", renderCompetitors(profile.get("competitors")));
        values.put("core_advantage", value(profile, "coreAdvantage"));
        values.put("benchmark_specs", defaultText(value(profile, "benchmarkSpecs"), "无"));
        values.put("core_needs", renderCoreNeeds(profile.get("coreNeeds")));
        values.put("total_count", String.valueOf(total));
        values.put("scene_weights", renderSceneWeights(req.getSceneWeights()));
        values.put("abc_split", batchA + "/" + batchB + "/" + batchC);
        values.put("batch_a", String.valueOf(batchA));
        values.put("batch_b", String.valueOf(batchB));
        values.put("batch_c", String.valueOf(batchC));
        for (Map.Entry<String, String> entry : values.entrySet()) {
            template = template.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return template;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadProfileSnapshot(Long workorderId) {
        GeoQuestionProfileDraft draft = draftMapper.selectOne(new LambdaQueryWrapper<GeoQuestionProfileDraft>()
                .eq(GeoQuestionProfileDraft::getWorkorderId, workorderId)
                .last("LIMIT 1"));
        if (draft == null || !StringUtils.hasText(draft.getProfileJson())) return Collections.emptyMap();
        try {
            return objectMapper.readValue(draft.getProfileJson(), Map.class);
        } catch (JsonProcessingException e) {
            throw new BizException(400, "工单信息补全草稿格式错误");
        }
    }

    private String readPromptResource(String path) {
        try {
            return new String(new ClassPathResource(path).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BizException("读取分层拓词提示词配置失败: " + path, e);
        }
    }

    private String value(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private String renderList(Object value) {
        if (value instanceof Collection<?> list) {
            return list.stream().map(String::valueOf).filter(StringUtils::hasText).collect(Collectors.joining("、"));
        }
        return value == null ? "" : String.valueOf(value);
    }

    private String renderCompetitors(Object value) {
        if (!(value instanceof Collection<?> list) || list.isEmpty()) return "无";
        List<String> items = new ArrayList<>();
        int index = 1;
        for (Object raw : list) {
            if (raw instanceof Map<?, ?> item) {
                String name = mapString(item, "competitorName", mapString(item, "competitor_name", ""));
                String advantages = mapString(item, "advantages", "");
                String disadvantages = mapString(item, "disadvantages", "");
                if (StringUtils.hasText(name)) {
                    items.add(index + ". " + name + "；优势：" + defaultText(advantages, "未填写") + "；劣势：" + defaultText(disadvantages, "未填写"));
                    index++;
                }
            }
        }
        return items.isEmpty() ? "无" : String.join("\n", items);
    }

    private String renderCoreNeeds(Object value) {
        if (!(value instanceof Collection<?> list) || list.isEmpty()) return "需求 1: 待补全 | 场景: 品牌";
        List<String> items = new ArrayList<>();
        int index = 1;
        for (Object raw : list) {
            if (raw instanceof Map<?, ?> item) {
                String text = mapString(item, "text", "");
                String scene = mapString(item, "scene", "");
                String urgent = Boolean.parseBoolean(mapString(item, "urgent", "false")) ? " | 紧急: 是" : "";
                if (StringUtils.hasText(text)) {
                    items.add("需求 " + index + ": " + text + " | 场景: " + sceneLabel(scene) + urgent);
                    index++;
                }
            }
        }
        return items.isEmpty() ? "需求 1: 待补全 | 场景: 品牌" : String.join("\n", items);
    }

    private String mapString(Map<?, ?> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    private String firstText(Map<String, Object> map, String... keys) {
        Object value = firstValue(map, keys);
        return value == null ? "" : String.valueOf(value);
    }

    private Object firstValue(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                return value;
            }
        }
        return null;
    }

    private String renderSceneWeights(Map<String, Integer> weights) {
        if (weights == null || weights.isEmpty()) return "品牌25/决策30/成交25/对比25/问答25/功能20";
        return SCENES.stream()
                .map(scene -> sceneLabel(scene) + n(weights.get(scene)))
                .collect(Collectors.joining("/"));
    }

    private String sceneLabel(String scene) {
        return switch (defaultText(scene, "")) {
            case "brand" -> "品牌";
            case "decision" -> "决策";
            case "deal" -> "成交";
            case "compare" -> "对比";
            case "qa" -> "问答";
            case "function" -> "功能";
            default -> defaultText(scene, "未指定");
        };
    }

    private String normalizeTier(String value) {
        String raw = defaultText(value, "C").trim().toUpperCase(Locale.ROOT);
        if (raw.startsWith("A")) return "A";
        if (raw.startsWith("B")) return "B";
        return "C";
    }

    private String normalizeScene(String value) {
        String raw = defaultText(value, "brand").trim();
        if (SCENES.contains(raw)) return raw;
        return switch (raw) {
            case "品牌", "品牌场景" -> "brand";
            case "决策", "决策场景" -> "decision";
            case "成交", "成交场景" -> "deal";
            case "对比", "对比场景" -> "compare";
            case "问答", "问答场景" -> "qa";
            case "功能", "功能场景" -> "function";
            default -> "brand";
        };
    }

    private BigDecimal decimal(Object value, String fallback) {
        if (value == null) return new BigDecimal(fallback);
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return new BigDecimal(fallback);
        }
    }

    private BigDecimal defaultBigDecimal(BigDecimal value, BigDecimal fallback) {
        return value == null ? fallback : value;
    }

    private String progress(String phase, String tier, String scene, int generated, int target, int tokenUsed, String message) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("phase", phase);
        map.put("currentTier", tier);
        map.put("currentScene", scene);
        map.put("generated", generated);
        map.put("target", target);
        map.put("tokenUsed", tokenUsed);
        map.put("message", message);
        return writeJson(map);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BizException("JSON serialization failed", e);
        }
    }

    private List<String> splitTags(String value) {
        if (!StringUtils.hasText(value)) return new ArrayList<>();
        String trimmed = value.trim();
        if (trimmed.startsWith("[")) {
            try {
                List<?> parsed = objectMapper.readValue(trimmed, List.class);
                return parsed.stream().map(String::valueOf).filter(StringUtils::hasText).limit(8).collect(Collectors.toList());
            } catch (JsonProcessingException ignored) {
                // Fallback to delimiter split for legacy non-JSON values.
            }
        }
        return Arrays.stream(value.split("[,，/、\\s]+")).filter(StringUtils::hasText).limit(8).collect(Collectors.toList());
    }

    private List<Map<String, Object>> defaultCompetitors(String competitors) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (String name : splitTags(competitors)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("competitorName", name);
            item.put("advantages", "请补充竞品优势");
            item.put("disadvantages", "请补充竞品劣势");
            list.add(item);
        }
        return list;
    }

    private String buildRegion(Company company) {
        return String.join(" / ", Arrays.asList(defaultText(company.getProvinceName(), ""), defaultText(company.getCityName(), ""), defaultText(company.getDistrictName(), ""))
                .stream().filter(StringUtils::hasText).collect(Collectors.toList()));
    }

    private int n(Integer value) {
        return value == null ? 0 : value;
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : (fallback == null ? "" : fallback);
    }

    private String csv(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private record GeneratedQuestionSpec(String questionText,
                                         String tier,
                                         String sceneCode,
                                         String priority,
                                         String monitorFrequency,
                                         BigDecimal scoreRelevance,
                                         BigDecimal scoreIntent,
                                         BigDecimal scoreCompetition,
                                         BigDecimal scoreConversion,
                                         BigDecimal scoreCoverage,
                                         BigDecimal totalScore,
                                         String relatedNeedText,
                                         String designReason) {
    }

    private record KeywordAllocation(int a, int b, int c) {
        private int total() {
            return a + b + c;
        }
    }
}
