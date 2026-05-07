package com.huanjing.geo.module.content.credential.audit;

import com.huanjing.geo.module.content.credential.dto.CookieCredentialMeta;

public class NoopCredentialAuditHook implements CredentialAuditHook {

    @Override
    public void onCredentialStored(CookieCredentialMeta meta) {
    }

    @Override
    public void onCredentialDecrypted(CookieCredentialMeta meta, Long operatorId) {
    }

    @Override
    public void onCredentialDestroyed(Long selfMediaAccountId, Long operatorId, int affectedRows) {
    }

    @Override
    public void onCredentialAccessDenied(
            Long selfMediaAccountId,
            Long expectedBrandId,
            Long actualBrandId,
            Long operatorId,
            String reason
    ) {
    }
}
