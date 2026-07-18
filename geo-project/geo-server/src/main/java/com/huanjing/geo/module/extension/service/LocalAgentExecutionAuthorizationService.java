package com.huanjing.geo.module.extension.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.mapper.SelfMediaPublishScheduleMapper;
import com.huanjing.geo.module.customer.access.BrandAccessAction;
import com.huanjing.geo.module.customer.access.BrandAccessService;
import com.huanjing.geo.module.extension.entity.LocalAgentRuntimeStatus;
import com.huanjing.geo.module.extension.entity.LocalAgentSession;
import com.huanjing.geo.module.extension.mapper.LocalAgentRuntimeStatusMapper;
import com.huanjing.geo.module.extension.mapper.LocalAgentSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class LocalAgentExecutionAuthorizationService {

    private static final int ERROR_CODE = 70040;

    private final LocalAgentSessionMapper sessionMapper;
    private final LocalAgentRuntimeStatusMapper runtimeStatusMapper;
    private final SelfMediaPublishScheduleMapper scheduleMapper;
    private final BrandAccessService brandAccessService;

    public AuthorizationResult evaluate(Long operatorId,
                                        Long localAgentSessionId,
                                        Long brandId,
                                        Long browserEnvironmentId,
                                        LocalDateTime now) {
        LocalDateTime checkedAt = now == null ? LocalDateTime.now() : now;
        if (operatorId == null || operatorId <= 0 || localAgentSessionId == null || localAgentSessionId <= 0) {
            return AuthorizationResult.denied("INVALID_LOCAL_AGENT_SESSION");
        }
        LocalAgentSession session = sessionMapper.selectById(localAgentSessionId);
        if (session == null || !"active".equalsIgnoreCase(session.getStatus())) {
            return AuthorizationResult.denied("LOCAL_AGENT_SESSION_INACTIVE");
        }
        if (!Objects.equals(operatorId, session.getOperatorId())) {
            return AuthorizationResult.denied("LOCAL_AGENT_OPERATOR_MISMATCH");
        }
        if (session.getExpiresAt() == null || !session.getExpiresAt().isAfter(checkedAt)) {
            return AuthorizationResult.denied("LOCAL_AGENT_SESSION_EXPIRED");
        }
        if (brandId == null || brandId <= 0) {
            return AuthorizationResult.denied("BRAND_MISSING");
        }
        if (session.getBrandId() != null && !Objects.equals(session.getBrandId(), brandId)) {
            return AuthorizationResult.denied("LOCAL_AGENT_SESSION_BRAND_MISMATCH");
        }
        if (!brandAccessService.hasBrandAccess(brandId, operatorId, BrandAccessAction.OPERATE)) {
            return AuthorizationResult.denied("NO_AUTHORIZED_BRAND");
        }
        LocalAgentRuntimeStatus runtime = runtimeStatusMapper.selectLatestBySessionId(localAgentSessionId);
        if (runtime == null || !Objects.equals(operatorId, runtime.getOperatorId())) {
            return AuthorizationResult.denied("HELPER_OFFLINE");
        }
        if (browserEnvironmentId == null || browserEnvironmentId <= 0
                || !scheduleMapper.isBrowserEnvironmentOwnedByLocalAgent(
                browserEnvironmentId,
                localAgentSessionId,
                brandId,
                operatorId,
                checkedAt)) {
            return AuthorizationResult.denied("ENVIRONMENT_NOT_BOUND_TO_THIS_HELPER");
        }
        return AuthorizationResult.allowed();
    }

    public void requireAuthorized(Long operatorId,
                                  Long localAgentSessionId,
                                  Long brandId,
                                  Long browserEnvironmentId,
                                  LocalDateTime now) {
        AuthorizationResult result = evaluate(
                operatorId, localAgentSessionId, brandId, browserEnvironmentId, now);
        if (result.authorized()) {
            return;
        }
        throw new BizException(
                ERROR_CODE,
                "当前本地助手没有执行该品牌浏览器环境的权限",
                403,
                Map.of("code", result.reason())
        );
    }

    public record AuthorizationResult(boolean authorized, String reason) {
        public static AuthorizationResult allowed() {
            return new AuthorizationResult(true, null);
        }

        public static AuthorizationResult denied(String reason) {
            return new AuthorizationResult(false, reason);
        }
    }
}
