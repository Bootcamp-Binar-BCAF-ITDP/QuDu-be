package com.delvin.loan.common;

public class LoanStatus {
    private LoanStatus() {}


    public static final String CHECKING = "CHECKING";

    public static final String REJECTED_BY_MARKETING = "REJECTED_BY_MARKETING";

    public static final String PENDING_BRANCH_MANAGER = "PENDING_BRANCH_MANAGER";

    public static final String REJECTED_BY_BRANCH_MANAGER = "REJECTED_BY_BRANCH_MANAGER";

    public static final String PENDING_BACK_OFFICE = "PENDING_BACK_OFFICE";

    public static final String VERIFIED = "VERIFIED";

    public static final String DISBURSED = "DISBURSED";

    public static boolean isTerminal(String status) {
        return REJECTED_BY_MARKETING.equals(status)
                || REJECTED_BY_BRANCH_MANAGER.equals(status)
                || DISBURSED.equals(status);
    }
}
