package com.huanjing.geo.module.extension.dto;

import java.time.LocalDateTime;

public record ExtensionBindResponse(String token, LocalDateTime expiresAt, Long sessionId) {
}
