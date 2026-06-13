package com.huanjing.geo.module.content.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.constant.SelfMediaPublishScheduleConstants;
import com.huanjing.geo.module.content.dto.SelfMediaPublishAutoScheduleRequest;
import com.huanjing.geo.module.content.dto.SelfMediaPublishScheduleCreateRequest;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaPublishScheduleMapper;
import com.huanjing.geo.module.content.service.adapter.SelfMediaPlatformScheduleAdapterRouter;
import com.huanjing.geo.module.content.vo.SelfMediaPublishAutoScheduleItemVO;
import com.huanjing.geo.module.content.vo.SelfMediaPublishAutoScheduleResponse;
import com.huanjing.geo.module.content.vo.SelfMediaPublishScheduleCreateResponse;
import com.huanjing.geo.module.customer.access.BrandAccessAction;
import com.huanjing.geo.module.customer.access.BrandAccessService;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SelfMediaPublishAutoScheduleService {
    private static final int ERROR_CODE = 70042;
    public static final String STRATEGY_PLATFORM_SPECIFIC = "platform_specific";

    private final BusinessCalendarService businessCalendarService;
    private final SelfMediaPublishScheduleService scheduleService;
    private final SelfMediaPublishScheduleMapper scheduleMapper;
    private final SelfMediaAccountMapper selfMediaAccountMapper;
    private final BrandMapper brandMapper;
    private final CompanyChannelQuotaService companyChannelQuotaService;
    private final BrandAccessService brandAccessService;
    private final CurrentUserService currentUserService;
    private final SelfMediaPlatformScheduleAdapterRouter scheduleAdapterRouter;

    public SelfMediaPublishAutoScheduleResponse preview(SelfMediaPublishAutoScheduleRequest request) {
        return plan(request, false);
    }

    public SelfMediaPublishAutoScheduleResponse create(SelfMediaPublishAutoScheduleRequest request) {
        return createInternal(request, null);
    }

    public SelfMediaPublishAutoScheduleResponse createSystem(SelfMediaPublishAutoScheduleRequest request, Long operatorId) {
        if (operatorId == null || operatorId <= 0) {
            throw new BizException(ERROR_CODE, "operatorId is required");
        }
        return createInternal(request, operatorId);
    }

    private SelfMediaPublishAutoScheduleResponse createInternal(SelfMediaPublishAutoScheduleRequest request, Long operatorId) {
        SelfMediaPublishAutoScheduleResponse response = operatorId == null ? plan(request, false) : planSystem(request, false);
        response.setCreated(true);
        for (SelfMediaPublishAutoScheduleItemVO item : response.getPlannedItems()) {
            if (!"planned".equals(item.getStatus())) {
                continue;
            }
            SelfMediaPublishScheduleCreateRequest createRequest = new SelfMediaPublishScheduleCreateRequest();
            createRequest.setBrandId(request.getBrandId());
            createRequest.setArticleIds(List.of(item.getArticleId()));
            createRequest.setSelfMediaAccountIds(List.of(item.getSelfMediaAccountId()));
            createRequest.setWindowStart(item.getPlannedPublishAt());
            createRequest.setWindowEnd(item.getPlannedPublishAt());
            createRequest.setScheduleStrategy(resolveItemStrategy(request.getScheduleStrategy(), item.getPlatform()));
            createRequest.setMinIntervalMinutes(1);
            String idempotencyKey = autoIdempotencyKey(request.getBrandId(), item, createRequest.getScheduleStrategy());
            SelfMediaPublishScheduleCreateResponse created = operatorId == null
                    ? scheduleService.createSchedules(createRequest, idempotencyKey)
                    : scheduleService.createSystemSchedules(createRequest, idempotencyKey, operatorId);
            response.getCreatedSchedules().addAll(created.getCreatedSchedules());
            response.getExistingSchedules().addAll(created.getExistingSchedules());
            response.getRejectedItems().addAll(created.getRejectedItems());
        }
        return response;
    }

    private SelfMediaPublishAutoScheduleResponse plan(SelfMediaPublishAutoScheduleRequest request, boolean created) {
        ValidatedAutoRequest validated = validate(request, true);
        return planValidated(validated, created);
    }

    private SelfMediaPublishAutoScheduleResponse planSystem(SelfMediaPublishAutoScheduleRequest request, boolean created) {
        ValidatedAutoRequest validated = validate(request, false);
        return planValidated(validated, created);
    }

    private SelfMediaPublishAutoScheduleResponse planValidated(ValidatedAutoRequest validated, boolean created) {
        SelfMediaPublishAutoScheduleResponse response = new SelfMediaPublishAutoScheduleResponse();
        response.setBrandId(validated.brandId());
        response.setTargetMonth(validated.targetMonth().toString());
        response.setScheduleStrategy(validated.strategy());
        response.setCreated(created);
        response.setRequestedCount(validated.articleIds().size() * validated.accountIds().size());

        List<PairCandidate> candidates = resolveCandidates(validated, response);
        List<PairCandidate> accepted = applyQuotaGuards(validated, candidates, response);
        List<BusinessCalendarService.PublishSlot> slots = businessCalendarService.selectEvenly(
                validated.targetMonth(),
                accepted.size(),
                validated.includeAdjustedWorkdays()
        );
        for (int i = 0; i < accepted.size(); i++) {
            PairCandidate candidate = accepted.get(i);
            BusinessCalendarService.PublishSlot slot = slots.get(i);
            response.getPlannedItems().add(plannedItem(candidate, slot));
        }
        response.setPlannedCount((int) response.getPlannedItems().stream()
                .filter(item -> "planned".equals(item.getStatus()))
                .count());
        response.setRejectedCount((int) response.getPlannedItems().stream()
                .filter(item -> "rejected".equals(item.getStatus()))
                .count());
        return response;
    }

    private List<PairCandidate> resolveCandidates(ValidatedAutoRequest request,
                                                  SelfMediaPublishAutoScheduleResponse response) {
        Map<Long, SelfMediaAccount> accounts = new LinkedHashMap<>();
        for (Long accountId : request.accountIds()) {
            accounts.put(accountId, selfMediaAccountMapper.selectById(accountId));
        }
        List<PairCandidate> candidates = new ArrayList<>();
        for (Long articleId : request.articleIds()) {
            for (Long accountId : request.accountIds()) {
                SelfMediaAccount account = accounts.get(accountId);
                if (account == null) {
                    response.getPlannedItems().add(rejectedItem(articleId, accountId, null,
                            "SELF_MEDIA_ACCOUNT_NOT_FOUND", "自媒体账号不存在"));
                    continue;
                }
                String platform = normalize(account.getPlatform());
                if (!StringUtils.hasText(platform)) {
                    response.getPlannedItems().add(rejectedItem(articleId, accountId, null,
                            "SELF_MEDIA_PLATFORM_MISSING", "自媒体账号缺少平台"));
                    continue;
                }
                candidates.add(new PairCandidate(articleId, accountId, platform));
            }
        }
        return candidates;
    }

    private List<PairCandidate> applyQuotaGuards(ValidatedAutoRequest request,
                                                 List<PairCandidate> candidates,
                                                 SelfMediaPublishAutoScheduleResponse response) {
        Map<String, Integer> remainingByPlatform = loadRemainingQuota(request);
        List<PairCandidate> accepted = new ArrayList<>();
        for (PairCandidate candidate : candidates) {
            int remaining = remainingByPlatform.getOrDefault(candidate.platform(), 0);
            if (remaining <= 0) {
                response.getPlannedItems().add(rejectedItem(
                        candidate.articleId(),
                        candidate.accountId(),
                        candidate.platform(),
                        "CHANNEL_QUOTA_EXHAUSTED",
                        "该客户本月自媒体平台额度不足，已停止自动排期"
                ));
                continue;
            }
            remainingByPlatform.put(candidate.platform(), remaining - 1);
            accepted.add(candidate);
        }
        return accepted;
    }

    private Map<String, Integer> loadRemainingQuota(ValidatedAutoRequest request) {
        LocalDateTime periodStart = request.targetMonth().atDay(1).atStartOfDay();
        LocalDateTime periodEnd = request.targetMonth().plusMonths(1).atDay(1).atStartOfDay();
        Map<String, Integer> remainingByPlatform = new LinkedHashMap<>();
        request.platforms().forEach(platform -> {
            CompanyChannelQuotaService.DistributionQuotaView quota =
                    companyChannelQuotaService.selfMediaDistributionQuota(request.companyId(), platform);
            long activeSchedules = scheduleMapper.countActiveByBrandPlatformAndPeriod(
                    request.brandId(),
                    platform,
                    periodStart,
                    periodEnd,
                    new ArrayList<>(SelfMediaPublishScheduleConstants.ACTIVE_STATUSES)
            );
            int occupied = Math.max(quota.usedCount(), Math.toIntExact(Math.min(activeSchedules, Integer.MAX_VALUE)));
            int remaining = quota.quotaLimit() - occupied;
            remainingByPlatform.put(platform, Math.max(0, remaining));
        });
        return remainingByPlatform;
    }

    private SelfMediaPublishAutoScheduleItemVO plannedItem(PairCandidate candidate,
                                                           BusinessCalendarService.PublishSlot slot) {
        SelfMediaPublishAutoScheduleItemVO item = new SelfMediaPublishAutoScheduleItemVO();
        item.setArticleId(candidate.articleId());
        item.setSelfMediaAccountId(candidate.accountId());
        item.setPlatform(candidate.platform());
        item.setCalendarDate(slot.date());
        item.setPlannedPublishAt(slot.plannedAt());
        item.setWindowName(slot.windowName());
        item.setWindowStart(slot.windowStart());
        item.setWindowEnd(slot.windowEnd());
        item.setDayType(slot.dayType());
        item.setDayName(slot.dayName());
        item.setWeek(slot.week());
        item.setAdjustedWorkday(slot.adjustedWorkday());
        item.setStatus("planned");
        return item;
    }

    private SelfMediaPublishAutoScheduleItemVO rejectedItem(Long articleId,
                                                            Long accountId,
                                                            String platform,
                                                            String code,
                                                            String message) {
        SelfMediaPublishAutoScheduleItemVO item = new SelfMediaPublishAutoScheduleItemVO();
        item.setArticleId(articleId);
        item.setSelfMediaAccountId(accountId);
        item.setPlatform(platform);
        item.setStatus("rejected");
        item.setRejectionCode(code);
        item.setRejectionMessage(message);
        return item;
    }

    private ValidatedAutoRequest validate(SelfMediaPublishAutoScheduleRequest request, boolean requireAccess) {
        if (request == null) {
            throw new BizException(ERROR_CODE, "request is required");
        }
        Long brandId = request.getBrandId();
        if (brandId == null || brandId <= 0) {
            throw new BizException(ERROR_CODE, "brandId is required");
        }
        if (requireAccess) {
            SysUser operator = currentUserService.requireCurrentUser();
            brandAccessService.requireBrandAccess(brandId, operator.getId(), BrandAccessAction.OPERATE);
        }

        Brand brand = brandMapper.selectById(brandId);
        if (brand == null || brand.getCompanyId() == null || brand.getCompanyId() <= 0) {
            throw new BizException(ERROR_CODE, "品牌未关联有效客户，不能自动排期");
        }
        if (!StringUtils.hasText(request.getTargetMonth())) {
            throw new BizException(ERROR_CODE, "targetMonth is required");
        }
        YearMonth month;
        try {
            month = YearMonth.parse(request.getTargetMonth().trim());
        } catch (Exception ex) {
            throw new BizException(ERROR_CODE, "targetMonth must use yyyy-MM format");
        }
        List<Long> articleIds = distinctPositive(request.getArticleIds(), "articleIds");
        List<Long> accountIds = distinctPositive(request.getSelfMediaAccountIds(), "selfMediaAccountIds");
        List<String> platforms = accountIds.stream()
                .map(selfMediaAccountMapper::selectById)
                .map(account -> account == null ? null : normalize(account.getPlatform()))
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        return new ValidatedAutoRequest(
                brandId,
                brand.getCompanyId(),
                articleIds,
                accountIds,
                platforms,
                month,
                normalizeStrategyMode(request.getScheduleStrategy()),
                Boolean.TRUE.equals(request.getIncludeAdjustedWorkdays())
        );
    }

    private List<Long> distinctPositive(List<Long> values, String fieldName) {
        if (values == null || values.isEmpty()) {
            throw new BizException(ERROR_CODE, fieldName + " must not be empty");
        }
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        for (Long value : values) {
            if (value == null || value <= 0) {
                throw new BizException(ERROR_CODE, fieldName + " contains invalid id");
            }
            result.add(value);
        }
        return new ArrayList<>(result);
    }

    private String normalizeStrategyMode(String value) {
        return StringUtils.hasText(value)
                ? value.trim()
                : STRATEGY_PLATFORM_SPECIFIC;
    }

    private String resolveItemStrategy(String requestedStrategy, String platform) {
        String normalized = normalizeStrategyMode(requestedStrategy);
        if (!STRATEGY_PLATFORM_SPECIFIC.equals(normalized)) {
            return normalized;
        }
        return scheduleAdapterRouter.contract(platform)
                .map(contract -> contract.supportsBackendDelayedPublish()
                        ? SelfMediaPublishScheduleConstants.STRATEGY_BACKEND_DELAYED_PUBLISH
                        : SelfMediaPublishScheduleConstants.STRATEGY_PLATFORM_SCHEDULE)
                .orElse(SelfMediaPublishScheduleConstants.STRATEGY_PLATFORM_SCHEDULE);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String autoIdempotencyKey(Long brandId, SelfMediaPublishAutoScheduleItemVO item, String strategy) {
        String payload = brandId + "|" + item.getArticleId() + "|" + item.getSelfMediaAccountId()
                + "|" + item.getPlannedPublishAt() + "|" + strategy;
        return "auto-" + sha256(payload).substring(0, 32);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private record ValidatedAutoRequest(Long brandId,
                                        Long companyId,
                                        List<Long> articleIds,
                                        List<Long> accountIds,
                                        List<String> platforms,
                                        YearMonth targetMonth,
                                        String strategy,
                                        boolean includeAdjustedWorkdays) {
    }

    private record PairCandidate(Long articleId, Long accountId, String platform) {
    }
}
