package com.huanjing.geo.module.extension;

public final class ExtensionErrorCodes {

    public static final int EXTENSION_BAD_REQUEST = 70001;
    public static final int EXTENSION_UNAUTHORIZED = 70002;
    public static final int EXTENSION_DENIED = 70003;
    public static final int EXTENSION_NOT_FOUND = 70004;
    public static final int EXTENSION_VERSION_TOO_LOW = 70005;
    public static final int FILL_TOKEN_INVALID = 70006;
    public static final int FILL_TOKEN_USED_OR_EXPIRED = 70007;
    public static final int BIND_CODE_INVALID = 70008;
    public static final int BIND_RATE_LIMIT_EXCEEDED = 70009;
    public static final int EXTENSION_INTERNAL_ERROR = 70010;

    private ExtensionErrorCodes() {
    }
}
