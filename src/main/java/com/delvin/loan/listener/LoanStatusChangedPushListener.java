package com.delvin.loan.listener;

import com.delvin.loan.common.LoanNotificationText;
import com.delvin.loan.common.NotificationType;
import com.delvin.loan.event.LoanStatusChangedEvent;
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
public class LoanStatusChangedPushListener {

    private final PushNotificationService pushNotificationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStatusChanged(LoanStatusChangedEvent event) {

        Map<String, String> data = new HashMap<>();
        data.put("type", NotificationType.LOAN_STATUS);
        data.put("applicationId", event.applicationId());
        data.put("status", event.status());

        try {
            pushNotificationService.sendToCustomer(
                    event.customerId(),
                    LoanNotificationText.title(event),
                    LoanNotificationText.pushBody(event),
                    data
            );
        } catch (Exception e) {
            log.error("Could not push status change for application {}",
                    event.applicationId(), e);
        }
    }
}
