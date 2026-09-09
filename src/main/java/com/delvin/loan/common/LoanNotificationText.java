package com.delvin.loan.common;

import com.delvin.loan.event.LoanStatusChangedEvent;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

public final class LoanNotificationText {

    private LoanNotificationText() {}

    public static String title(LoanStatusChangedEvent event) {
        return event.approved()
                ? "Loan application approved"
                : "Loan application rejected";
    }

    public static String pushBody(LoanStatusChangedEvent event) {
        if (event.approved()) {
            return "Funds of " + rupiah(event.amount()) + " for application "
                    + event.applicationId() + " have been disbursed.";
        }
        return "Application " + event.applicationId() + " was rejected at the "
                + stageLabel(event.status()) + " stage. Tap to see the details.";
    }

    public static String emailSubject(LoanStatusChangedEvent event) {
        return event.approved()
                ? "Loan " + event.applicationId() + " has been disbursed"
                : "Loan application " + event.applicationId() + " was rejected";
    }

    public static String emailBody(LoanStatusChangedEvent event) {
        return event.approved() ? approvedBody(event) : rejectedBody(event);
    }

    private static String approvedBody(LoanStatusChangedEvent event) {
        return """
                Hello %s,

                Your loan application %s has been approved and disbursed.

                Amount  : %s
                Bank    : %s
                Account : %s

                The funds will reach your account within one business day.
                If you did not make this application, contact your branch at once.

                This message was sent automatically. Please do not reply to it.
                """.formatted(
                event.customerName(),
                event.applicationId(),
                rupiah(event.amount()),
                nullSafe(event.bankName()),
                mask(event.accountNumber())
        );
    }

    private static String rejectedBody(LoanStatusChangedEvent event) {
        return """
                Hello %s,

                We are sorry - your loan application %s cannot be taken further.

                Stage  : %s
                Amount : %s
                Reason : %s

                You are welcome to apply again once the requirements are met.

                This message was sent automatically. Please do not reply to it.
                """.formatted(
                event.customerName(),
                event.applicationId(),
                stageLabel(event.status()),
                rupiah(event.amount()),
                event.decisionNote() == null || event.decisionNote().isBlank()
                        ? "Not stated"
                        : event.decisionNote()
        );
    }

    public static String stageLabel(String status) {
        return switch (status == null ? "" : status) {
            case LoanStatus.REJECTED_BY_MARKETING -> "marketing review";
            case LoanStatus.REJECTED_BY_BRANCH_MANAGER -> "branch manager approval";
            case LoanStatus.REJECTED_BY_BACK_OFFICE -> "back office verification";
            case LoanStatus.DISBURSED -> "disbursement";
            default -> status;
        };
    }

    /**
     * Amounts stay in rupiah regardless of the interface language - the money
     * itself is IDR, so an en-US format here would print the wrong currency.
     */
    public static String rupiah(BigDecimal amount) {
        if (amount == null) return "-";
        NumberFormat format = NumberFormat.getCurrencyInstance(Locale.of("id", "ID"));
        format.setMaximumFractionDigits(0);
        return format.format(amount);
    }

    public static String mask(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= 4) return "****";
        return "*".repeat(accountNumber.length() - 4)
                + accountNumber.substring(accountNumber.length() - 4);
    }

    private static String nullSafe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
