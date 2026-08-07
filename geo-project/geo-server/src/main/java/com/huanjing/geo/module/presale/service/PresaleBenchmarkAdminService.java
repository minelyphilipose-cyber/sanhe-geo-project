package com.huanjing.geo.module.presale.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.presale.dto.request.PresaleBenchmarkSaveRequest;
import com.huanjing.geo.module.presale.dto.request.PresaleBenchmarkStatusRequest;
import com.huanjing.geo.module.presale.persist.entity.PresaleBenchmark;
import com.huanjing.geo.module.presale.persist.entity.PresaleBenchmarkHistory;
import com.huanjing.geo.module.presale.persist.mapper.PresaleBenchmarkHistoryMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleBenchmarkMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PresaleBenchmarkAdminService {
    private static final String ALL = "_ALL_";
    private static final Set<String> MANAGE_ROLES = Set.of("delivery_manager", "manager", "super_admin");
    private static final Set<String> CONFIDENCE_LEVELS = Set.of("HIGH", "MEDIUM", "LOW");

    private final PresaleBenchmarkMapper benchmarkMapper;
    private final PresaleBenchmarkHistoryMapper historyMapper;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;

    public List<PresaleBenchmark> list() {
        ensureManagerRole();
        return benchmarkMapper.selectList(new LambdaQueryWrapper<PresaleBenchmark>()
                .orderByAsc(PresaleBenchmark::getIndustry, PresaleBenchmark::getIndustryRole)
                .orderByDesc(PresaleBenchmark::getEffectiveFrom, PresaleBenchmark::getId));
    }

    public List<PresaleBenchmarkHistory> history(Long benchmarkId) {
        ensureManagerRole();
        LambdaQueryWrapper<PresaleBenchmarkHistory> wrapper = new LambdaQueryWrapper<PresaleBenchmarkHistory>()
                .orderByDesc(PresaleBenchmarkHistory::getCreatedAt, PresaleBenchmarkHistory::getId);
        if (benchmarkId != null) {
            wrapper.eq(PresaleBenchmarkHistory::getBenchmarkId, benchmarkId);
        }
        return historyMapper.selectList(wrapper.last("LIMIT 200"));
    }

    @Transactional
    public PresaleBenchmark create(PresaleBenchmarkSaveRequest request) {
        SysUser operator = ensureManagerRole();
        validate(request, null);
        PresaleBenchmark row = new PresaleBenchmark();
        apply(row, request);
        row.setSource("MANUAL");
        row.setCreatedAt(LocalDateTime.now());
        row.setUpdatedAt(row.getCreatedAt());
        benchmarkMapper.insert(row);
        writeHistory(row, "INSERT", null, row, operator, request.getRemark());
        return row;
    }

    @Transactional
    public PresaleBenchmark update(Long id, PresaleBenchmarkSaveRequest request) {
        ensureManagerRole();
        require(id);
        throw new BizException(400, "行业基准版本不可覆盖修改，请新增一个生效版本或停用原版本");
    }

    @Transactional
    public PresaleBenchmark updateStatus(Long id, PresaleBenchmarkStatusRequest request) {
        SysUser operator = ensureManagerRole();
        PresaleBenchmark row = require(id);
        if (Boolean.FALSE.equals(request.getEnabled())
                && Boolean.TRUE.equals(row.getEnabled())
                && ALL.equals(row.getIndustry())
                && ALL.equals(row.getIndustryRole())
                && row.getEffectiveFrom() != null
                && !row.getEffectiveFrom().isAfter(LocalDate.now())) {
            long otherGlobal = benchmarkMapper.selectCount(new LambdaQueryWrapper<PresaleBenchmark>()
                    .eq(PresaleBenchmark::getIndustry, ALL)
                    .eq(PresaleBenchmark::getIndustryRole, ALL)
                    .eq(PresaleBenchmark::getEnabled, true)
                    .le(PresaleBenchmark::getEffectiveFrom, LocalDate.now())
                    .ne(PresaleBenchmark::getId, id));
            if (otherGlobal == 0) {
                throw new BizException(400, "最后一条有效全局通用基准不可停用");
            }
        }
        String before = json(row);
        row.setEnabled(request.getEnabled());
        row.setRemark(trimToNull(request.getRemark()));
        row.setUpdatedAt(LocalDateTime.now());
        benchmarkMapper.updateById(row);
        writeHistory(row, Boolean.TRUE.equals(request.getEnabled()) ? "UPDATE" : "DISABLE",
                before, row, operator, request.getRemark());
        return row;
    }

    private void validate(PresaleBenchmarkSaveRequest request, Long currentId) {
        String industry = normalizeKey(request.getIndustry());
        String role = normalizeKey(request.getIndustryRole());
        String confidence = request.getConfidenceLevel().trim().toUpperCase(Locale.ROOT);
        if (!CONFIDENCE_LEVELS.contains(confidence)) {
            throw new BizException(400, "confidenceLevel 必须为 HIGH、MEDIUM 或 LOW");
        }
        if (request.getSampleSize() < 0) {
            throw new BizException(400, "sampleSize 不能小于 0");
        }
        requireTop1AtLeastAverage("综合分", request.getTop1Overall(), request.getAvgOverall());
        requireTop1AtLeastAverage("提及分", request.getTop1Mention(), request.getAvgMention());
        requireTop1AtLeastAverage("排名分", request.getTop1Ranking(), request.getAvgRanking());
        requireTop1AtLeastAverage("情感分", request.getTop1Sentiment(), request.getAvgSentiment());
        requireTop1AtLeastAverage("覆盖分", request.getTop1Coverage(), request.getAvgCoverage());
        LambdaQueryWrapper<PresaleBenchmark> duplicate = new LambdaQueryWrapper<PresaleBenchmark>()
                .eq(PresaleBenchmark::getIndustry, industry)
                .eq(PresaleBenchmark::getIndustryRole, role)
                .eq(PresaleBenchmark::getEffectiveFrom, request.getEffectiveFrom());
        if (currentId != null) {
            duplicate.ne(PresaleBenchmark::getId, currentId);
        }
        if (benchmarkMapper.selectCount(duplicate) > 0) {
            throw new BizException(400, "同一行业、角色和生效日期的基准已存在");
        }
    }

    private void requireTop1AtLeastAverage(String label, BigDecimal top1, BigDecimal average) {
        if (top1.compareTo(average) < 0) {
            throw new BizException(400, label + "的 Top1 不得低于行业平均值");
        }
    }

    private void apply(PresaleBenchmark row, PresaleBenchmarkSaveRequest request) {
        row.setIndustry(normalizeKey(request.getIndustry()));
        row.setIndustryRole(normalizeKey(request.getIndustryRole()));
        row.setAvgOverall(request.getAvgOverall());
        row.setAvgMention(request.getAvgMention());
        row.setAvgRanking(request.getAvgRanking());
        row.setAvgSentiment(request.getAvgSentiment());
        row.setAvgCoverage(request.getAvgCoverage());
        row.setTop1Overall(request.getTop1Overall());
        row.setTop1Mention(request.getTop1Mention());
        row.setTop1Ranking(request.getTop1Ranking());
        row.setTop1Sentiment(request.getTop1Sentiment());
        row.setTop1Coverage(request.getTop1Coverage());
        row.setTop10Score(request.getTop10Score());
        row.setConfidenceLevel(request.getConfidenceLevel().trim().toUpperCase(Locale.ROOT));
        row.setSampleSize(request.getSampleSize());
        row.setEffectiveFrom(request.getEffectiveFrom());
        row.setEnabled(request.getEnabled());
        row.setRemark(trimToNull(request.getRemark()));
    }

    private void writeHistory(PresaleBenchmark row, String operation, String beforeJson,
                              PresaleBenchmark after, SysUser operator, String remark) {
        PresaleBenchmarkHistory history = new PresaleBenchmarkHistory();
        history.setBenchmarkId(row.getId());
        history.setIndustry(row.getIndustry());
        history.setIndustryRole(row.getIndustryRole());
        history.setOperation(operation);
        history.setBeforeSnapshot(beforeJson);
        history.setAfterSnapshot(json(after));
        history.setOperatorId(operator.getId());
        history.setOperatorName(StringUtils.hasText(operator.getDisplayName())
                ? operator.getDisplayName() : operator.getUsername());
        history.setRemark(trimToNull(remark));
        history.setCreatedAt(LocalDateTime.now());
        historyMapper.insert(history);
    }

    private PresaleBenchmark require(Long id) {
        PresaleBenchmark row = benchmarkMapper.selectById(id);
        if (row == null) {
            throw new BizException(404, "行业基准不存在");
        }
        return row;
    }

    private SysUser ensureManagerRole() {
        SysUser user = currentUserService.requireCurrentUser();
        String role = user.getRole() == null ? "" : user.getRole().trim().toLowerCase(Locale.ROOT);
        if (!MANAGE_ROLES.contains(role)) {
            throw new BizException(403, "No permission to manage presale benchmarks");
        }
        return user;
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String json(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BizException(500, "行业基准审计快照序列化失败");
        }
    }
}
