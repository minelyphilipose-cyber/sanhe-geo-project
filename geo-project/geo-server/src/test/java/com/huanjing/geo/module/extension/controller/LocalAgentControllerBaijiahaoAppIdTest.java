package com.huanjing.geo.module.extension.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.content.config.SemiAutoPlatformProperties;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.mapper.BrowserEnvironmentAccountMapper;
import com.huanjing.geo.module.content.mapper.BrowserEnvironmentMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.content.service.SelfMediaPublishScheduleService;
import com.huanjing.geo.module.extension.service.LocalAgentSessionService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

class LocalAgentControllerBaijiahaoAppIdTest {

    private final LocalAgentController controller = new LocalAgentController(
            mock(LocalAgentSessionService.class),
            mock(SelfMediaPublishScheduleService.class),
            mock(SelfMediaAccountMapper.class),
            mock(BrowserEnvironmentMapper.class),
            mock(BrowserEnvironmentAccountMapper.class),
            mock(SemiAutoPlatformProperties.class),
            new ObjectMapper()
    );

    @Test
    void defaultWorksListUrlUsesBaijiahaoAppIdFromExtraJson() throws Exception {
        SelfMediaAccount account = new SelfMediaAccount();
        account.setPlatform("baijiahao");
        account.setPlatformAccountId("geo-baijiahao-990006013-5d42b6194aa240c");
        account.setExtraJson("{\"appId\":\"1867055852901021\"}");

        String url = defaultWorksListUrl(account);

        assertEquals("https://baijiahao.baidu.com/builder/rc/content"
                + "?currentPage=1&pageSize=10&search=&type=&collection=&app_id=1867055852901021&startDate=&endDate=", url);
    }

    @Test
    void defaultWorksListUrlRejectsNonNumericBaijiahaoPlatformAccountId() throws Exception {
        SelfMediaAccount account = new SelfMediaAccount();
        account.setPlatform("baijiahao");
        account.setPlatformAccountId("geo-baijiahao-990006013-5d42b6194aa240c");

        assertNull(defaultWorksListUrl(account));
    }

    private String defaultWorksListUrl(SelfMediaAccount account) throws Exception {
        Method method = LocalAgentController.class.getDeclaredMethod(
                "defaultWorksListUrl",
                String.class,
                SelfMediaAccount.class
        );
        method.setAccessible(true);
        return (String) method.invoke(controller, "baijiahao", account);
    }
}
