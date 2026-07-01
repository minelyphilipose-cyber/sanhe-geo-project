package com.huanjing.geo.module.project.controller;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.service.ProjectSelfMediaScheduleService;
import com.huanjing.geo.module.partner.service.PartnerFeatureAccessGuard;
import com.huanjing.geo.module.partner.service.PartnerResponseSanitizer;
import com.huanjing.geo.module.project.service.KeywordGroupService;
import com.huanjing.geo.module.project.service.ProjectService;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProjectControllerPartnerAccessTest {

    private ProjectSelfMediaScheduleService projectSelfMediaScheduleService;
    private ProjectController controller;

    @BeforeEach
    void setUp() {
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        projectSelfMediaScheduleService = mock(ProjectSelfMediaScheduleService.class);
        controller = new ProjectController(
                mock(ProjectService.class),
                mock(KeywordGroupService.class),
                projectSelfMediaScheduleService,
                mock(PartnerResponseSanitizer.class),
                new PartnerFeatureAccessGuard(currentUserService)
        );

        SysUser partner = new SysUser();
        partner.setRole("partner");
        when(currentUserService.requireCurrentUser()).thenReturn(partner);
        when(currentUserService.isPartnerUser(partner)).thenReturn(true);
    }

    @Test
    void partnerCannotAccessSelfMediaScheduleConfig() {
        BizException ex = assertThrows(
                BizException.class,
                () -> controller.selfMediaScheduleConfig(1L)
        );

        assertEquals(403, ex.getCode());
        verifyNoInteractions(projectSelfMediaScheduleService);
    }
}
