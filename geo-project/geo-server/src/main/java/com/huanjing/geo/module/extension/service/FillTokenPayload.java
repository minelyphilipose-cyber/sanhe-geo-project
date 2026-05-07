package com.huanjing.geo.module.extension.service;

import com.huanjing.geo.common.exception.BizException;

import static com.huanjing.geo.module.extension.ExtensionErrorCodes.FILL_TOKEN_INVALID;

public record FillTokenPayload(int v, long aid, long bid, long op, long tid, long exp, long iat, String n) {

    public static final int CURRENT_VERSION = 1;

    public String canonicalString() {
        return v + "|" + aid + "|" + bid + "|" + op + "|" + tid + "|" + exp + "|" + iat + "|" + n;
    }

    public static FillTokenPayload parseCanonical(String value) {
        String[] parts = value == null ? new String[0] : value.split("\\|", -1);
        if (parts.length != 8) {
            throw new BizException(FILL_TOKEN_INVALID, "fill token payload invalid");
        }
        try {
            FillTokenPayload payload = new FillTokenPayload(
                    Integer.parseInt(parts[0]),
                    Long.parseLong(parts[1]),
                    Long.parseLong(parts[2]),
                    Long.parseLong(parts[3]),
                    Long.parseLong(parts[4]),
                    Long.parseLong(parts[5]),
                    Long.parseLong(parts[6]),
                    parts[7]
            );
            if (payload.v() != CURRENT_VERSION || payload.n() == null || payload.n().isBlank()) {
                throw new BizException(FILL_TOKEN_INVALID, "fill token payload invalid");
            }
            return payload;
        } catch (NumberFormatException ex) {
            throw new BizException(FILL_TOKEN_INVALID, "fill token payload invalid", ex);
        }
    }
}
