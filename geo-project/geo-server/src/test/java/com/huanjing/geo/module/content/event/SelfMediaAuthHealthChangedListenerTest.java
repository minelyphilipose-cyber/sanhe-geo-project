package com.huanjing.geo.module.content.event;

import com.huanjing.geo.module.content.service.SelfMediaAccountHealthAlertService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SelfMediaAuthHealthChangedListenerTest {

    @Test
    void refreshesOnlyChangedAccountAndDoesNotPropagateAlertFailures() {
        SelfMediaAccountHealthAlertService alertService = mock(SelfMediaAccountHealthAlertService.class);
        SelfMediaAuthHealthChangedListener listener = new SelfMediaAuthHealthChangedListener(alertService);

        listener.refreshAccountAlert(new SelfMediaAuthHealthChangedEvent(10L));
        verify(alertService).refreshAccountAlert(10L);

        doThrow(new RuntimeException("alert backend unavailable"))
                .when(alertService).refreshAccountAlert(11L);
        assertDoesNotThrow(() -> listener.refreshAccountAlert(new SelfMediaAuthHealthChangedEvent(11L)));
    }
}
