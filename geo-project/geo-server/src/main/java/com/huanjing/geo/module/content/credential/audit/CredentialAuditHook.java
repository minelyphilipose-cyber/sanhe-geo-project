package com.huanjing.geo.module.content.credential.audit;

import com.huanjing.geo.module.content.credential.dto.CookieCredentialMeta;

public interface CredentialAuditHook {

    void onCredentialStored(CookieCredentialMeta meta);

    void onCredentialDecrypted(CookieCredentialMeta meta, Long operatorId);

    void onCredentialDestroyed(Long selfMediaAccountId, Long operatorId, int affectedRows);

    void onCredentialAccessDenied(
            Long selfMediaAccountId,
            Long expectedBrandId,
            Long actualBrandId,
            Long operatorId,
            String reason
    );
}
