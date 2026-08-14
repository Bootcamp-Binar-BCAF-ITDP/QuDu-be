package com.delvin.loan.controller.prop;

public class SecurityRoutes {

    private SecurityRoutes() {
    }

    // PUBLIC
    public static final String[] PUBLIC = {
            "/api/auth/**",
            "/api/loan-applications/**"
    };

    // SUPER ADMIN
    public static final String[] SUPERADMIN = {
            "/api/users/**",
            "/api/roles/**",
            "/api/menus/**",
            "/api/branches/**"
    };

    // MARKETING
    public static final String[] MARKETING = {
            "/api/loan-reviews/**",
            "/api/marketing"
    };

    // BRANCH MANAGER
    public static final String[] BRANCH_MANAGER = {
            "/api/loan-approvals/**",
            "/api/bm"
    };

    // BACKOFFICE
    public static final String[] BACKOFFICE = {
            "/api/loan-verifications/**",
            "/api/loan-disbursements/**",
            "/api/bo",
            "/api/loan-verifications/**"
    };
}
