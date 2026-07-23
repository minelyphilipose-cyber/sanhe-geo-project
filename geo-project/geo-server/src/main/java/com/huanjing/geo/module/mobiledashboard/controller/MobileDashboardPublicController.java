package com.huanjing.geo.module.mobiledashboard.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.mobiledashboard.dto.MobileDashboardAggregateVO;
import com.huanjing.geo.module.mobiledashboard.dto.MobileDashboardBootstrapVO;
import com.huanjing.geo.module.mobiledashboard.dto.MobileDashboardSessionRequest;
import com.huanjing.geo.module.mobiledashboard.dto.MobileDashboardSessionVO;
import com.huanjing.geo.module.mobiledashboard.service.MobileDashboardAggregateService;
import com.huanjing.geo.module.mobiledashboard.service.MobileDashboardShareService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.function.Supplier;

@Tag(name = "MobileDashboardPublic")
@RestController
@RequestMapping("/api/public/mobile-dashboard")
@RequiredArgsConstructor
public class MobileDashboardPublicController {

    private static final String SHARE_CARD_CODE_HEADER = "X-Mobile-Dashboard-Share-Code";
    private static final String SHARE_CARD_TITLE_HEADER = "X-Mobile-Dashboard-Share-Title";

    private final MobileDashboardShareService mobileDashboardShareService;
    private final MobileDashboardAggregateService mobileDashboardAggregateService;

    @GetMapping("/share-card-meta")
    public ResponseEntity<Void> shareCardMeta(
            @RequestHeader(value = SHARE_CARD_CODE_HEADER, required = false) String shareCode) {
        String title = mobileDashboardShareService.resolveShareCardTitle(shareCode);
        return ResponseEntity.noContent()
                .header(SHARE_CARD_TITLE_HEADER, encodeAsHtmlEntities(title))
                .build();
    }

    @PostMapping("/session")
    public R<MobileDashboardSessionVO> exchangeSession(@Valid @RequestBody MobileDashboardSessionRequest request,
                                                       HttpServletRequest httpServletRequest) {
        return R.ok(mobileDashboardShareService.exchangeSession(request.getShareCode(), httpServletRequest));
    }

    @GetMapping("/bootstrap")
    public R<MobileDashboardBootstrapVO> bootstrap(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                   HttpServletRequest request) {
        return audited("bootstrap", authorization, request, () -> R.ok(mobileDashboardShareService.getBootstrap(authorization)));
    }

    @GetMapping("/home")
    public R<MobileDashboardAggregateVO.Home> home(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                   @RequestParam(required = false) LocalDate startDate,
                                                   @RequestParam(required = false) LocalDate endDate,
                                                   HttpServletRequest request) {
        return audited("home", authorization, request, () -> {
            Long projectId = mobileDashboardShareService.requireValidSession(authorization).projectId();
            return R.ok(mobileDashboardAggregateService.home(projectId, startDate, endDate));
        });
    }

    @GetMapping("/monitor")
    public R<MobileDashboardAggregateVO.Monitor> monitor(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                         @RequestParam(required = false) LocalDate startDate,
                                                         @RequestParam(required = false) LocalDate endDate,
                                                         @RequestParam(required = false) String platformCode,
                                                         @RequestParam(defaultValue = "1") Integer page,
                                                         @RequestParam(defaultValue = "5") Integer size,
                                                         HttpServletRequest request) {
        return audited("monitor", authorization, request, () -> {
            Long projectId = mobileDashboardShareService.requireValidSession(authorization).projectId();
            return R.ok(mobileDashboardAggregateService.monitor(projectId, startDate, endDate, platformCode, page, size));
        });
    }

    @GetMapping("/monitor/question/{pollResultId}")
    public R<MobileDashboardAggregateVO.QuestionMonitorItem> questionDetail(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                                            @PathVariable Long pollResultId,
                                                                            HttpServletRequest request) {
        return audited("question_detail", authorization, request, () -> {
            Long projectId = mobileDashboardShareService.requireValidSession(authorization).projectId();
            return R.ok(mobileDashboardAggregateService.questionDetail(projectId, pollResultId));
        });
    }

    @GetMapping("/content")
    public R<MobileDashboardAggregateVO.Content> content(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                         @RequestParam(required = false) String month,
                                                         @RequestParam(defaultValue = "1") Integer taskPage,
                                                         @RequestParam(defaultValue = "4") Integer taskSize,
                                                         HttpServletRequest request) {
        return audited("content", authorization, request, () -> {
            Long projectId = mobileDashboardShareService.requireValidSession(authorization).projectId();
            YearMonth yearMonth = month == null || month.isBlank() ? null : YearMonth.parse(month);
            return R.ok(mobileDashboardAggregateService.content(projectId, yearMonth, taskPage, taskSize));
        });
    }

    @GetMapping("/report")
    public R<MobileDashboardAggregateVO.Report> report(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                       HttpServletRequest request) {
        return audited("report", authorization, request, () -> {
            mobileDashboardShareService.requireValidSession(authorization);
            throw new BizException(404, "Mobile dashboard report page is disabled");
        });
    }

    private <T> R<T> audited(String eventType, String authorization, HttpServletRequest request, Supplier<R<T>> action) {
        try {
            R<T> result = action.get();
            mobileDashboardShareService.logPublicApiAccess(authorization, eventType, true, null, request);
            return result;
        } catch (RuntimeException ex) {
            mobileDashboardShareService.logPublicApiAccess(authorization, eventType, false, failReason(ex), request);
            throw ex;
        }
    }

    private String failReason(RuntimeException ex) {
        String name = ex.getClass().getSimpleName();
        if (name == null || name.isBlank()) {
            return "runtime_error";
        }
        return name.length() <= 64 ? name : name.substring(0, 64);
    }

    private String encodeAsHtmlEntities(String value) {
        StringBuilder encoded = new StringBuilder();
        value.codePoints().forEach(codePoint -> encoded
                .append("&#")
                .append(codePoint)
                .append(';'));
        return encoded.toString();
    }
}
