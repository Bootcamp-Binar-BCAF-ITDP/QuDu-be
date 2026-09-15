package com.delvin.loan.configuration;

import com.delvin.loan.controller.prop.SecurityRoutes;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * API documentation, grouped by who is allowed to call what.
 *
 * Every group below takes its paths straight from {@link SecurityRoutes}, which
 * is the same array {@code SecurityConfig} matches requests against. Retyping
 * the prefixes here would create a second answer to "who can call this", and
 * the two would drift apart the first time someone adds a route to only one of
 * them. Add a prefix to SecurityRoutes and it appears in the right group here
 * on the next start, with no edit to this file.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI quduOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("QuickDuit Loan API")
                        .version("v1")
                        .description("""
                                Endpoints are grouped by the role that may call them. Pick a group
                                from the dropdown at the top right.

                                Everything outside the Public group needs a bearer token. Sign in
                                through POST /api/auth/login, then press Authorize and paste the
                                `token` from the response. Refresh tokens are not used here: the
                                access token is enough for a try-it-out call, and it expires.
                                """))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("The `token` field returned by /api/auth/login.")));
    }

    /* ---- one group per role ------------------------------------------- */

    @Bean
    public GroupedOpenApi publicApi() {
        return open("public", "Public", "Callable by anyone, no token needed.", SecurityRoutes.PUBLIC);
    }

    @Bean
    public GroupedOpenApi customerApi() {
        return secured("customer", "Customer",
                "The mobile customer app. Requires the CUSTOMER role.",
                SecurityRoutes.CUSTOMER);
    }

    @Bean
    public GroupedOpenApi marketingApi() {
        return secured("marketing", "Marketing",
                "First review of an incoming application. Requires the MARKETING role.",
                SecurityRoutes.MARKETING);
    }

    @Bean
    public GroupedOpenApi branchManagerApi() {
        return secured("branch-manager", "Branch Manager",
                "Approvals and limit increase decisions. Requires the BRANCH_MANAGER role.",
                SecurityRoutes.BRANCH_MANAGER);
    }

    @Bean
    public GroupedOpenApi backOfficeApi() {
        return secured("back-office", "Back Office",
                "Verification calls and disbursement. Requires the BACK_OFFICE role.",
                SecurityRoutes.BACKOFFICE);
    }

    /**
     * Master data. Excludes the public paths on purpose: /api/plafonds/catalog
     * sits under /api/plafonds/** but SecurityConfig matches PUBLIC first, so it
     * is reachable without a token. Listing it here would tell a reader it needs
     * SUPERADMIN, which is the opposite of the truth.
     */
    @Bean
    public GroupedOpenApi superadminApi() {
        return GroupedOpenApi.builder()
                .group("superadmin")
                .displayName("Superadmin")
                .pathsToMatch(SecurityRoutes.SUPERADMIN)
                .pathsToExclude(SecurityRoutes.PUBLIC)
                .addOpenApiCustomizer(requiresToken(
                        "Users, roles, menus, branches and plafond tiers. Requires the SUPERADMIN role."))
                .build();
    }

    /**
     * Not a role. These endpoints are open to all four internal roles at once,
     * so they cannot sit in any single group above without misreporting who may
     * call them.
     */
    @Bean
    public GroupedOpenApi loanApplicationsApi() {
        return secured("loan-applications", "Loan Applications (shared)",
                "Reading applications and buckets. Open to SUPERADMIN, MARKETING, "
                        + "BRANCH_MANAGER and BACK_OFFICE.",
                SecurityRoutes.LOAN_APPLICATIONS);
    }

    /* ---- helpers ------------------------------------------------------- */

    private GroupedOpenApi open(String group, String display, String note, String[] paths) {
        return GroupedOpenApi.builder()
                .group(group)
                .displayName(display)
                .pathsToMatch(paths)
                .addOpenApiCustomizer(api -> api.getInfo().setDescription(note))
                .build();
    }

    private GroupedOpenApi secured(String group, String display, String note, String[] paths) {
        return GroupedOpenApi.builder()
                .group(group)
                .displayName(display)
                .pathsToMatch(paths)
                .addOpenApiCustomizer(requiresToken(note))
                .build();
    }

    /**
     * Applies the bearer requirement to the whole group rather than to each
     * controller. Annotating 17 controllers by hand would be one more place to
     * forget, and the Public group would still need its lock icon removed.
     */
    private OpenApiCustomizer requiresToken(String note) {
        return api -> {
            api.getInfo().setDescription(note);
            api.addSecurityItem(new SecurityRequirement().addList(BEARER));
        };
    }
}
