package com.delvin.loan.configuration;

import com.delvin.loan.controller.prop.SecurityRoutes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springdoc.core.models.GroupedOpenApi;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {

    private final OpenApiConfig config = new OpenApiConfig();

    private List<GroupedOpenApi> allGroups() {
        return List.of(
                config.publicApi(),
                config.customerApi(),
                config.marketingApi(),
                config.branchManagerApi(),
                config.backOfficeApi(),
                config.superadminApi(),
                config.loanApplicationsApi());
    }

    @Test
    @DisplayName("every group takes its paths from SecurityRoutes, not from a retyped copy")
    void groupsMirrorSecurityRoutes() {
        assertThat(config.publicApi().getPathsToMatch())
                .containsExactly(SecurityRoutes.PUBLIC);
        assertThat(config.customerApi().getPathsToMatch())
                .containsExactly(SecurityRoutes.CUSTOMER);
        assertThat(config.marketingApi().getPathsToMatch())
                .containsExactly(SecurityRoutes.MARKETING);
        assertThat(config.branchManagerApi().getPathsToMatch())
                .containsExactly(SecurityRoutes.BRANCH_MANAGER);
        assertThat(config.backOfficeApi().getPathsToMatch())
                .containsExactly(SecurityRoutes.BACKOFFICE);
        assertThat(config.superadminApi().getPathsToMatch())
                .containsExactly(SecurityRoutes.SUPERADMIN);
        assertThat(config.loanApplicationsApi().getPathsToMatch())
                .containsExactly(SecurityRoutes.LOAN_APPLICATIONS);
    }

    @Test
    @DisplayName("the superadmin group excludes the public paths, or it would claim the plafond catalog needs a token")
    void superadminExcludesPublicPaths() {
        assertThat(config.superadminApi().getPathsToExclude())
                .containsExactly(SecurityRoutes.PUBLIC);

        assertThat(SecurityRoutes.PUBLIC).contains("/api/plafonds/catalog");
        assertThat(SecurityRoutes.SUPERADMIN).contains("/api/plafonds/**");
    }

    @Test
    @DisplayName("each group has a distinct name, or the dropdown silently loses one")
    void groupNamesAreUnique() {
        List<String> names = allGroups().stream().map(GroupedOpenApi::getGroup).toList();

        assertThat(names).doesNotHaveDuplicates();
        assertThat(names).allSatisfy(name -> assertThat(name).isNotBlank());
    }

    @Test
    @DisplayName("every group is labelled for a reader, not just slugged")
    void groupsHaveDisplayNames() {
        assertThat(allGroups())
                .extracting(GroupedOpenApi::getDisplayName)
                .allSatisfy(display -> assertThat(display).isNotBlank());
    }

    @Test
    @DisplayName("the documentation paths are permitted, or the Swagger UI answers 401")
    void documentationPathsArePublic() {
        assertThat(SecurityRoutes.DOCS)
                .contains("/v3/api-docs", "/swagger-ui.html", "/swagger-ui/**");
    }

    @Test
    @DisplayName("documentation paths are kept out of PUBLIC, so the Public group stays about the API")
    void documentationIsNotPartOfThePublicApi() {
        assertThat(SecurityRoutes.PUBLIC)
                .noneMatch(path -> path.contains("api-docs") || path.contains("swagger"));
    }

    @Test
    @DisplayName("the bearer scheme is declared once, so the Authorize button has something to fill")
    void bearerSchemeIsDeclared() {
        var components = config.quduOpenApi().getComponents();

        assertThat(components.getSecuritySchemes()).containsKey("bearerAuth");
        assertThat(components.getSecuritySchemes().get("bearerAuth").getScheme()).isEqualTo("bearer");
    }

    @Test
    @DisplayName("no role's prefixes leak into another group")
    void groupsDoNotOverlapExceptWhereIntended() {
        record Group(String name, String[] paths) {}

        List<Group> roleGroups = List.of(
                new Group("customer", SecurityRoutes.CUSTOMER),
                new Group("marketing", SecurityRoutes.MARKETING),
                new Group("branch-manager", SecurityRoutes.BRANCH_MANAGER),
                new Group("back-office", SecurityRoutes.BACKOFFICE));

        List<String> everyPath = roleGroups.stream()
                .flatMap(g -> Stream.of(g.paths()))
                .toList();

        assertThat(everyPath).doesNotHaveDuplicates();
    }
}
