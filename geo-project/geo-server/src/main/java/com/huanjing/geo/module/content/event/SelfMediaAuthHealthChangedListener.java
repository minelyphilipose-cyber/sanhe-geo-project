package com.huanjing.geo.module.content.event;

import com.huanjing.geo.module.content.service.SelfMediaAccountHealthAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class SelfMediaAuthHealthChangedListener {

    private final SelfMediaAccountHealthAlertService alertService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void refreshAccountAlert(SelfMediaAuthHealthChangedEvent event) {
        if (event == null || event.selfMediaAccountId() == null) {
            return;
        }
        try {
            alertService.refreshAccountAlert(event.selfMediaAccountId());
        } catch (Exception ex) {
            log.warn("Failed to refresh self-media auth alert after health change, accountId={}",
                    event.selfMediaAccountId(), ex);
        }
    }
}
