package com.huanjing.geo.module.extension.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.mapper.DistributionTaskMapper;
import com.huanjing.geo.module.extension.config.ExtensionProperties;
import com.huanjing.geo.module.extension.dto.ExtensionTaskListItemResponse;
import com.huanjing.geo.module.extension.dto.ExtensionTaskListRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static com.huanjing.geo.module.extension.ExtensionErrorCodes.TASK_STATE_CONFLICT;

@Service
@RequiredArgsConstructor
public class ExtensionTaskListService {

    private static final int TASK_LIMIT = 20;

    private final DistributionTaskMapper taskMapper;
    private final ExtensionProperties properties;

    public List<ExtensionTaskListItemResponse> listTasksForSessionOperator(Long operatorId) {
        List<ExtensionTaskListRow> rows = taskMapper.selectExtensionSemiAutoTasks(operatorId, TASK_LIMIT);
        return rows.stream()
                .map(row -> toResponse(row, operatorId))
                .toList();
    }

    private ExtensionTaskListItemResponse toResponse(ExtensionTaskListRow row, Long sessionOperatorId) {
        if (!Objects.equals(row.getOperatorId(), sessionOperatorId)) {
            throw new BizException(TASK_STATE_CONFLICT, "task operator mismatch");
        }
        LocalDateTime expiresAt = row.getFillTokenIssuedAt() == null
                ? null
                : row.getFillTokenIssuedAt().plusSeconds(properties.getFillToken().getTtlSeconds());
        return new ExtensionTaskListItemResponse(
                row.getTaskId(),
                row.getPlatform(),
                row.getStatus(),
                row.getPublishUrl(),
                row.getTitle(),
                row.getCreatedAt(),
                row.getFillTokenIssuedAt(),
                expiresAt
        );
    }
}
