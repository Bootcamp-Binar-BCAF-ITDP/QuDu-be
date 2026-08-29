package com.delvin.loan.listener;

import com.delvin.loan.event.LoanDisbursedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

@Component
public class LoanDisbursedEmailListener {

    private static final Logger log = LoggerFactory.getLogger(LoanDisbursedEmailListener.class);

    private final JavaMailSender mailSender;
    private final String from;

    public LoanDisbursedEmailListener(
            JavaMailSender mailSender,
            @Value("${app.mail.from}") String from
    ) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLoanDisbursed(LoanDisbursedEvent event) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(event.customerEmail());
            message.setSubject("Your loan " + event.applicationId() + " has been disbursed");
            message.setText(body(event));

            mailSender.send(message);
            log.info("Disbursement email sent for application {}", event.applicationId());

        } catch (Exception e) {
            // The money already moved. A failed email must not surface as an error to the operator.
            log.error("Could not send disbursement email for application {}",
                    event.applicationId(), e);
        }
    }

    private String body(LoanDisbursedEvent event) {
        return """
                Dear %s,

                Your loan application %s has been disbursed.

                Amount   : %s
                Bank     : %s
                Account  : %s

                The funds should appear in your account within one business day.
                If you did not expect this, contact your branch immediately.

                This is an automated message. Please do not reply.
                """.formatted(
                event.customerName(),
                event.applicationId(),
                rupiah(event.disbursedAmount()),
                event.bankName(),
                mask(event.accountNumber())
        );
    }

    private String rupiah(BigDecimal amount) {
        if (amount == null) return "-";
        NumberFormat format = NumberFormat.getCurrencyInstance(Locale.of("id", "ID"));
        format.setMaximumFractionDigits(0);
        return format.format(amount);
    }

    /** Email is not a secure channel. Show only enough to recognise the account. */
    private String mask(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= 4) return "****";
        return "*".repeat(accountNumber.length() - 4)
                + accountNumber.substring(accountNumber.length() - 4);
    }
}