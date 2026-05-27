package com.huanjing.geo.module.extension.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.mapper.DistributionTaskMapper;
import com.huanjing.geo.module.extension.config.ExtensionProperties;
import com.huanjing.geo.module.extension.dto.ExtensionTaskListItemResponse;
import com.huanjing.geo.module.extension.dto.ExtensionTaskListRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExtensionTaskListServiceTest {

    private DistributionTaskMapper taskMapper;
    private ExtensionTaskListService service;

    @BeforeEach
    void setUp() {
        taskMapper = mock(DistributionTaskMapper.class);
        ExtensionProperties properties = new ExtensionProperties();
        properties.getFillToken().setTtlSeconds(300);
        service = new ExtensionTaskListService(taskMapper, properties);
    }

    @Test
    void listsCurrentOperatorSemiAutoTasksWithWhitelistedResponseFields() {
        LocalDateTime issuedAt = LocalDateTime.of(2026, 5, 7, 12, 0);
        when(taskMapper.selectExtensionSemiAutoTasks(99L, 20)).thenReturn(List.of(row(30L, 99L, "token_issued", issuedAt)));

        List<ExtensionTaskListItemResponse> result = service.listTasksForSessionOperator(99L);

        assertEquals(1, result.size());
        ExtensionTaskListItemResponse task = result.get(0);
        assertEquals(30L, task.taskId());
        assertEquals("douyin", task.platform());
        assertEquals("token_issued", task.status());
        assertEquals("文章标题", task.title());
        assertEquals(issuedAt.plusMinutes(5), task.expiresAt());
    }

    @Test
    void mapperIsCalledWithCurrentOperatorOnlySoOtherOperatorTasksAreNotLoaded() {
        when(taskMapper.selectExtensionSemiAutoTasks(99L, 20)).thenReturn(List.of());

        assertEquals(List.of(), service.listTasksForSessionOperator(99L));

        verify(taskMapper).selectExtensionSemiAutoTasks(99L, 20);
    }

    @Test
    void rejectsRowWhenOperatorDoesNotStrictlyMatchSessionOperator() {
        when(taskMapper.selectExtensionSemiAutoTasks(99L, 20)).thenReturn(List.of(row(30L, 100L, "token_issued", LocalDateTime.now())));

        BizException ex = assertThrows(BizException.class, () -> service.listTasksForSessionOperator(99L));

        assertEquals(70012, ex.getCode());
    }

    @Test
    void includesOnlyMapperProvidedAllowedStatuses() {
        when(taskMapper.selectExtensionSemiAutoTasks(99L, 20)).thenReturn(List.of(
                row(30L, 99L, "token_issued", LocalDateTime.now()),
                row(31L, 99L, "filling", LocalDateTime.now()),
                row(32L, 99L, "filled", LocalDateTime.now())
        ));

        List<String> statuses = service.listTasksForSessionOperator(99L).stream()
                .map(ExtensionTaskListItemResponse::status)
                .toList();

        assertEquals(List.of("token_issued", "filling", "filled"), statuses);
    }

    private ExtensionTaskListRow row(Long taskId, Long operatorId, String status, LocalDateTime issuedAt) {
        ExtensionTaskListRow row = new ExtensionTaskListRow();
        row.setTaskId(taskId);
        row.setPlatform("douyin");
        row.setStatus(status);
        row.setPublishUrl("https://example.test/published");
        row.setTitle("文章标题");
        row.setCreatedAt(issuedAt.minusMinutes(2));
        row.setFillTokenIssuedAt(issuedAt);
        row.setOperatorId(operatorId);
        row.setBrandId(10L);
        return row;
    }
}
