package com.delvin.loan.common;

public enum StatusGroup {
    PENDING,
    APPROVED,
    REJECTED,
    OTHER;

    public static StatusGroup of(String status) {
        if (status == null) return OTHER;
        if (status.startsWith("REJECTED_")) return REJECTED;
        if (LoanStatus.VERIFIED.equals(status) || LoanStatus.DISBURSED.equals(status)) return APPROVED;
        if (LoanStatus.CHECKING.equals(status) || status.startsWith("PENDING_")) return PENDING;
        return OTHER;
    }
}