package com.huanjing.geo.module.system.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.system.dto.KeywordAffixWordOptionVO;
import com.huanjing.geo.module.system.service.KeywordAffixWordService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "KeywordAffixWord")
@RestController
@RequestMapping("/api/keyword-affix-words")
@RequiredArgsConstructor
public class KeywordAffixWordController {

    private final KeywordAffixWordService keywordAffixWordService;

    @GetMapping("/options")
    public R<KeywordAffixWordOptionVO> options(@RequestParam(required = false) String type) {
        return R.ok(keywordAffixWordService.options(type));
    }
}
