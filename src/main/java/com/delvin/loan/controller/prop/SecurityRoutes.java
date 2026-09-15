package com.delvin.loan.controller.prop;

public class SecurityRoutes {

    private SecurityRoutes() {
    }

    public static final String[] PUBLIC = {
            "/api/auth/**",
            "/api/dashboard/**",

            "/api/plafonds/catalog"
    };

    /**
     * The OpenAPI document and the page that renders it.
     *
     * Kept apart from PUBLIC rather than folded into it for two reasons. These
     * are not API endpoints, and OpenApiConfig builds its Public documentation
     * group from PUBLIC, so mixing them in would describe the docs as part of
     * the API. And closing the docs to the outside is then one line here rather
     * than picking entries out of a shared list.
     *
     * Without these the Swagger UI answers 401 and shows an empty screen with
     * no hint why.
     */
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
