package com.huanjing.geo.module.content.dto;

import java.util.List;

public record SelfMediaCookieStatusItemVO(
        Long articleId,
        Long brandId,
        List<SelfMediaCookieStatusAccountVO> accounts
) {
}
