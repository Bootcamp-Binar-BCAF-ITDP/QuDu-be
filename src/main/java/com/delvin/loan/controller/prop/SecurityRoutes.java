package com.delvin.loan.controller.prop;

public class SecurityRoutes {

    private SecurityRoutes() {
    }

    // PUBLIC
    public static final String[] PUBLIC = {
            "/api/auth/**",
            "/api/loan-applications/{applicationId}/documents"
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
//            "/api/loan-applications/{applicationId}/documents"
    };

    // BRANCH MANAGER
    public static final String[] BRANCH_MANAGER = {
            "/api/loan-approvals/**",
            "/api/loan-applications/bucket/branch-manager",
//            "/api/loan-applications/{applicationId}/documents"
    };

    // BACKOFFICE
    public static final String[] BACKOFFICE = {
            "/api/loan-verifications/**",
            "/api/loan-disbursements/**",
            "/api/loan-applications/bucket/back-office",
            "/bucket/back-office",
//            "/api/loan-applications/{applicationId}/documents",
            "/api/loan-verifications/**"
    };
}
