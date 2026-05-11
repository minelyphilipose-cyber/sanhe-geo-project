package com.huanjing.geo.module.content.authoritymedia;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.entity.AuthorityMediaOrder;
import com.huanjing.geo.module.content.entity.AuthorityMediaPreviewToken;
import com.huanjing.geo.module.content.mapper.AuthorityMediaOrderMapper;
import com.huanjing.geo.module.content.mapper.AuthorityMediaPreviewTokenMapper;
import com.huanjing.geo.module.content.service.ContentArticleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthorityMediaPreviewTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder TOKEN_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Set<Integer> INVALID_REMOTE_STATUSES = Set.of(-2, -1);

    private final AuthorityMediaPreviewTokenMapper tokenMapper;
    private final AuthorityMediaOrderMapper orderMapper;
    private final ContentArticleService contentArticleService;
    private final MeititejiaProperties properties;

    @Transactional
    public String issuePreviewUrl(AuthorityMediaOrder order, ArticleDraft article, String previewUrlBase) {
        if (order == null || order.getId() == null) {
            throw new BizException(500, "authority media order is required before preview token issue");
        }
        if (article == null || article.getId() == null) {
            throw new BizException(500, "article is required before preview token issue");
        }
        tokenMapper.revokeByOrderId(order.getId(), LocalDateTime.now());

        String token = newToken();
        AuthorityMediaPreviewToken previewToken = new AuthorityMediaPreviewToken();
        previewToken.setOrderId(order.getId());
        previewToken.setArticleId(article.getId());
        previewToken.setTokenHash(sha256Hex(token));
        previewToken.setExpiresAt(LocalDateTime.now().plusDays(Math.max(properties.getPreviewTokenTtlDays(), 1)));
        previewToken.setAccessCount(0);
        tokenMapper.insert(previewToken);
        return normalizeBase(previewUrlBase) + "/api/public/authority-media/previews/" + token;
    }

    @Transactional
    public String renderPreview(String token, String ip, String userAgent) {
        if (!StringUtils.hasText(token)) {
            throw new BizException(404, "preview token not found");
        }
        AuthorityMediaPreviewToken previewToken = tokenMapper.selectByTokenHash(sha256Hex(token.trim()));
        if (previewToken == null) {
            throw new BizException(404, "preview token not found");
        }
        LocalDateTime now = LocalDateTime.now();
        if (previewToken.getRevokedAt() != null || previewToken.getExpiresAt() == null || previewToken.getExpiresAt().isBefore(now)) {
            throw new BizException(410, "preview token expired");
        }
        AuthorityMediaOrder order = orderMapper.selectById(previewToken.getOrderId());
        if (order == null
                || !"submitted".equals(order.getSubmitStatus())
                || (order.getRemoteStatus() != null && INVALID_REMOTE_STATUSES.contains(order.getRemoteStatus()))) {
            throw new BizException(410, "preview token revoked");
        }
        tokenMapper.recordAccess(
                previewToken.getId(),
                now,
                truncate(ip, 64),
                truncate(userAgent, 512)
        );
        return contentArticleService.publicPreviewHtml(previewToken.getArticleId());
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return TOKEN_ENCODER.encodeToString(bytes);
    }

    private String normalizeBase(String previewUrlBase) {
        if (!StringUtils.hasText(previewUrlBase)) {
            throw new BizException(500, "authority media preview url base is required");
        }
        return previewUrlBase.trim().replaceAll("/+$", "");
    }

    private String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
