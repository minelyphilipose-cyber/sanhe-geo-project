package com.huanjing.geo.module.partner.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PartnerFeatureAccessGuardTest {

    private CurrentUserService currentUserService;
    private PartnerFeatureAccessGuard guard;

    @BeforeEach
    void setUp() {
        currentUserService = mock(CurrentUserService.class);
        guard = new PartnerFeatureAccessGuard(currentUserService);
    }

    @Test
    void rejectsCurrentPartnerUser() {
        SysUser partner = new SysUser();
        partner.setRole("partner");
        when(currentUserService.requireCurrentUser()).thenReturn(partner);
        when(currentUserService.isPartnerUser(partner)).thenReturn(true);

        BizException ex = assertThrows(
                BizException.class,
                () -> guard.ensureInternalDeliveryFeature("browser environment operations")
        );

        assertEquals(403, ex.getCode());
    }

    @Test
    void rejectsPartnerUserByOperatorId() {
        SysUser partnerStaff = new SysUser();
        partnerStaff.setId(100L);
        partnerStaff.setRole("partner_staff");
        when(currentUserService.requireById(100L)).thenReturn(partnerStaff);
        when(currentUserService.isPartnerUser(partnerStaff)).thenReturn(true);

        BizException ex = assertThrows(
                BizException.class,
                () -> guard.ensureInternalDeliveryUser(100L, "local agent operations")
        );

        assertEquals(403, ex.getCode());
    }

    @Test
    void allowsInternalUserByOperatorId() {
        SysUser internal = new SysUser();
        internal.setId(200L);
        internal.setRole("operator");
        when(currentUserService.requireById(200L)).thenReturn(internal);
        when(currentUserService.isPartnerUser(internal)).thenReturn(false);

        assertDoesNotThrow(() -> guard.ensureInternalDeliveryUser(200L, "local agent operations"));
    }
}
