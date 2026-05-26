package com.huanjing.geo.module.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.system.dto.SystemAlertTodoVO;
import com.huanjing.geo.module.system.service.SystemAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/alerts")
@RequiredArgsConstructor
public class SystemAlertController {

    private final SystemAlertService systemAlertService;

    @GetMapping("/my-todos")
    public R<Page<SystemAlertTodoVO>> myTodos(@RequestParam(defaultValue = "1") long current,
                                              @RequestParam(defaultValue = "20") long size) {
        return R.ok(systemAlertService.myTodos(current, size));
    }
}
