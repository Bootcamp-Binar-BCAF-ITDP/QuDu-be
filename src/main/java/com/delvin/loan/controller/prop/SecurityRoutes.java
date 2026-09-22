package com.delvin.loan.controller.prop;

public class SecurityRoutes {

    private SecurityRoutes() {
    }

    public static final String[] PUBLIC = {
            "/api/auth/**",
            "/api/dashboard/**",
            "/api/plafonds/catalog",
            "/api/branches/options"
    };

    public static final String[] DOCS = {
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**"
    };

    public static final String[] CUSTOMER = {
        "/api/customer/**"
    };

    public static final String[] LOAN_APPLICATIONS = {
            "/api/loan-applications/**"
    };

    public static final String[] SUPERADMIN = {
            "/api/users/**",
            "/api/roles/**",
            "/api/menus/**",
            "/api/branches/**",
            "/api/plafonds/**"
    };

    public static final String[] MARKETING = {
            "/api/loan-reviews/**",
            "/api/marketing"
    };

    public static final String[] BRANCH_MANAGER = {
            "/api/loan-approvals/**",
            "/api/bm/**"
    };

    public static final String[] BACKOFFICE = {
            "/api/loan-verifications/**",
            "/api/loan-disbursements/**",
            "/api/bo"
    };
}
