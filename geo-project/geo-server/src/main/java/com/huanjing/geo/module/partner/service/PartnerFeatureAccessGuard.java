package com.huanjing.geo.module.partner.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PartnerFeatureAccessGuard {

    private final CurrentUserService currentUserService;

    public void ensureInternalDeliveryFeature(String featureName) {
        SysUser user = currentUserService.requireCurrentUser();
        ensureInternalDeliveryUser(user, featureName);
    }

    public void ensureInternalDeliveryUser(Long userId, String featureName) {
        ensureInternalDeliveryUser(currentUserService.requireById(userId), featureName);
    }

    private void ensureInternalDeliveryUser(SysUser user, String featureName) {
        if (currentUserService.isPartnerUser(user)) {
            throw new BizException(403, "Partners cannot access " + featureName);
        }
    }
}
