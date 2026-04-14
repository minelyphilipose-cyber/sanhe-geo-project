package com.huanjing.geo.module.project.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.project.dto.QuestionPoolManageItemVO;
import com.huanjing.geo.module.project.service.QuestionPoolService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "QuestionPool")
@RestController
@RequestMapping("/api/question-pools")
@RequiredArgsConstructor
public class QuestionPoolController {

    private final QuestionPoolService questionPoolService;

    @GetMapping
    public R<Page<QuestionPoolManageItemVO>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long projectId
    ) {
        return R.ok(questionPoolService.pageManage(current, size, keyword, projectId));
    }
}
