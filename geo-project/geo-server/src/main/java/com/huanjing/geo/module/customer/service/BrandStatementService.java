package com.huanjing.geo.module.customer.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.customer.dto.BrandStatementUpdateRequest;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.ActivityLogService;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BrandStatementService {

    private final BrandMapper brandMapper;
    private final CompanyMapper companyMapper;
    private final CurrentUserService currentUserService;
    private final ActivityLogService activityLogService;

    public Map<String, Object> detail(Long brandId) {
        SysUser user = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("company.read");
        Brand brand = requireBrand(brandId);
        ensureBrandAccess(user, brand);
        return toStatementView(brand);
    }

    public void ensureRegeneratePermission(Long brandId) {
        SysUser user = currentUserService.requireCurrentUser();
        ensureEditableByInternal(user);
        Brand brand = requireBrand(brandId);
        ensureBrandAccess(user, brand);
    }

    @Transactional
    public Map<String, Object> saveDraft(Long brandId, BrandStatementUpdateRequest req) {
        SysUser user = currentUserService.requireCurrentUser();
        ensureEditableByInternal(user);
        Brand brand = requireBrand(brandId);
        ensureBrandAccess(user, brand);

        String statementJson = toStatementJson(req.getPositioning(), req.getSellingPoints(), req.getDifferentiation(), req.getBrandParagraph());
        int nextVersion = Math.max(brand.getStatementVersion() == null ? 0 : brand.getStatementVersion(), 0) + 1;
        LocalDateTime now = LocalDateTime.now();

        brand.setStandardStatement(statementJson);
        brand.setStatementStatus("draft");
        brand.setStatementVersion(nextVersion);
        brand.setStatementHistory(appendHistory(
                brand.getStatementHistory(),
                nextVersion,
                statementJson,
                now,
                user.getId(),
                "manual_edit"
        ));
        brandMapper.updateById(brand);

        activityLogService.logAction(
                user.getId(),
                "brand.statement.save_draft",
                "brand",
                brand.getId(),
                null,
                Map.of("statementStatus", "draft", "statementVersion", nextVersion),
                null
        );
        return toStatementView(brand);
    }

    @Transactional
    public Map<String, Object> lock(Long brandId) {
        SysUser user = currentUserService.requireCurrentUser();
        ensureLockPermission(user);
        Brand brand = requireBrand(brandId);
        ensureBrandAccess(user, brand);
        if (!StringUtils.hasText(brand.getStandardStatement())) {
            throw new BizException(400, "No statement to lock");
        }

        LocalDateTime now = LocalDateTime.now();
        brand.setStatementStatus("locked");
        brand.setStatementLockedAt(now);
        brand.setStatementLockedBy(user.getId());
        brandMapper.updateById(brand);

        activityLogService.logAction(
                user.getId(),
                "brand.statement.lock",
                "brand",
                brand.getId(),
                null,
                Map.of("statementStatus", "locked", "statementVersion", brand.getStatementVersion()),
                null
        );
        return toStatementView(brand);
    }

    @Transactional
    public Map<String, Object> unlock(Long brandId) {
        SysUser user = currentUserService.requireCurrentUser();
        ensureLockPermission(user);
        Brand brand = requireBrand(brandId);
        ensureBrandAccess(user, brand);
        if (!"locked".equalsIgnoreCase(brand.getStatementStatus())) {
            throw new BizException(400, "Only locked statement can unlock");
        }
        brand.setStatementStatus("draft");
        brandMapper.updateById(brand);

        activityLogService.logAction(
                user.getId(),
                "brand.statement.unlock",
                "brand",
                brand.getId(),
                null,
                Map.of("statementStatus", "draft", "statementVersion", brand.getStatementVersion()),
                null
        );
        return toStatementView(brand);
    }

    @Transactional
    public void applyAutoGeneratedStatement(Long brandId, String statementJson) {
        Brand brand = requireBrand(brandId);
        int nextVersion = Math.max(brand.getStatementVersion() == null ? 0 : brand.getStatementVersion(), 0) + 1;
        LocalDateTime now = LocalDateTime.now();
        brand.setStandardStatement(statementJson);
        brand.setStatementStatus("draft");
        brand.setStatementGeneratedAt(now);
        brand.setStatementVersion(nextVersion);
        brand.setStatementHistory(appendHistory(
                brand.getStatementHistory(),
                nextVersion,
                statementJson,
                now,
                null,
                "auto_generated"
        ));
        brandMapper.updateById(brand);
    }

    public String resolvePromptStatement(Brand brand) {
        if (brand == null) {
            return null;
        }
        if ("locked".equalsIgnoreCase(brand.getStatementStatus()) && StringUtils.hasText(brand.getStandardStatement())) {
            return brand.getStandardStatement();
        }
        if (brand.getStatementLockedAt() == null || !StringUtils.hasText(brand.getStatementHistory())) {
            return null;
        }
        JSONArray history = safeHistoryArray(brand.getStatementHistory());
        JSONObject matched = null;
        for (Object obj : history) {
            if (!(obj instanceof JSONObject entry)) {
                continue;
            }
            LocalDateTime createdAt = safeParseDateTime(entry.getStr("created_at"));
            if (createdAt == null || createdAt.isAfter(brand.getStatementLockedAt())) {
                continue;
            }
            if (matched == null) {
                matched = entry;
                continue;
            }
            LocalDateTime matchedAt = safeParseDateTime(matched.getStr("created_at"));
            if (matchedAt == null || createdAt.isAfter(matchedAt)) {
                matched = entry;
            }
        }
        if (matched == null) {
            return null;
        }
        JSONObject content = matched.getJSONObject("content");
        return content == null ? null : content.toString();
    }

    public String buildStructuredStatementJson(String positioning, List<String> sellingPoints, String differentiation, String brandParagraph) {
        return toStatementJson(positioning, sellingPoints, differentiation, brandParagraph);
    }

    private Map<String, Object> toStatementView(Brand brand) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("brandId", brand.getId());
        result.put("statementStatus", brand.getStatementStatus());
        result.put("statementVersion", brand.getStatementVersion());
        result.put("statementGeneratedAt", brand.getStatementGeneratedAt());
        result.put("statementLockedAt", brand.getStatementLockedAt());
        result.put("statementLockedBy", brand.getStatementLockedBy());
        result.put("standardStatement", parseJsonObject(brand.getStandardStatement()));
        result.put("statementHistory", safeHistoryArray(brand.getStatementHistory()));
        result.put("promptStatement", parseJsonObject(resolvePromptStatement(brand)));
        return result;
    }

    private JSONObject parseJsonObject(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return JSONUtil.parseObj(raw);
        } catch (Exception ex) {
            return null;
        }
    }

    private String toStatementJson(String positioning, List<String> sellingPoints, String differentiation, String brandParagraph) {
        JSONObject content = new JSONObject();
        content.set("positioning", nullSafeTrim(positioning));
        content.set("selling_points", normalizeSellingPoints(sellingPoints));
        content.set("differentiation", nullSafeTrim(differentiation));
        content.set("brand_paragraph", nullSafeTrim(brandParagraph));
        return content.toString();
    }

    private List<String> normalizeSellingPoints(List<String> sellingPoints) {
        if (sellingPoints == null) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String point : sellingPoints) {
            String val = nullSafeTrim(point);
            if (val != null) {
                normalized.add(val);
            }
        }
        return normalized;
    }

    private String appendHistory(String historyJson,
                                 int version,
                                 String statementJson,
                                 LocalDateTime createdAt,
                                 Long createdBy,
                                 String changeSource) {
        JSONArray arr = safeHistoryArray(historyJson);
        JSONObject entry = new JSONObject();
        entry.set("version", version);
        entry.set("content", parseJsonObject(statementJson));
        entry.set("created_at", createdAt.toString());
        entry.set("created_by", createdBy);
        entry.set("change_source", changeSource);
        arr.add(entry);
        return arr.toString();
    }

    private JSONArray safeHistoryArray(String historyJson) {
        if (!StringUtils.hasText(historyJson)) {
            return new JSONArray();
        }
        try {
            Object parsed = JSONUtil.parse(historyJson);
            if (parsed instanceof JSONArray arr) {
                return arr;
            }
            return new JSONArray();
        } catch (Exception ex) {
            return new JSONArray();
        }
    }

    private LocalDateTime safeParseDateTime(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return LocalDateTime.parse(text);
        } catch (Exception ex) {
            return null;
        }
    }

    private Brand requireBrand(Long brandId) {
        Brand brand = brandMapper.selectById(brandId);
        if (brand == null || brand.getDeletedAt() != null) {
            throw new BizException(404, "Brand not found");
        }
        return brand;
    }

    private void ensureBrandAccess(SysUser user, Brand brand) {
        Company company = companyMapper.selectById(brand.getCompanyId());
        if (company == null || company.getDeletedAt() != null) {
            throw new BizException(404, "Company not found");
        }
        currentUserService.ensurePartnerResourceAccess(user, company.getPartnerId(), "brand");
    }

    private void ensureEditableByInternal(SysUser user) {
        currentUserService.ensurePermission("brand.update");
        if (currentUserService.isPartnerUser(user)) {
            throw new BizException(403, "Partner role can only view brand statement");
        }
    }

    private void ensureLockPermission(SysUser user) {
        ensureEditableByInternal(user);
        if (!currentUserService.hasPermission("brand.statement.lock")) {
            throw new BizException(403, "No permission: brand.statement.lock");
        }
    }

    private String nullSafeTrim(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
