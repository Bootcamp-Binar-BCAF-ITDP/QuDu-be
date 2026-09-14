package com.delvin.loan.service;

import com.delvin.loan.common.AccountType;
import com.delvin.loan.common.RoleName;
import com.delvin.loan.dto.response.auth.AuthResponse;
import com.delvin.loan.exception.BusinessException;
import com.delvin.loan.model.AppUser;
import com.delvin.loan.model.Role;
import com.delvin.loan.model.User;
import com.delvin.loan.repository.BranchRepository;
import com.delvin.loan.repository.CustomerRepository;
import com.delvin.loan.repository.PasswordResetTokenRepository;
import com.delvin.loan.repository.RegistrationOtpRepository;
import com.delvin.loan.repository.RoleRepository;
import com.delvin.loan.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthRefreshTest {

    private static final String STAFF = "marketing@example.com";
    private static final String CUSTOMER = "customer@example.com";

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private RegistrationOtpRepository registrationOtpRepository;
    @Mock private EmailService emailService;
    @Mock private CustomerRepository customerRepository;
    @Mock private PlafondService plafondService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtService jwtService;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private AppUserDetailsService appUserDetailsService;

    @InjectMocks
    private AuthService service;

    private AppUser principal(String username, String userId, AccountType type, String role) {
        AppUser appUser = new AppUser();
        appUser.setUsername(username);
        appUser.setUserId(userId);
        appUser.setAccountType(type);
        appUser.setRole(role);
        return appUser;
    }

    private void stubRotation(String subject) {
        when(refreshTokenService.rotate(eq("old-refresh"), any()))
                .thenReturn(new RefreshTokenService.RotationResult(
                        subject,
                        new RefreshTokenService.IssuedToken("new-refresh", Instant.now())));
    }

    private void stubStaffLookup() {
        User user = new User();
        user.setUserId("USR-1");
        Role role = new Role();
        role.setRoleName(RoleName.MARKETING);
        role.setRoleMenus(new ArrayList<>());
        user.setRole(role);

        when(userRepository.findById("USR-1")).thenReturn(Optional.of(user));
    }

    @Test
    @DisplayName("a refresh returns a new access token and the rotated refresh token")
    void refreshReturnsBothTokens() {
        stubRotation(STAFF);
        when(appUserDetailsService.loadUserByUsername(STAFF))
                .thenReturn(principal(STAFF, "USR-1", AccountType.USER, RoleName.MARKETING));
        when(jwtService.reissue(any(), any())).thenReturn("new-access");
        when(jwtService.accessTtlSeconds()).thenReturn(900L);
        stubStaffLookup();

        AuthResponse response = service.refresh("old-refresh");

        assertThat(response.getToken()).isEqualTo("new-access");
        assertThat(response.getRefreshToken()).isEqualTo("new-refresh");
        assertThat(response.getExpiresIn()).isEqualTo(900L);
        assertThat(response.getRole()).isEqualTo(RoleName.MARKETING);
    }

    @Test
    @DisplayName("the principal is reloaded from the database, not trusted from the old token")
    void principalIsReloaded() {
        stubRotation(STAFF);
        when(appUserDetailsService.loadUserByUsername(STAFF))
                .thenReturn(principal(STAFF, "USR-1", AccountType.USER, RoleName.MARKETING));
        when(jwtService.reissue(any(), any())).thenReturn("new-access");
        when(jwtService.accessTtlSeconds()).thenReturn(900L);
        stubStaffLookup();

        service.refresh("old-refresh");

        verify(appUserDetailsService).loadUserByUsername(STAFF);
    }

    @Test
    @DisplayName("a customer refresh carries no menus, because customers have none")
    void customerRefreshCarriesNoMenus() {
        stubRotation(CUSTOMER);
        when(appUserDetailsService.loadUserByUsername(CUSTOMER))
                .thenReturn(principal(CUSTOMER, "CUST-1", AccountType.CUSTOMER, "CUSTOMER"));
        when(jwtService.reissue(any(), any())).thenReturn("new-access");
        when(jwtService.accessTtlSeconds()).thenReturn(900L);

        AuthResponse response = service.refresh("old-refresh");

        assertThat(response.getMenus()).isEmpty();
        assertThat(response.getUserId()).isEqualTo("CUST-1");
        verify(userRepository, never()).findById(any());
    }

    @Test
    @DisplayName("a principal deleted or deactivated since sign-in cannot renew")
    void deletedPrincipalCannotRenew() {
        stubRotation(STAFF);
        when(appUserDetailsService.loadUserByUsername(STAFF))
                .thenThrow(new UsernameNotFoundException("User is not active"));

        assertThatThrownBy(() -> service.refresh("old-refresh"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("session has expired")
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("that refusal also revokes every remaining token for them")
    void deletedPrincipalIsFullyRevoked() {
        stubRotation(STAFF);
        when(appUserDetailsService.loadUserByUsername(STAFF))
                .thenThrow(new UsernameNotFoundException("User is not active"));

        assertThatThrownBy(() -> service.refresh("old-refresh"))
                .isInstanceOf(BusinessException.class);

        verify(refreshTokenService).revokeAllFor(eq(STAFF), any());
    }

    @Test
    @DisplayName("a rotation failure is passed straight through as a 401")
    void rotationFailurePropagates() {
        when(refreshTokenService.rotate(any(), any()))
                .thenThrow(BusinessException.unauthorized("Your session has expired. Sign in again."));

        assertThatThrownBy(() -> service.refresh("stale"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        verify(appUserDetailsService, never()).loadUserByUsername(any());
    }

    @Test
    @DisplayName("signing out hands the token to the revoker")
    void logoutRevokes() {
        service.logout("some-refresh");

        verify(refreshTokenService).revoke(eq("some-refresh"), any());
    }
}
