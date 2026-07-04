package com.huanjing.geo.module.mobiledashboard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.customer.access.InternalScopeService;
import com.huanjing.geo.module.mobiledashboard.dto.ProjectCompetitorConfigRequest;
import com.huanjing.geo.module.mobiledashboard.dto.ProjectCompetitorConfigVO;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ProjectCompetitorConfigServiceTest {

    @Test
    void updateExisting_doesNotBumpVersionWhenOnlyQaStatusChangesToPassed() throws Exception {
        CapturedUpdate update = updateExisting(
                existing("竞品一", List.of("旧别名"), 1, "active", "pending"),
                item(7L, "竞品一", List.of("旧别名"), 1, true, "passed")
        );

        assertThat(update.sql()).contains("config_version = config_version + ?");
        assertThat(update.args()[7]).isEqualTo(false);
        assertThat(update.args()[8]).isEqualTo("passed");
        assertThat(update.args()[9]).isEqualTo(0);
    }

    @Test
    void updateExisting_doesNotBumpVersionWhenOnlyDisplayOrderOrStatusChanges() throws Exception {
        CapturedUpdate update = updateExisting(
                existing("竞品一", List.of("旧别名"), 1, "active", "passed"),
                item(7L, "竞品一", List.of("旧别名"), 2, false, "passed")
        );

        assertThat(update.args()[4]).isEqualTo(2);
        assertThat(update.args()[5]).isEqualTo("disabled");
        assertThat(update.args()[7]).isEqualTo(false);
        assertThat(update.args()[9]).isEqualTo(0);
    }

    @Test
    void updateExisting_bumpsVersionAndKeepsQaPassedWhenNameChanges() throws Exception {
        CapturedUpdate update = updateExisting(
                existing("竞品一", List.of("旧别名"), 1, "active", "passed"),
                item(7L, "竞品二", List.of("旧别名"), 1, true, "passed")
        );

        assertThat(update.args()[0]).isEqualTo("竞品二");
        assertThat(update.args()[6]).isEqualTo("passed");
        assertThat(update.args()[7]).isEqualTo(true);
        assertThat(update.args()[9]).isEqualTo(1);
    }

    @Test
    void updateExisting_bumpsVersionAndKeepsQaPassedWhenAliasesChange() throws Exception {
        CapturedUpdate update = updateExisting(
                existing("竞品一", List.of("旧别名"), 1, "active", "passed"),
                item(7L, "竞品一", List.of("新别名"), 1, true, "passed")
        );

        assertThat(update.args()[6]).isEqualTo("passed");
        assertThat(update.args()[7]).isEqualTo(true);
        assertThat(update.args()[9]).isEqualTo(1);
    }

    private CapturedUpdate updateExisting(ProjectCompetitorConfigVO existing,
                                          ProjectCompetitorConfigRequest.Item item) throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ProjectCompetitorConfigService service = new ProjectCompetitorConfigService(
                jdbcTemplate,
                mock(CurrentUserService.class),
                mock(InternalScopeService.class),
                mock(ProjectMapper.class),
                new ObjectMapper()
        );
        Method method = ProjectCompetitorConfigService.class.getDeclaredMethod(
                "updateExisting",
                Long.class,
                ProjectCompetitorConfigRequest.Item.class,
                ProjectCompetitorConfigVO.class
        );
        method.setAccessible(true);
        method.invoke(service, 11L, item, existing);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), argsCaptor.capture());
        return new CapturedUpdate(sqlCaptor.getValue(), argsCaptor.getValue());
    }

    private ProjectCompetitorConfigVO existing(String name,
                                               List<String> aliases,
                                               int displayOrder,
                                               String status,
                                               String qaStatus) {
        ProjectCompetitorConfigVO vo = new ProjectCompetitorConfigVO();
        vo.setId(7L);
        vo.setProjectId(11L);
        vo.setCompetitorName(name);
        vo.setAliases(aliases);
        vo.setDisplayOrder(displayOrder);
        vo.setStatus(status);
        vo.setQaStatus(qaStatus);
        vo.setConfigVersion(1);
        return vo;
    }

    private ProjectCompetitorConfigRequest.Item item(Long id,
                                                     String name,
                                                     List<String> aliases,
                                                     int displayOrder,
                                                     boolean active,
                                                     String qaStatus) {
        ProjectCompetitorConfigRequest.Item item = new ProjectCompetitorConfigRequest.Item();
        item.setId(id);
        item.setCompetitorName(name);
        item.setAliases(aliases);
        item.setDisplayOrder(displayOrder);
        item.setActive(active);
        item.setQaStatus(qaStatus);
        return item;
    }

    private record CapturedUpdate(String sql, Object[] args) {
    }
}
