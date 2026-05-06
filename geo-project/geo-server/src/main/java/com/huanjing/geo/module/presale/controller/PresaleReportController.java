package com.huanjing.geo.module.presale.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.presale.dto.request.CreateReportRequest;
import com.huanjing.geo.module.presale.dto.request.LlmPromptQuestionGenerateRequest;
import com.huanjing.geo.module.presale.dto.request.PresalePromptTraceQueryRequest;
import com.huanjing.geo.module.presale.dto.request.ReportListQueryRequest;
import com.huanjing.geo.module.presale.dto.response.LlmPromptQuestionGenerateVO;
import com.huanjing.geo.module.presale.dto.response.PresalePromptTraceDetailVO;
import com.huanjing.geo.module.presale.dto.response.PresalePromptTracePageVO;
import com.huanjing.geo.module.presale.dto.response.PromptTemplateVO;
import com.huanjing.geo.module.presale.dto.response.ReportDetailVO;
import com.huanjing.geo.module.presale.dto.response.ReportListItemVO;
import com.huanjing.geo.module.presale.dto.response.ReportScopePreviewVO;
import com.huanjing.geo.module.presale.dto.response.ReportVersionMetaVO;
import com.huanjing.geo.module.presale.dto.response.ReportVersionOptionVO;
import com.huanjing.geo.module.presale.service.PresaleLlmPromptQuestionService;
import com.huanjing.geo.module.presale.service.PresalePromptTraceService;
import com.huanjing.geo.module.presale.service.PresaleReportService;
import com.huanjing.geo.module.presale.service.PresaleReportVersionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 售前报告 Controller。
 *
 * <p>响应包装与仓库约定一致:</p>
 * <ul>
 *   <li>列表 → {@code R<Page<ReportListItemVO>>}</li>
 *   <li>详情 → {@code R<ReportDetailVO>}</li>
 *   <li>轮询 → {@code R<ReportVersionMetaVO>}</li>
 *   <li>创建 → {@code R<Long>}(返回新建的 reportId)</li>
 * </ul>
 *
 * <p>路由前缀 {@code /api/presale/reports},与 UI 侧路由 {@code /admin/presale/report/*}
 * 不同(后端是 REST 接口前缀,前端是页面路由)。</p>
 */
@RestController
@RequestMapping("/api/presale/reports")
public class PresaleReportController {

    private final PresaleReportService reportService;
    private final PresaleReportVersionService versionService;
    private final PresaleLlmPromptQuestionService llmPromptQuestionService;
    private final PresalePromptTraceService promptTraceService;

    public PresaleReportController(PresaleReportService reportService,
                                   PresaleReportVersionService versionService,
                                   PresaleLlmPromptQuestionService llmPromptQuestionService,
                                   PresalePromptTraceService promptTraceService) {
        this.reportService = reportService;
        this.versionService = versionService;
        this.llmPromptQuestionService = llmPromptQuestionService;
        this.promptTraceService = promptTraceService;
    }

    /**
     * 列表页:分页 + 筛选 + 排序。
     * 使用 @ModelAttribute 把 query string 绑定到 Request 对象。
     */
    @GetMapping
    public R<Page<ReportListItemVO>> list(@ModelAttribute ReportListQueryRequest req) {
        return R.ok(reportService.listReports(req));
    }

    /**
     * 新建报告页:诊断范围预览。
     */
    @GetMapping("/scope-preview")
    public R<ReportScopePreviewVO> getScopePreview() {
        return R.ok(reportService.getScopePreview());
    }

    /**
     * 新建报告页:反显当前 active version 的 Prompt 原文模板。
     */
    @GetMapping("/prompt-templates")
    public R<List<PromptTemplateVO>> listPromptTemplates() {
        return R.ok(reportService.listPromptTemplates());
    }

    @PostMapping("/llm-prompt-questions/generate")
    public R<LlmPromptQuestionGenerateVO> generateLlmPromptQuestions(
            @RequestBody @Valid LlmPromptQuestionGenerateRequest req) {
        return R.ok(llmPromptQuestionService.generate(req));
    }

    /**
     * 新建报告。
     */
    @PostMapping
    public R<Long> create(@RequestBody @Valid CreateReportRequest req) {
        Long reportId = reportService.createReport(req);
        return R.ok(reportId);
    }

    /**
     * 删除报告。当前为软删除:列表/详情不可见,版本与导出审计数据保留。
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable("id") Long id) {
        reportService.deleteReport(id);
        return R.ok();
    }

    /**
     * 取报告的最新版本详情(含 L1/L2/L3 JSON)。
     * 前端进入详情页默认用此接口。
     */
    @GetMapping("/{id}/versions/latest")
    public R<ReportDetailVO> getLatestDetail(@PathVariable("id") Long id) {
        return R.ok(versionService.getDetail(id, null));
    }

    /**
     * 取报告的指定版本详情。
     */
    @GetMapping("/{id}/versions/{versionNo}")
    public R<ReportDetailVO> getVersionDetail(@PathVariable("id") Long id,
                                              @PathVariable("versionNo") Integer versionNo) {
        return R.ok(versionService.getDetail(id, versionNo));
    }

    @GetMapping("/{id}/versions")
    public R<List<ReportVersionOptionVO>> listVersions(@PathVariable("id") Long id) {
        return R.ok(promptTraceService.listVersions(id));
    }

    @GetMapping("/{id}/versions/{versionNo}/prompt-traces")
    public R<PresalePromptTracePageVO> listPromptTraces(@PathVariable("id") Long id,
                                                        @PathVariable("versionNo") Integer versionNo,
                                                        @ModelAttribute PresalePromptTraceQueryRequest req) {
        return R.ok(promptTraceService.list(id, versionNo, req));
    }

    @GetMapping("/{id}/versions/{versionNo}/prompt-traces/{promptResultId}")
    public R<PresalePromptTraceDetailVO> getPromptTraceDetail(@PathVariable("id") Long id,
                                                              @PathVariable("versionNo") Integer versionNo,
                                                              @PathVariable("promptResultId") Long promptResultId) {
        return R.ok(promptTraceService.detail(id, versionNo, promptResultId));
    }

    /**
     * 进度页轮询专用:仅返回最新版本的状态元信息,不含快照 JSON。
     * 前端每 3 秒调用一次,直到 generationStatus ∈ {DONE, FAILED}。
     */
    @GetMapping("/{id}/versions/latest/meta")
    public R<ReportVersionMetaVO> getLatestVersionMeta(@PathVariable("id") Long id) {
        return R.ok(versionService.getLatestVersionMeta(id));
    }
}
