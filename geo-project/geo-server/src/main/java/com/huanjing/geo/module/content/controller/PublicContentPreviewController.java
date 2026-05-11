package com.huanjing.geo.module.content.controller;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.authoritymedia.AuthorityMediaPreviewTokenService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

@RestController
@RequestMapping("/api/public/authority-media")
@RequiredArgsConstructor
public class PublicContentPreviewController {

    private final AuthorityMediaPreviewTokenService previewTokenService;

    @GetMapping(value = "/previews/{token}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> authorityMediaPreview(@PathVariable String token, HttpServletRequest request) {
        try {
            return noIndex(previewTokenService.renderPreview(token, clientIp(request), request.getHeader(HttpHeaders.USER_AGENT)));
        } catch (BizException ex) {
            int status = ex.getCode() == 410 ? 410 : 404;
            return ResponseEntity.status(status)
                    .contentType(MediaType.TEXT_HTML)
                    .header("X-Robots-Tag", "noindex, nofollow")
                    .body(errorHtml(status == 410 ? "预览链接已失效" : "预览链接不存在"));
        }
    }

    private ResponseEntity<String> noIndex(String html) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .header("X-Robots-Tag", "noindex, nofollow")
                .body(html);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String errorHtml(String message) {
        return """
                <!doctype html>
                <html lang="zh-CN">
                <head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>%s</title></head>
                <body style="margin:0;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;background:#f8fafc;color:#0f172a">
                  <main style="max-width:720px;margin:80px auto;padding:0 24px"><h1 style="font-size:24px">%s</h1></main>
                </body>
                </html>
                """.formatted(message, message);
    }
}
