package com.huanjing.geo.module.content.mapper;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SelfMediaPublishScheduleMapperSqlTest {

    @Test
    void plainAnnotationSqlShouldNotContainXmlEscapedOperators() {
        List<String> offenders = new ArrayList<>();
        for (Method method : SelfMediaPublishScheduleMapper.class.getDeclaredMethods()) {
            inspectSql(method, offenders);
        }
        assertTrue(offenders.isEmpty(),
                "非 <script> 注解 SQL 不应包含 XML 转义比较符: " + String.join(", ", offenders));
    }

    private void inspectSql(Method method, List<String> offenders) {
        String sql = annotationSql(method);
        if (sql == null || sql.contains("<script>")) {
            return;
        }
        if (sql.contains("&lt;") || sql.contains("&gt;")) {
            offenders.add(method.getName());
        }
    }

    private String annotationSql(Method method) {
        Select select = method.getAnnotation(Select.class);
        if (select != null) {
            return String.join("\n", Arrays.asList(select.value()));
        }
        Update update = method.getAnnotation(Update.class);
        if (update != null) {
            return String.join("\n", Arrays.asList(update.value()));
        }
        return null;
    }
}
