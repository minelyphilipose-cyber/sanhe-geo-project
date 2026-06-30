package com.huanjing.geo.module.content.service;

import com.huanjing.geo.module.content.entity.SpecialIndustryTemplateRoute;
import com.huanjing.geo.module.content.mapper.SpecialIndustryTemplateRouteMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpecialIndustryTemplateRouteServiceTest {

    private final SpecialIndustryTemplateRouteMapper routeMapper = mock(SpecialIndustryTemplateRouteMapper.class);
    private final SpecialIndustryTemplateRouteService service = new SpecialIndustryTemplateRouteService(routeMapper);

    @Test
    void specificPlatformAndIdentityRouteWinsOverGenericRoute() {
        SpecialIndustryTemplateRoute generic = route(null, null, "通用模板", 100);
        SpecialIndustryTemplateRoute personalToutiao = route("toutiao", "personal", "头条个人号模板", 100);
        when(routeMapper.selectList(any())).thenReturn(List.of(generic, personalToutiao));

        assertThat(service.resolveTemplateName("oral", "self_media", "toutiao", "personal"))
                .contains("头条个人号模板");
    }

    @Test
    void industrySpecificRouteWinsOverWildcardRoute() {
        SpecialIndustryTemplateRoute wildcard = route("toutiao", "personal", "通用头条模板", 100);
        wildcard.setIndustryCode("*");
        SpecialIndustryTemplateRoute oral = route("toutiao", "personal", "口腔头条模板", 10);
        oral.setIndustryCode("oral");
        when(routeMapper.selectList(any())).thenReturn(List.of(wildcard, oral));

        assertThat(service.resolveTemplateName("oral", "self_media", "toutiao", "personal"))
                .contains("口腔头条模板");
    }

    private SpecialIndustryTemplateRoute route(String subCode, String identity, String templateName, int priority) {
        SpecialIndustryTemplateRoute route = new SpecialIndustryTemplateRoute();
        route.setIndustryCode("*");
        route.setChannelGroupCode("self_media");
        route.setChannelSubCode(subCode);
        route.setAccountIdentity(identity);
        route.setTemplateName(templateName);
        route.setPriority(priority);
        route.setEnabled(true);
        return route;
    }
}
