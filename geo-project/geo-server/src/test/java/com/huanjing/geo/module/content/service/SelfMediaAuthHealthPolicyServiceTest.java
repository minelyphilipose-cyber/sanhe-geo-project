package com.huanjing.geo.module.content.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.dto.SelfMediaAuthHealthPolicyUpdateRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class SelfMediaAuthHealthPolicyServiceTest {

    @Test
    void updateRejectsRecipientRoleThatCannotReceiveSystemAlerts() {
        SelfMediaAuthHealthPolicyService service = new SelfMediaAuthHealthPolicyService(null, null, null, null);
        SelfMediaAuthHealthPolicyUpdateRequest request = new SelfMediaAuthHealthPolicyUpdateRequest(
                true, 30, 7, 30, "declared_then_reference", true,
                "operator", 1, "test invalid role"
        );

        assertThrows(BizException.class, () -> service.update("douyin", request));
    }
}
