package com.delvin.loan.listener;

import com.delvin.loan.common.LoanNotificationText;
import com.delvin.loan.event.LoanStatusChangedEvent;
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
public class LoanStatusChangedEmailListener {

    private final JavaMailSender mailSender;
    private final String from;

    public LoanStatusChangedEmailListener(
            JavaMailSender mailSender,
            @Value("${app.mail.from}") String from
    ) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStatusChanged(LoanStatusChangedEvent event) {

        if (event.customerEmail() == null || event.customerEmail().isBlank()) {
            log.warn("Application {} reached {} but the customer has no email on file",
                    event.applicationId(), event.status());
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(event.customerEmail());
            message.setSubject(LoanNotificationText.emailSubject(event));
            message.setText(LoanNotificationText.emailBody(event));

            mailSender.send(message);

            log.info("Status email ({}) sent for application {}",
                    event.status(), event.applicationId());
        } catch (Exception e) {
            log.error("Could not send status email for application {}",
                    event.applicationId(), e);
        }
    }
}
