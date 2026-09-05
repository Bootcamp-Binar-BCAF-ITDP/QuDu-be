package com.delvin.loan.listener;

import com.delvin.loan.common.PlafondNotificationText;
import com.delvin.loan.event.PlafondDecisionEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
public class PlafondDecisionEmailListener {

    private final JavaMailSender mailSender;
    private final String from;

    public PlafondDecisionEmailListener(
            JavaMailSender mailSender,
            @Value("${app.mail.from}") String from
    ) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDecision(PlafondDecisionEvent event) {

        if (event.customerEmail() == null || event.customerEmail().isBlank()) {
            log.warn("Plafond request {} was {} but the customer has no email on file",
                    event.requestId(), event.status());
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(event.customerEmail());
            message.setSubject(PlafondNotificationText.emailSubject(event));
            message.setText(PlafondNotificationText.emailBody(event));

            mailSender.send(message);

            log.info("Plafond decision email ({}) sent for request {}",
                    event.status(), event.requestId());
        } catch (Exception e) {
            log.error("Could not send plafond decision email for request {}", event.requestId(), e);
        }
    }
}
