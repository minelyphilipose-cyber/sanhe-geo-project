package com.huanjing.geo.module.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.system.dto.SystemAlertTodoVO;
import com.huanjing.geo.module.system.service.SystemAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
                                              @RequestParam(defaultValue = "20") long size,
                                              @RequestParam(defaultValue = "false") boolean includeResolved,
                                              @RequestParam(defaultValue = "false") boolean unreadOnly) {
        return R.ok(systemAlertService.myTodos(current, size, includeResolved, unreadOnly));
    }

    @GetMapping("/my-unread-count")
    public R<Long> myUnreadCount() {
        return R.ok(systemAlertService.myUnreadCount());
    }

    @PostMapping("/{id}/read")
    public R<Void> markRead(@PathVariable Long id) {
        systemAlertService.markRead(id);
        return R.ok();
    }

    @PostMapping("/{id}/resolve")
    public R<Void> resolve(@PathVariable Long id) {
        systemAlertService.resolve(id);
        return R.ok();
    }
}
