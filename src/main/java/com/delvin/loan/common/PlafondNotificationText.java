package com.delvin.loan.common;

import com.delvin.loan.event.PlafondDecisionEvent;

public final class PlafondNotificationText {

    private PlafondNotificationText() {}

    public static String title(PlafondDecisionEvent event) {
        return event.approved()
                ? "Kenaikan limit disetujui"
                : "Kenaikan limit ditolak";
    }

    public static String pushBody(PlafondDecisionEvent event) {

        if (event.approved()) {
            return "Limit Anda kini " + LoanNotificationText.rupiah(event.approvedAmount())
                    + (event.grantedLevel() == null ? "" : " (plafond level " + event.grantedLevel() + ")")
                    + ". Anda dapat langsung mengajukan pinjaman.";
        }

        return "Permintaan kenaikan limit " + LoanNotificationText.rupiah(event.requestedAmount())
                + " tidak disetujui. Ketuk untuk melihat alasannya.";
    }

    public static String emailSubject(PlafondDecisionEvent event) {
        return event.approved()
                ? "Kenaikan limit " + event.requestId() + " disetujui"
                : "Kenaikan limit " + event.requestId() + " ditolak";
    }

    public static String emailBody(PlafondDecisionEvent event) {
        return event.approved() ? approvedBody(event) : rejectedBody(event);
    }

    private static String approvedBody(PlafondDecisionEvent event) {
        return """
                Halo %s,

                Permintaan kenaikan limit Anda (%s) telah disetujui.

                Diminta   : %s
                Disetujui : %s
                Plafond   : level %s

                Limit baru ini langsung berlaku - Anda dapat mengajukan pinjaman
                sampai jumlah tersebut melalui aplikasi QuickDuit.

                Pesan ini dikirim otomatis. Mohon tidak membalas email ini.
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
                Halo %s,

                Mohon maaf, permintaan kenaikan limit Anda (%s) tidak dapat kami
                setujui saat ini.

                Diminta : %s
                Alasan  : %s

                Limit Anda saat ini tidak berubah. Anda dapat mengajukan kembali
                setelah melengkapi persyaratan yang diminta.

                Pesan ini dikirim otomatis. Mohon tidak membalas email ini.
                """.formatted(
                nullSafe(event.customerName()),
                event.requestId(),
                LoanNotificationText.rupiah(event.requestedAmount()),
                event.notes() == null || event.notes().isBlank()
                        ? "Tidak dicantumkan"
                        : event.notes()
        );
    }

    private static String nullSafe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
