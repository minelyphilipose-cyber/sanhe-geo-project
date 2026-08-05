package com.huanjing.geo.module.presale.generate.web;

import com.huanjing.geo.common.exception.BizException;

public class PresaleWebReadinessException extends BizException {
    public PresaleWebReadinessException(String message) {
        super(409, message);
    }
}
