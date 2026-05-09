package com.huanjing.geo.module.content.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.content.dto.AuthorityMediaResourceVO;
import com.huanjing.geo.module.content.service.AuthorityMediaResourceQueryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@Tag(name = "AuthorityMediaResource")
@RestController
@RequestMapping("/api/content/authority-media/resources")
@RequiredArgsConstructor
public class AuthorityMediaResourceController {

    private final AuthorityMediaResourceQueryService resourceQueryService;

    @GetMapping
    public R<Page<AuthorityMediaResourceVO>> page(@RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) String industry,
                                                  @RequestParam(required = false) String province,
                                                  @RequestParam(required = false) Integer entranceLevel,
                                                  @RequestParam(required = false) Integer newsResource,
                                                  @RequestParam(required = false) Integer includeCondition,
                                                  @RequestParam(required = false) Integer weekendPublish,
                                                  @RequestParam(required = false) BigDecimal minPrice,
                                                  @RequestParam(required = false) BigDecimal maxPrice,
                                                  @RequestParam(required = false) Integer minPcWeight,
                                                  @RequestParam(required = false) Integer minMWeight,
                                                  @RequestParam(defaultValue = "1") Long current,
                                                  @RequestParam(defaultValue = "10") Long size) {
        return R.ok(resourceQueryService.page(
                keyword,
                industry,
                province,
                entranceLevel,
                newsResource,
                includeCondition,
                weekendPublish,
                minPrice,
                maxPrice,
                minPcWeight,
                minMWeight,
                current,
                size
        ));
    }
}
