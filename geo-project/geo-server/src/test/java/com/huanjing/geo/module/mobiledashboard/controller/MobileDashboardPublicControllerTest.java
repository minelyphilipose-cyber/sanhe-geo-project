package com.huanjing.geo.module.mobiledashboard.controller;

import com.huanjing.geo.module.mobiledashboard.service.MobileDashboardAggregateService;
import com.huanjing.geo.module.mobiledashboard.service.MobileDashboardShareService;
import com.huanjing.geo.module.mobiledashboard.service.MobileDashboardWechatShareService;
import com.huanjing.geo.module.mobiledashboard.dto.MobileDashboardWechatConfigVO;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.stream.Collectors;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MobileDashboardPublicControllerTest {

    @Test
    void shareCardMetaReturnsAsciiHtmlEntitiesForNginxInjection() throws Exception {
        MobileDashboardShareService shareService = mock(MobileDashboardShareService.class);
        MobileDashboardWechatShareService wechatShareService = mock(MobileDashboardWechatShareService.class);
        when(wechatShareService.shareCardContent("MAHEKSKZ")).thenReturn(
                new MobileDashboardWechatConfigVO.ShareContent(
                        "华为鸿蒙智家",
                        "看板说明",
                        "https://www.huanjingaigeo.com/m/MAHEKSKZ",
                        "https://www.huanjingaigeo.com/share.png"
                )
        );
        MobileDashboardPublicController controller = new MobileDashboardPublicController(
                shareService,
                mock(MobileDashboardAggregateService.class),
                wechatShareService
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(get("/api/public/mobile-dashboard/share-card-meta")
                        .header("X-Mobile-Dashboard-Share-Code", "MAHEKSKZ"))
                .andExpect(status().isNoContent())
                .andExpect(header().string(
                        "X-Mobile-Dashboard-Share-Title",
                        htmlEntities("华为鸿蒙智家")
                ))
                .andExpect(header().string(
                        "X-Mobile-Dashboard-Share-Description",
                        htmlEntities("看板说明")
                ))
                .andExpect(header().string(
                        "X-Mobile-Dashboard-Share-Image",
                        htmlEntities("https://www.huanjingaigeo.com/share.png")
                ))
                .andExpect(header().string(
                        "X-Mobile-Dashboard-Share-Url",
                        htmlEntities("https://www.huanjingaigeo.com/m/MAHEKSKZ")
                ));
    }

    private String htmlEntities(String value) {
        return value.codePoints()
                .mapToObj(codePoint -> "&#" + codePoint + ";")
                .collect(Collectors.joining());
    }
}
