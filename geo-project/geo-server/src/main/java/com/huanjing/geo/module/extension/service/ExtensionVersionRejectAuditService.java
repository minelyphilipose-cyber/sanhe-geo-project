package com.huanjing.geo.module.extension.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.audit.AuditMode;
import com.huanjing.geo.module.audit.AuditOperation;
import org.springframework.stereotype.Service;

@Service
public class ExtensionVersionRejectAuditService {

    @AuditOperation(value = "EXTENSION_VERSION_REJECT", mode = AuditMode.SYNC, sensitive = false)
    public void reject(int code, String message, Throwable cause) {
        reject(code, message, 200, null, cause);
    }

    @AuditOperation(value = "EXTENSION_VERSION_REJECT", mode = AuditMode.SYNC, sensitive = false)
    public void reject(int code, String message, int httpStatus, Object data, Throwable cause) {
        if (cause == null) {
            throw new BizException(code, message, httpStatus, data);
        }
        throw new BizException(code, message, httpStatus, data, cause);
    }
}
