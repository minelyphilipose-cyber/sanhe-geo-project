package com.huanjing.geo.module.presale.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.presale.dto.request.PresalePage03MarketConfigUpdateRequest;
import com.huanjing.geo.module.presale.persist.entity.PresalePage03MarketConfig;
import com.huanjing.geo.module.presale.persist.mapper.PresalePage03MarketConfigMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.ActivityLogService;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PresalePage03MarketConfigService {

    private static final long SINGLETON_ID = 1L;
    private static final Set<String> MANAGE_ROLES = Set.of("delivery_manager", "manager", "super_admin");

    private final PresalePage03MarketConfigMapper mapper;
    private final CurrentUserService currentUserService;
    private final ActivityLogService activityLogService;

    public PresalePage03MarketConfig getConfig() {
        PresalePage03MarketConfig config = mapper.selectById(SINGLETON_ID);
        if (config != null) {
            return config;
        }
        PresalePage03MarketConfig defaults = defaultConfig();
        mapper.insert(defaults);
        return defaults;
    }

    public PresalePage03MarketConfig getConfigForAdmin() {
        ensureManagerRole();
        return getConfig();
    }

    public PresalePage03MarketConfig update(PresalePage03MarketConfigUpdateRequest req) {
        SysUser operator = ensureManagerRole();
        PresalePage03MarketConfig entity = getConfig();
        Map<String, Object> before = snapshot(entity);
        fill(entity, req);
        mapper.updateById(entity);
        activityLogService.logAction(
                operator.getId(),
                "presale.page03_config.update",
                "presale_page03_market_config",
                entity.getId(),
                before,
                snapshot(entity),
                null
        );
        return entity;
    }

    private SysUser ensureManagerRole() {
        SysUser user = currentUserService.requireCurrentUser();
        String role = user.getRole() == null ? "" : user.getRole();
        if (!MANAGE_ROLES.contains(role)) {
            throw new BizException(403, "No permission to manage Page03 config");
        }
        return user;
    }

    private PresalePage03MarketConfig defaultConfig() {
        PresalePage03MarketConfig out = new PresalePage03MarketConfig();
        out.setId(SINGLETON_ID);
        out.setMarketLabel("AI 搜索流量总览");
        out.setMarketSource("来源：行业公开数据综合估算");
        out.setAppMonthlyActiveValue("8.3");
        out.setAppMonthlyActiveUnit("亿");
        out.setDailyActiveUsersValue("7.2");
        out.setDailyActiveUsersUnit("亿");
        out.setDailyQuestionTotalValue("12");
        out.setDailyQuestionTotalUnit("亿次");
        out.setDoubaoMonthlyUsageValue("28");
        out.setDoubaoMonthlyUsageUnit("次");
        out.setPlatform1Name("豆包");
        out.setPlatform1Value("5.8亿/月活");
        out.setPlatform2Name("千问");
        out.setPlatform2Value("4.2亿/月活");
        out.setPlatform3Name("DeepSeek");
        out.setPlatform3Value("3.1亿/月活");
        out.setPlatformSuffix("元宝 / Kimi 等");
        out.setPage03DataSource("公开口径综合测算");
        out.setFootnote("注：以上数据基于行业公开数据与主流AI平台问答量综合估算，存在±20%合理浮动区间，仅作量级参考，不构成精确市场断言。");
        out.setQuestionCount(3);
        return out;
    }

    private void fill(PresalePage03MarketConfig entity, PresalePage03MarketConfigUpdateRequest req) {
        entity.setMarketLabel(req.getMarketLabel().trim());
        entity.setMarketSource(req.getMarketSource().trim());
        entity.setAppMonthlyActiveValue(req.getAppMonthlyActiveValue().trim());
        entity.setAppMonthlyActiveUnit(req.getAppMonthlyActiveUnit().trim());
        entity.setDailyActiveUsersValue(req.getDailyActiveUsersValue().trim());
        entity.setDailyActiveUsersUnit(req.getDailyActiveUsersUnit().trim());
        entity.setDailyQuestionTotalValue(req.getDailyQuestionTotalValue().trim());
        entity.setDailyQuestionTotalUnit(req.getDailyQuestionTotalUnit().trim());
        entity.setDoubaoMonthlyUsageValue(req.getDoubaoMonthlyUsageValue().trim());
        entity.setDoubaoMonthlyUsageUnit(req.getDoubaoMonthlyUsageUnit().trim());
        entity.setPlatform1Name(req.getPlatform1Name().trim());
        entity.setPlatform1Value(req.getPlatform1Value().trim());
        entity.setPlatform2Name(req.getPlatform2Name().trim());
        entity.setPlatform2Value(req.getPlatform2Value().trim());
        entity.setPlatform3Name(req.getPlatform3Name().trim());
        entity.setPlatform3Value(req.getPlatform3Value().trim());
        entity.setPlatformSuffix(req.getPlatformSuffix().trim());
        entity.setPage03DataSource(req.getPage03DataSource().trim());
        entity.setFootnote(req.getFootnote().trim());
        entity.setQuestionCount(req.getQuestionCount());
    }

    private Map<String, Object> snapshot(PresalePage03MarketConfig entity) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("marketLabel", entity.getMarketLabel());
        out.put("marketSource", entity.getMarketSource());
        out.put("dailyQuestionTotal", entity.getDailyQuestionTotalValue() + entity.getDailyQuestionTotalUnit());
        out.put("page03DataSource", entity.getPage03DataSource());
        out.put("footnote", entity.getFootnote());
        out.put("questionCount", entity.getQuestionCount());
        return out;
    }
}
