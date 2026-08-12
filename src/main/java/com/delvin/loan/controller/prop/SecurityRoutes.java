package com.delvin.loan.controller.prop;

public class SecurityRoutes {

    private SecurityRoutes() {
    }

    // PUBLIC

    public static final String[] PUBLIC = {
            "/api/auth/**"
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
            "/api/loan-reviews/**"
    };

    // BRANCH MANAGER

    public static final String[] BRANCH_MANAGER = {
            "/api/loan-verifications/**",
            "/api/loan-approvals/**"
    };

    // BACKOFFICE

    public static final String[] BACKOFFICE = {
            "/api/disbursements/**"
    };
}
