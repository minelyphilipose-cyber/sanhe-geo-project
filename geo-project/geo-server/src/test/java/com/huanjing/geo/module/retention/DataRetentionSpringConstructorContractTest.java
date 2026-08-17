package com.huanjing.geo.module.retention;

import com.huanjing.geo.module.retention.detailcleanup.DataRetentionDetailCleanupReadinessService;
import com.huanjing.geo.module.retention.detailcleanup.DataRetentionDetailCleanupService;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchManagementService;
import com.huanjing.geo.module.retention.execute.DataRetentionExecuteCommandFactory;
import com.huanjing.geo.module.retention.execute.DataRetentionExecuteCoordinator;
import com.huanjing.geo.module.retention.execute.DataRetentionExecutionBudgetGate;
import com.huanjing.geo.module.retention.poll.PollRetentionDomainExecutor;
import com.huanjing.geo.module.retention.service.PollRetentionDryRunService;
import com.huanjing.geo.module.retention.service.PollRetentionSliceWriteService;
import com.huanjing.geo.module.retention.service.WebsitePublishedContentCleanupService;
import com.huanjing.geo.module.retention.website.WebsiteRetentionDomainExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DataRetentionSpringConstructorContractTest {

    private static final List<Class<?>> MULTI_CONSTRUCTOR_BEANS = List.of(
            WebsitePublishedContentCleanupService.class,
            PollRetentionDryRunService.class,
            DataRetentionExecuteCommandFactory.class,
            DataRetentionExecutionBudgetGate.class,
            WebsiteRetentionDomainExecutor.class,
            PollRetentionDomainExecutor.class,
            PollRetentionSliceWriteService.class,
            DataRetentionExecuteCoordinator.class,
            DataRetentionDetailCleanupReadinessService.class,
            DataRetentionDetailCleanupService.class,
            DataRetentionDynamicSwitchManagementService.class);

    @Test
    void everyMultiConstructorRetentionBeanHasOneUsableInjectionConstructor() {
        for (Class<?> beanType : MULTI_CONSTRUCTOR_BEANS) {
            List<Constructor<?>> injectionConstructors = Arrays.stream(beanType.getDeclaredConstructors())
                    .filter(constructor -> constructor.isAnnotationPresent(Autowired.class))
                    .toList();
            assertThat(injectionConstructors)
                    .as("%s injection constructors", beanType.getName())
                    .hasSize(1);

            try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
                for (Class<?> dependencyType : injectionConstructors.get(0).getParameterTypes()) {
                    registerMock(context, dependencyType);
                }
                registerBean(context, beanType);
                context.refresh();
                assertThat(context.getBean(beanType)).isNotNull();
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void registerMock(AnnotationConfigApplicationContext context, Class<?> type) {
        context.registerBean((Class) type, () -> mock(type));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void registerBean(AnnotationConfigApplicationContext context, Class<?> type) {
        context.registerBean((Class) type);
    }
}
