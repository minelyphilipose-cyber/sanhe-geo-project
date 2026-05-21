package com.huanjing.geo.module.customer.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.customer.entity.BrandMaterial;
import com.huanjing.geo.module.customer.mapper.BrandMaterialMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class BrandMaterialPublicUrlService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String PUBLIC_STREAM_PATH = "/api/public/brand-materials/%d/stream";

    private final BrandMaterialMapper brandMaterialMapper;

    @Value("${geo.public-base-url:${APP_PUBLIC_URL:}}")
    private String publicBaseUrl;

    @Value("${geo.jwt.secret:}")
    private String jwtSecret;

    @Value("${geo.material-public-token-secret:${MATERIAL_PUBLIC_TOKEN_SECRET:}}")
    private String materialPublicTokenSecret;

    public String buildPublicStreamUrl(BrandMaterial material) {
        requirePublicMaterial(material);
        String baseUrl = normalizeBaseUrl(publicBaseUrl);
        return baseUrl + PUBLIC_STREAM_PATH.formatted(material.getId()) + "?sig=" + sign(material);
    }

    public BrandMaterial verifyPublicAccess(Long materialId, String signature) {
        if (materialId == null || !StringUtils.hasText(signature)) {
            throw new BizException(404, "Material not found");
        }
        BrandMaterial material = brandMaterialMapper.selectById(materialId);
        requirePublicMaterial(material);
        if (!MessageDigest.isEqual(sign(material).getBytes(StandardCharsets.UTF_8),
                signature.trim().getBytes(StandardCharsets.UTF_8))) {
            throw new BizException(404, "Material not found");
        }
        return material;
    }

    private void requirePublicMaterial(BrandMaterial material) {
        if (material == null
                || material.getId() == null
                || !StringUtils.hasText(material.getObjectKey())
                || !"brand_image".equals(material.getCategory())) {
            throw new BizException(404, "Material not found");
        }
    }

    private String sign(BrandMaterial material) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(resolveSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            String payload = material.getId() + ":" + material.getBrandId() + ":" + material.getObjectKey();
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception ex) {
            throw new BizException(500, "Generate material public url failed");
        }
    }

    private String resolveSecret() {
        if (StringUtils.hasText(materialPublicTokenSecret)) {
            return materialPublicTokenSecret.trim();
        }
        if (StringUtils.hasText(jwtSecret)) {
            return jwtSecret.trim();
        }
        throw new BizException(500, "Material public token secret is required");
    }

    private String normalizeBaseUrl(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(500, "APP_PUBLIC_URL is required for external image urls");
        }
        return value.trim().replaceAll("/+$", "");
    }
}
