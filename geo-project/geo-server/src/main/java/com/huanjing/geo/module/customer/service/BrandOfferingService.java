package com.huanjing.geo.module.customer.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.customer.dto.BrandOfferingRequest;
import com.huanjing.geo.module.customer.dto.BrandOfferingVO;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.entity.BrandOffering;
import com.huanjing.geo.module.customer.mapper.BrandOfferingMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class BrandOfferingService {

    private static final List<String> STATUSES = List.of("active", "disabled");
    private static final int DEFAULT_PRIORITY_STEP = 10;

    private final BrandOfferingMapper offeringMapper;
    private final BrandService brandService;
    private final BrandProfileService brandProfileService;
    private final CurrentUserService currentUserService;

    public List<BrandOfferingVO> list(Long brandId, String status) {
        brandService.requireBrandWithAccess(brandId, false);
        LambdaQueryWrapper<BrandOffering> wrapper = new LambdaQueryWrapper<BrandOffering>()
                .eq(BrandOffering::getBrandId, brandId)
                .isNull(BrandOffering::getDeletedAt)
                .orderByAsc(BrandOffering::getPriority, BrandOffering::getId);
        if (StringUtils.hasText(status)) {
            wrapper.eq(BrandOffering::getStatus, normalizeStatus(status));
        }
        return offeringMapper.selectList(wrapper).stream().map(this::toVO).toList();
    }

    @Transactional
    public BrandOfferingVO create(Long brandId, BrandOfferingRequest req) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("brand.update");
        Brand brand = brandService.requireBrandWithAccess(brandId, true);

        BrandOffering offering = new BrandOffering();
        offering.setBrandId(brandId);
        applyRequest(offering, req);
        if (req.getPriority() == null) {
            offering.setPriority(nextPriority(brandId));
        }
        offering.setCreatedBy(operator.getId());
        offeringMapper.insert(offering);
        brandProfileService.createProfileVersionSnapshot(brand, operator.getId(), "offering.create");
        return toVO(offering);
    }

    @Transactional
    public BrandOfferingVO update(Long brandId, Long offeringId, BrandOfferingRequest req) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("brand.update");
        Brand brand = brandService.requireBrandWithAccess(brandId, true);
        BrandOffering offering = requireOffering(brandId, offeringId);
        applyRequest(offering, req);
        offeringMapper.updateById(offering);
        brandProfileService.createProfileVersionSnapshot(brand, operator.getId(), "offering.update");
        return toVO(offering);
    }

    @Transactional
    public void delete(Long brandId, Long offeringId) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("brand.update");
        Brand brand = brandService.requireBrandWithAccess(brandId, true);
        BrandOffering offering = requireOffering(brandId, offeringId);
        offering.setDeletedAt(LocalDateTime.now());
        offeringMapper.updateById(offering);
        brandProfileService.createProfileVersionSnapshot(brand, operator.getId(), "offering.delete");
    }

    private BrandOffering requireOffering(Long brandId, Long offeringId) {
        BrandOffering offering = offeringMapper.selectById(offeringId);
        if (offering == null || offering.getDeletedAt() != null || !brandId.equals(offering.getBrandId())) {
            throw new BizException(404, "Brand offering not found");
        }
        return offering;
    }

    private void applyRequest(BrandOffering offering, BrandOfferingRequest req) {
        offering.setOfferingName(req.getOfferingName().trim());
        offering.setOfferingAliasesJson(toAliasesJson(req.getOfferingAliases()));
        offering.setTargetUsers(trimToNull(req.getTargetUsers()));
        offering.setOfferingIntro(trimToNull(req.getOfferingIntro()));
        offering.setQualificationDescription(trimToNull(req.getQualificationDescription()));
        offering.setRemark(trimToNull(req.getRemark()));
        offering.setStatus(normalizeStatus(req.getStatus()));
        if (req.getPriority() != null) {
            offering.setPriority(Math.max(0, req.getPriority()));
        } else if (offering.getPriority() == null) {
            offering.setPriority(DEFAULT_PRIORITY_STEP);
        }
        offering.setUseScenarios(trimToNull(req.getUseScenarios()));
        offering.setMedicalIndustryCode(trimToNull(req.getMedicalIndustryCode()));
        offering.setMedicalCategoryCode(trimToNull(req.getMedicalCategoryCode()));
        offering.setMedicalCategoryName(trimToNull(req.getMedicalCategoryName()));
        offering.setQualificationRef(trimToNull(req.getQualificationRef()));
        offering.setMedicalProjectEnabled(Boolean.TRUE.equals(req.getMedicalProjectEnabled()));
    }

    private String normalizeStatus(String status) {
        String value = StringUtils.hasText(status) ? status.trim().toLowerCase(Locale.ROOT) : "active";
        if (!STATUSES.contains(value)) {
            throw new BizException(400, "Invalid offering status");
        }
        return value;
    }

    private String toAliasesJson(String aliases) {
        List<String> values = parseAliases(aliases);
        return values.isEmpty() ? null : JSONUtil.toJsonStr(values);
    }

    private List<String> parseAliases(String aliases) {
        if (!StringUtils.hasText(aliases)) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String item : aliases.split("[,，、;；\\n\\r]+")) {
            if (StringUtils.hasText(item)) {
                String value = item.trim();
                if (!values.contains(value)) {
                    values.add(value);
                }
            }
        }
        return values;
    }

    private List<String> parseAliasesJson(String aliasesJson) {
        if (!StringUtils.hasText(aliasesJson)) {
            return List.of();
        }
        try {
            List<String> values = new ArrayList<>();
            JSONUtil.parseArray(aliasesJson).forEach(item -> {
                if (item != null && StringUtils.hasText(String.valueOf(item))) {
                    values.add(String.valueOf(item).trim());
                }
            });
            return values;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private BrandOfferingVO toVO(BrandOffering offering) {
        BrandOfferingVO vo = new BrandOfferingVO();
        vo.setId(offering.getId());
        vo.setBrandId(offering.getBrandId());
        vo.setOfferingName(offering.getOfferingName());
        vo.setOfferingAliases(parseAliasesJson(offering.getOfferingAliasesJson()));
        vo.setTargetUsers(offering.getTargetUsers());
        vo.setOfferingIntro(offering.getOfferingIntro());
        vo.setQualificationDescription(offering.getQualificationDescription());
        vo.setRemark(offering.getRemark());
        vo.setStatus(offering.getStatus());
        vo.setPriority(offering.getPriority());
        vo.setUseScenarios(offering.getUseScenarios());
        vo.setMedicalIndustryCode(offering.getMedicalIndustryCode());
        vo.setMedicalCategoryCode(offering.getMedicalCategoryCode());
        vo.setMedicalCategoryName(offering.getMedicalCategoryName());
        vo.setQualificationRef(offering.getQualificationRef());
        vo.setMedicalProjectEnabled(Boolean.TRUE.equals(offering.getMedicalProjectEnabled()));
        vo.setCreatedAt(offering.getCreatedAt());
        vo.setUpdatedAt(offering.getUpdatedAt());
        return vo;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private int nextPriority(Long brandId) {
        BrandOffering latest = offeringMapper.selectOne(new LambdaQueryWrapper<BrandOffering>()
                .eq(BrandOffering::getBrandId, brandId)
                .isNull(BrandOffering::getDeletedAt)
                .orderByDesc(BrandOffering::getPriority)
                .orderByDesc(BrandOffering::getId)
                .last("LIMIT 1"));
        Integer currentMax = latest == null || latest.getPriority() == null ? 0 : latest.getPriority();
        return currentMax + DEFAULT_PRIORITY_STEP;
    }
}
