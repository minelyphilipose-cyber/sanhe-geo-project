package com.huanjing.geo.module.extension.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.extension.dto.ClaimGateEvaluation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class SelfMediaGateDiagnosticsWriter {

    private final ObjectMapper objectMapper;

    public String mergeClaimGate(String diagnosticsJson, ClaimGateEvaluation evaluation) {
        ObjectNode root = parseRoot(diagnosticsJson);
        ObjectNode gate = objectMapper.createObjectNode();
        gate.put("gateMode", evaluation.gateMode());
        gate.put("wouldBlock", evaluation.wouldBlock());
        gate.putPOJO("blockedReasons", evaluation.blockedReasons());
        gate.put("evaluatedAt", evaluation.evaluatedAt().toString());
        if (evaluation.retryAfterSeconds() == null) {
            gate.putNull("retryAfterSeconds");
        } else {
            gate.put("retryAfterSeconds", evaluation.retryAfterSeconds());
        }
        root.set("claimGate", gate);
        return root.toString();
    }

    private ObjectNode parseRoot(String diagnosticsJson) {
        if (!StringUtils.hasText(diagnosticsJson)) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode node = objectMapper.readTree(diagnosticsJson);
            return node != null && node.isObject()
                    ? (ObjectNode) node
                    : objectMapper.createObjectNode();
        } catch (Exception ex) {
            throw new BizException(70044, "diagnostics json invalid", 400, null, ex);
        }
    }
}
