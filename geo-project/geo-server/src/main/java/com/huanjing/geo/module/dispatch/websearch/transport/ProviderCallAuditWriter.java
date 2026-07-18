package com.huanjing.geo.module.dispatch.websearch.transport;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.dispatch.entity.PollProviderCall;
import com.huanjing.geo.module.dispatch.mapper.PollProviderCallMapper;
import com.huanjing.geo.module.dispatch.websearch.enums.ErrorCategory;
import com.huanjing.geo.module.dispatch.websearch.enums.ProviderCallStatus;
import com.huanjing.geo.module.dispatch.websearch.model.WebSearchRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ProviderCallAuditWriter {

    private final PollProviderCallMapper mapper;
    private final PollPayloadProtector payloadProtector;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PollProviderCall start(WebSearchRequest request, String requestBody, LocalDateTime startedAt) {
        PollProviderCall call = new PollProviderCall();
        call.setAttemptId(request.attemptId());
        call.setCallType("MODEL_RESPONSE");
        call.setSequenceNo(mapper.selectMaxSequenceNo(request.attemptId()) + 1);
        call.setRetryNo(0);
        call.setProvider(request.profile().provider());
        call.setEndpointUrl(request.profile().endpointUrl());
        call.setHttpMethod("POST");
        call.setStatus(ProviderCallStatus.RUNNING.name());
        call.setRetryable(false);
        call.setSanitizedRequest(payloadProtector.sanitize(requestBody));
        call.setRawRequestEncrypted(payloadProtector.encryptIfConfigured(requestBody));
        call.setPayloadKeyVersion(payloadProtector.keyVersion());
        call.setDeadlineAt(request.attemptDeadlineAt());
        call.setStartedAt(startedAt);
        call.setCreatedAt(startedAt);
        call.setUpdatedAt(startedAt);
        if (mapper.insert(call) != 1) {
            throw new BizException(500, "Failed to create provider call audit");
        }
        return call;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void succeed(Long callId,
                        int httpStatus,
                        String responseBody,
                        String providerRequestId,
                        String usageJson,
                        LocalDateTime completedAt,
                        long latencyMs) {
        PollProviderCall update = baseCompletion(callId, completedAt, latencyMs);
        update.setStatus(ProviderCallStatus.SUCCEEDED.name());
        update.setHttpStatus(httpStatus);
        update.setProviderRequestId(providerRequestId);
        update.setUsageJson(usageJson);
        update.setSanitizedResponse(payloadProtector.sanitize(responseBody));
        update.setRawResponseEncrypted(payloadProtector.encryptIfConfigured(responseBody));
        update.setPayloadKeyVersion(payloadProtector.keyVersion());
        requireUpdated(mapper.updateById(update), callId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long callId,
                     Integer httpStatus,
                     String responseBody,
                     ErrorCategory category,
                     String errorCode,
                     String errorMessage,
                     LocalDateTime completedAt,
                     long latencyMs) {
        PollProviderCall update = baseCompletion(callId, completedAt, latencyMs);
        update.setStatus(ProviderCallStatus.FAILED.name());
        update.setHttpStatus(httpStatus);
        update.setErrorCategory(category.name());
        update.setErrorCode(errorCode);
        update.setErrorMessage(truncate(errorMessage, 2000));
        update.setRetryable(category.retryable());
        update.setSanitizedResponse(payloadProtector.sanitize(responseBody));
        update.setRawResponseEncrypted(payloadProtector.encryptIfConfigured(responseBody));
        update.setPayloadKeyVersion(payloadProtector.keyVersion());
        requireUpdated(mapper.updateById(update), callId);
    }

    private PollProviderCall baseCompletion(Long callId, LocalDateTime completedAt, long latencyMs) {
        PollProviderCall update = new PollProviderCall();
        update.setId(callId);
        update.setCompletedAt(completedAt);
        update.setLatencyMs(latencyMs);
        update.setUpdatedAt(completedAt);
        return update;
    }

    private void requireUpdated(int rows, Long callId) {
        if (rows != 1) {
            throw new BizException(409, "Provider call audit changed concurrently: " + callId);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
