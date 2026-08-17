package com.huanjing.geo.module.content.controller;

import com.huanjing.geo.module.content.douyin.DouyinAuthorizationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "DouyinAuthorization")
@RestController
@RequestMapping("/api/douyin/open-platform")
@RequiredArgsConstructor
public class DouyinAuthorizationController {
    private final DouyinAuthorizationService authorizationService;

    @GetMapping("/auth/callback")
    public ResponseEntity<Void> authCallback(@RequestParam(required = false) String code,
                                             @RequestParam(required = false) String state,
                                             @RequestParam(required = false) String error,
                                             @RequestParam(required = false, name = "error_description") String errorDescription) {
        String location = authorizationService.handleCallback(code, state);
        return ResponseEntity.status(302)
                .header(HttpHeaders.LOCATION, location)
                .build();
    }
}
