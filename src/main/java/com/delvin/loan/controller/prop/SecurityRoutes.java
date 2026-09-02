package com.delvin.loan.controller.prop;

public class SecurityRoutes {

    private SecurityRoutes() {
    }

    // PUBLIC
    public static final String[] PUBLIC = {
            "/api/auth/**",
            "/api/dashboard/**"
    };

    // CUSTOMER
    public static final String[] CUSTOMER = {
        "/api/customer/**"

    };

    // SHARED
    public static final String[] LOAN_APPLICATIONS = {
            "/api/loan-applications/**"
    };

    // SUPER ADMIN
    public static final String[] SUPERADMIN = {
            "/api/users/**",
            "/api/roles/**",
            "/api/menus/**",
            "/api/branches/**",
            "/api/plafonds/**"
    };

    // MARKETING
    public static final String[] MARKETING = {
            "/api/loan-reviews/**",
            "/api/marketing"
    };

    // BRANCH MANAGER
    public static final String[] BRANCH_MANAGER = {
            "/api/loan-approvals/**",
            "/api/bm/**"
    };

    // BACKOFFICE
    public static final String[] BACKOFFICE = {
            "/api/loan-verifications/**",
            "/api/loan-disbursements/**",
            "/api/bo"
    };
}
