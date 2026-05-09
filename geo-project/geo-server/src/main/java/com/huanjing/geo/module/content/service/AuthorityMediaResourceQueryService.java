package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.content.authoritymedia.MeititejiaResourceType;
import com.huanjing.geo.module.content.dto.AuthorityMediaResourceVO;
import com.huanjing.geo.module.content.entity.AuthorityMediaResource;
import com.huanjing.geo.module.content.mapper.AuthorityMediaResourceMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class AuthorityMediaResourceQueryService {

    private final AuthorityMediaResourceMapper resourceMapper;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;

    public Page<AuthorityMediaResourceVO> page(String keyword,
                                               String industry,
                                               String province,
                                               Integer entranceLevel,
                                               Integer newsResource,
                                               Integer includeCondition,
                                               Integer weekendPublish,
                                               BigDecimal minPrice,
                                               BigDecimal maxPrice,
                                               Integer minPcWeight,
                                               Integer minMWeight,
                                               Long current,
                                               Long size) {
        currentUserService.ensurePermission("project.read");
        LambdaQueryWrapper<AuthorityMediaResource> wrapper = new LambdaQueryWrapper<AuthorityMediaResource>()
                .eq(AuthorityMediaResource::getResourceType, MeititejiaResourceType.NEWS_MEDIA.name())
                .eq(AuthorityMediaResource::getStatus, 1)
                .isNull(AuthorityMediaResource::getDeletedAt)
                .orderByAsc(AuthorityMediaResource::getPrice)
                .orderByDesc(AuthorityMediaResource::getPcWeight)
                .orderByDesc(AuthorityMediaResource::getMWeight)
                .orderByAsc(AuthorityMediaResource::getId);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(AuthorityMediaResource::getName, keyword.trim());
        }
        if (StringUtils.hasText(industry)) {
            wrapper.like(AuthorityMediaResource::getIndustry, industry.trim());
        }
        if (StringUtils.hasText(province)) {
            wrapper.like(AuthorityMediaResource::getProvince, province.trim());
        }
        if (entranceLevel != null) {
            wrapper.eq(AuthorityMediaResource::getEntranceLevel, entranceLevel);
        }
        if (newsResource != null) {
            wrapper.eq(AuthorityMediaResource::getNewsResource, newsResource);
        }
        if (includeCondition != null) {
            wrapper.eq(AuthorityMediaResource::getIncludeCondition, includeCondition);
        }
        if (weekendPublish != null) {
            wrapper.eq(AuthorityMediaResource::getWeekendPublish, weekendPublish);
        }
        if (minPrice != null) {
            wrapper.ge(AuthorityMediaResource::getPrice, minPrice);
        }
        if (maxPrice != null) {
            wrapper.le(AuthorityMediaResource::getPrice, maxPrice);
        }
        if (minPcWeight != null) {
            wrapper.ge(AuthorityMediaResource::getPcWeight, minPcWeight);
        }
        if (minMWeight != null) {
            wrapper.ge(AuthorityMediaResource::getMWeight, minMWeight);
        }

        Page<AuthorityMediaResource> data = resourceMapper.selectPage(new Page<>(current, size), wrapper);
        Page<AuthorityMediaResourceVO> result = new Page<>(data.getCurrent(), data.getSize(), data.getTotal());
        result.setRecords(data.getRecords().stream().map(this::toVO).toList());
        return result;
    }

    private AuthorityMediaResourceVO toVO(AuthorityMediaResource resource) {
        AuthorityMediaResourceVO vo = new AuthorityMediaResourceVO();
        vo.setId(resource.getId());
        vo.setResourceType(resource.getResourceType());
        vo.setExternalResourceId(resource.getExternalResourceId());
        vo.setName(resource.getName());
        vo.setPlatform(resource.getPlatform());
        vo.setIndustry(resource.getIndustry());
        vo.setProvince(resource.getProvince());
        vo.setPrice(resource.getPrice());
        vo.setStatus(resource.getStatus());
        vo.setPcWeight(resource.getPcWeight());
        vo.setMWeight(resource.getMWeight());
        vo.setNewsResource(resource.getNewsResource());
        vo.setEntranceLevel(resource.getEntranceLevel());
        vo.setIncludeCondition(resource.getIncludeCondition());
        vo.setPublicationTime(resource.getPublicationTime());
        vo.setWeekendPublish(resource.getWeekendPublish());
        vo.setPublishRate(resource.getPublishRate());
        vo.setInclusionRate(resource.getInclusionRate());
        vo.setRemark(resource.getRemark());
        vo.setUptime(resource.getUptime());
        vo.setUpdatedAt(resource.getUpdatedAt());
        fillRawFields(vo, resource.getRawPayload());
        return vo;
    }

    private void fillRawFields(AuthorityMediaResourceVO vo, String rawPayload) {
        if (!StringUtils.hasText(rawPayload)) {
            return;
        }
        try {
            JsonNode raw = objectMapper.readTree(rawPayload);
            vo.setEntranceLink(text(raw, "entrance_link"));
            vo.setCaseLink(text(raw, "case_link"));
            vo.setNoDisclaimer(integer(raw, "no_disclaimer"));
            vo.setCanSign(integer(raw, "can_sign"));
            vo.setFirstPublish(integer(raw, "first_publish"));
            vo.setKeep3Month(integer(raw, "keep_3_month"));
            vo.setFocalPic(text(raw, "focal_pic"));
        } catch (Exception ignored) {
            // Raw payload is only used for optional display metadata.
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode child = node == null ? null : node.get(field);
        return child == null || child.isNull() || !StringUtils.hasText(child.asText()) ? null : child.asText().trim();
    }

    private Integer integer(JsonNode node, String field) {
        JsonNode child = node == null ? null : node.get(field);
        if (child == null || child.isNull() || !StringUtils.hasText(child.asText())) {
            return null;
        }
        if (child.canConvertToInt()) {
            return child.asInt();
        }
        try {
            return Integer.parseInt(child.asText().trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
