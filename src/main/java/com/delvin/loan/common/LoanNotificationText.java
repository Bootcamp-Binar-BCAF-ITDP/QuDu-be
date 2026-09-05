package com.delvin.loan.common;

import com.delvin.loan.event.LoanStatusChangedEvent;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

public final class LoanNotificationText {

    private LoanNotificationText() {}

    public static String title(LoanStatusChangedEvent event) {
        return event.approved()
                ? "Pengajuan pinjaman disetujui"
                : "Pengajuan pinjaman ditolak";
    }

    public static String pushBody(LoanStatusChangedEvent event) {
        if (event.approved()) {
            return "Dana " + rupiah(event.amount()) + " untuk pengajuan "
                    + event.applicationId() + " telah dicairkan.";
        }
        return "Pengajuan " + event.applicationId() + " ditolak pada tahap "
                + stageLabel(event.status()) + ". Ketuk untuk melihat detail.";
    }

    public static String emailSubject(LoanStatusChangedEvent event) {
        return event.approved()
                ? "Pinjaman " + event.applicationId() + " telah dicairkan"
                : "Pengajuan pinjaman " + event.applicationId() + " ditolak";
    }

    public static String emailBody(LoanStatusChangedEvent event) {
        return event.approved() ? approvedBody(event) : rejectedBody(event);
    }

    private static String approvedBody(LoanStatusChangedEvent event) {
        return """
                Halo %s,

                Pengajuan pinjaman %s Anda telah disetujui dan dicairkan.

                Jumlah   : %s
                Bank     : %s
                Rekening : %s

                Dana akan masuk ke rekening Anda dalam satu hari kerja.
                Jika Anda tidak merasa mengajukan, segera hubungi cabang Anda.

                Pesan ini dikirim otomatis. Mohon tidak membalas email ini.
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
                Halo %s,

                Mohon maaf, pengajuan pinjaman %s Anda tidak dapat kami lanjutkan.

                Tahap    : %s
                Jumlah   : %s
                Alasan   : %s

                Anda dapat mengajukan kembali setelah melengkapi persyaratan.

                Pesan ini dikirim otomatis. Mohon tidak membalas email ini.
                """.formatted(
                event.customerName(),
                event.applicationId(),
                stageLabel(event.status()),
                rupiah(event.amount()),
                event.decisionNote() == null || event.decisionNote().isBlank()
                        ? "Tidak dicantumkan"
                        : event.decisionNote()
        );
    }

    public static String stageLabel(String status) {
        return switch (status == null ? "" : status) {
            case LoanStatus.REJECTED_BY_MARKETING -> "review marketing";
            case LoanStatus.REJECTED_BY_BRANCH_MANAGER -> "persetujuan branch manager";
            case LoanStatus.REJECTED_BY_BACK_OFFICE -> "verifikasi back office";
            case LoanStatus.DISBURSED -> "pencairan";
            default -> status;
        };
    }

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
