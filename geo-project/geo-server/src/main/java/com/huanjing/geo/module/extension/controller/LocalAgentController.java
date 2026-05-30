package com.huanjing.geo.module.extension.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.extension.dto.LocalAgentPairingApproveRequest;
import com.huanjing.geo.module.extension.dto.LocalAgentPairingApproveResponse;
import com.huanjing.geo.module.extension.dto.LocalAgentPairingClaimRequest;
import com.huanjing.geo.module.extension.dto.LocalAgentPairingClaimResponse;
import com.huanjing.geo.module.extension.dto.LocalAgentPairingIntentRequest;
import com.huanjing.geo.module.extension.dto.LocalAgentPairingIntentResponse;
import com.huanjing.geo.module.extension.dto.LocalAgentSessionVO;
import com.huanjing.geo.module.extension.dto.LocalAgentSignRequest;
import com.huanjing.geo.module.extension.dto.LocalAgentSignResponse;
import com.huanjing.geo.module.extension.service.LocalAgentSessionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "LocalAgent")
@RestController
@RequestMapping("/api/v1/local-agent")
@RequiredArgsConstructor
public class LocalAgentController {
    private final LocalAgentSessionService service;

    @PostMapping("/pairing-intents")
    public R<LocalAgentPairingIntentResponse> registerPairingIntent(
            @Valid @RequestBody LocalAgentPairingIntentRequest request) {
        return R.ok(service.registerPairingIntent(request));
    }

    @PostMapping("/pairings/approve")
    public R<LocalAgentPairingApproveResponse> approvePairing(
            @Valid @RequestBody LocalAgentPairingApproveRequest request) {
        return R.ok(service.approvePairing(request));
    }

    @PostMapping("/pairings/claim")
    public R<LocalAgentPairingClaimResponse> claimPairing(
            @Valid @RequestBody LocalAgentPairingClaimRequest request,
            HttpServletRequest servletRequest) {
        return R.ok(service.claimPairing(request, servletRequest.getHeader("User-Agent")));
    }

    @GetMapping("/sessions")
    public R<List<LocalAgentSessionVO>> listSessions() {
        return R.ok(service.listActiveSessions());
    }

    @PostMapping("/sessions/{sessionId}/sign")
    public R<LocalAgentSignResponse> signRequest(@PathVariable Long sessionId,
                                                 @Valid @RequestBody LocalAgentSignRequest request) {
        return R.ok(service.signRequest(sessionId, request));
    }

    @PostMapping("/sessions/{sessionId}/revoke")
    public R<Void> revoke(@PathVariable Long sessionId) {
        service.revoke(sessionId);
        return R.ok();
    }
}
