package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.module.content.constant.SelfMediaAccountIdentity;
import com.huanjing.geo.module.content.entity.SpecialIndustryTemplateRoute;
import com.huanjing.geo.module.content.mapper.SpecialIndustryTemplateRouteMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SpecialIndustryTemplateRouteService {

    private static final String ANY_INDUSTRY = "*";

    private final SpecialIndustryTemplateRouteMapper routeMapper;

    public Optional<String> resolveTemplateName(String industryCode,
                                                String channelGroupCode,
                                                String channelSubCode,
                                                String accountIdentity) {
        String group = trimToNull(channelGroupCode);
        if (group == null) {
            return Optional.empty();
        }
        String sub = trimToNull(channelSubCode);
        String identity = SelfMediaAccountIdentity.normalize(accountIdentity, SelfMediaAccountIdentity.ENTERPRISE);
        List<SpecialIndustryTemplateRoute> routes = routeMapper.selectList(
                new LambdaQueryWrapper<SpecialIndustryTemplateRoute>()
                        .eq(SpecialIndustryTemplateRoute::getEnabled, true)
                        .eq(SpecialIndustryTemplateRoute::getChannelGroupCode, group)
                        .and(wrapper -> wrapper
                                .isNull(SpecialIndustryTemplateRoute::getChannelSubCode)
                                .or()
                                .eq(sub != null, SpecialIndustryTemplateRoute::getChannelSubCode, sub))
                        .and(wrapper -> wrapper
                                .eq(SpecialIndustryTemplateRoute::getAccountIdentity, identity)
                                .or()
                                .isNull(SpecialIndustryTemplateRoute::getAccountIdentity))
                        .and(wrapper -> wrapper
                                .eq(SpecialIndustryTemplateRoute::getIndustryCode, ANY_INDUSTRY)
                                .or()
                                .eq(StringUtils.hasText(industryCode), SpecialIndustryTemplateRoute::getIndustryCode, industryCode))
        );
        return routes.stream()
                .filter(route -> matchesIndustry(route, industryCode))
                .filter(route -> matchesSubCode(route, sub))
                .filter(route -> matchesIdentity(route, identity))
                .max(Comparator
                        .comparingInt((SpecialIndustryTemplateRoute route) -> routeScore(route, industryCode, sub, identity))
                        .thenComparing(route -> route.getPriority() == null ? 0 : route.getPriority())
                        .thenComparing(route -> route.getId() == null ? 0L : route.getId()))
                .map(SpecialIndustryTemplateRoute::getTemplateName)
                .filter(StringUtils::hasText);
    }

    private boolean matchesIndustry(SpecialIndustryTemplateRoute route, String industryCode) {
        String routeIndustry = trimToNull(route.getIndustryCode());
        return ANY_INDUSTRY.equals(routeIndustry)
                || (StringUtils.hasText(industryCode) && industryCode.equals(routeIndustry));
    }

    private boolean matchesSubCode(SpecialIndustryTemplateRoute route, String subCode) {
        String routeSub = trimToNull(route.getChannelSubCode());
        return routeSub == null || (subCode != null && subCode.equals(routeSub));
    }

    private boolean matchesIdentity(SpecialIndustryTemplateRoute route, String identity) {
        String routeIdentity = trimToNull(route.getAccountIdentity());
        return routeIdentity == null || routeIdentity.equals(identity);
    }

    private int routeScore(SpecialIndustryTemplateRoute route, String industryCode, String subCode, String identity) {
        int score = 0;
        if (StringUtils.hasText(industryCode) && industryCode.equals(trimToNull(route.getIndustryCode()))) {
            score += 100;
        }
        if (subCode != null && subCode.equals(trimToNull(route.getChannelSubCode()))) {
            score += 10;
        }
        if (identity.equals(trimToNull(route.getAccountIdentity()))) {
            score += 5;
        }
        return score;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
