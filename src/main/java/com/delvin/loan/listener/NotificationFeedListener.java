package com.delvin.loan.listener;

import com.delvin.loan.common.LoanNotificationText;
import com.delvin.loan.common.NotificationType;
import com.delvin.loan.common.PlafondNotificationText;
import com.delvin.loan.event.LoanStatusChangedEvent;
import com.delvin.loan.event.PlafondDecisionEvent;
import com.delvin.loan.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationFeedListener {

    private final NotificationService notificationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLoanStatusChanged(LoanStatusChangedEvent event) {
        try {
            notificationService.record(
                    event.customerId(),
                    NotificationType.LOAN_STATUS,
                    LoanNotificationText.title(event),
                    LoanNotificationText.pushBody(event),
                    event.applicationId()
            );
        } catch (Exception e) {
            log.error("Could not store notification for application {}", event.applicationId(), e);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPlafondDecision(PlafondDecisionEvent event) {
        try {
            notificationService.record(
                    event.customerId(),
                    NotificationType.PLAFOND_DECISION,
                    PlafondNotificationText.title(event),
                    PlafondNotificationText.pushBody(event),
                    event.requestId()
            );
        } catch (Exception e) {
            log.error("Could not store notification for plafond request {}", event.requestId(), e);
        }
    }
}
