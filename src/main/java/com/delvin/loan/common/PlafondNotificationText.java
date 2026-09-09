package com.delvin.loan.common;

import com.delvin.loan.event.PlafondDecisionEvent;

public final class PlafondNotificationText {

    private PlafondNotificationText() {}

    public static String title(PlafondDecisionEvent event) {
        return event.approved()
                ? "Limit increase approved"
                : "Limit increase rejected";
    }

    public static String pushBody(PlafondDecisionEvent event) {

        if (event.approved()) {
            return "Your limit is now " + LoanNotificationText.rupiah(event.approvedAmount())
                    + (event.grantedLevel() == null ? "" : " (plafond level " + event.grantedLevel() + ")")
                    + ". You can apply for a loan straight away.";
        }

        return "Your limit increase request for "
                + LoanNotificationText.rupiah(event.requestedAmount())
                + " was not approved. Tap to see why.";
    }

    public static String emailSubject(PlafondDecisionEvent event) {
        return event.approved()
                ? "Limit increase " + event.requestId() + " approved"
                : "Limit increase " + event.requestId() + " rejected";
    }

    public static String emailBody(PlafondDecisionEvent event) {
        return event.approved() ? approvedBody(event) : rejectedBody(event);
    }

    private static String approvedBody(PlafondDecisionEvent event) {
        return """
                Hello %s,

                Your limit increase request (%s) has been approved.

                Requested : %s
                Approved  : %s
                Plafond   : level %s

                The new limit takes effect immediately - you can apply for a loan
                up to that amount through the QuickDuit app.

                This message was sent automatically. Please do not reply to it.
                """.formatted(
                nullSafe(event.customerName()),
                event.requestId(),
                LoanNotificationText.rupiah(event.requestedAmount()),
                LoanNotificationText.rupiah(event.approvedAmount()),
                event.grantedLevel() == null ? "-" : event.grantedLevel().toString()
        );
    }

    private static String rejectedBody(PlafondDecisionEvent event) {
        return """
                Hello %s,

                We are sorry - your limit increase request (%s) cannot be approved
                at this time.

                Requested : %s
                Reason    : %s

                Your current limit is unchanged. You are welcome to ask again once
                the requirements are met.

                This message was sent automatically. Please do not reply to it.
                """.formatted(
                nullSafe(event.customerName()),
                event.requestId(),
                LoanNotificationText.rupiah(event.requestedAmount()),
                event.notes() == null || event.notes().isBlank()
                        ? "Not stated"
                        : event.notes()
        );
    }

    private static String nullSafe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
