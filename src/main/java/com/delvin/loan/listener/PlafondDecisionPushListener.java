package com.delvin.loan.listener;

import com.delvin.loan.common.NotificationType;
import com.delvin.loan.common.PlafondNotificationText;
import com.delvin.loan.event.PlafondDecisionEvent;
import com.delvin.loan.service.PushNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class PlafondDecisionPushListener {

    private final PushNotificationService pushNotificationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDecision(PlafondDecisionEvent event) {

        Map<String, String> data = new HashMap<>();
        data.put("type", NotificationType.PLAFOND_DECISION);
        data.put("requestId", event.requestId());
        data.put("status", event.status() == null ? "" : event.status().name());

        try {
            pushNotificationService.sendToCustomer(
                    event.customerId(),
                    PlafondNotificationText.title(event),
                    PlafondNotificationText.pushBody(event),
                    data
            );
        } catch (Exception e) {
            log.error("Could not push plafond decision for request {}", event.requestId(), e);
        }
    }
}
