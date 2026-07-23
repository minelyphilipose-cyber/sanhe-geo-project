package com.huanjing.geo.module.mobiledashboard.controller;

import com.huanjing.geo.module.mobiledashboard.service.MobileDashboardAggregateService;
import com.huanjing.geo.module.mobiledashboard.service.MobileDashboardShareService;
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
        when(shareService.resolveShareCardTitle("MAHEKSKZ")).thenReturn("华为鸿蒙智家");
        MobileDashboardPublicController controller = new MobileDashboardPublicController(
                shareService,
                mock(MobileDashboardAggregateService.class)
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(get("/api/public/mobile-dashboard/share-card-meta")
                        .header("X-Mobile-Dashboard-Share-Code", "MAHEKSKZ"))
                .andExpect(status().isNoContent())
                .andExpect(header().string(
                        "X-Mobile-Dashboard-Share-Title",
                        htmlEntities("华为鸿蒙智家")
                ));
    }

    private String htmlEntities(String value) {
        return value.codePoints()
                .mapToObj(codePoint -> "&#" + codePoint + ";")
                .collect(Collectors.joining());
    }
}
