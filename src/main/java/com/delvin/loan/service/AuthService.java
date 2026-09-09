package com.delvin.loan.service;

import com.delvin.loan.common.AccountType;
import com.delvin.loan.common.OtpCodes;
import com.delvin.loan.dto.response.auth.RegisterResponse;
import com.delvin.loan.dto.response.menu.MenuResponse;
import com.delvin.loan.exception.BusinessException;
import com.delvin.loan.model.*;
import com.delvin.loan.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import com.delvin.loan.dto.request.auth.LoginRequest;
import com.delvin.loan.dto.request.auth.RegisterRequest;
import com.delvin.loan.dto.response.auth.AuthResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import com.delvin.loan.dto.request.auth.ForgotPasswordRequest;
import com.delvin.loan.dto.request.auth.ResetPasswordRequest;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final String INVALID_CREDENTIALS = "Wrong username or password";

    private static final int RESET_TOKEN_TTL_MINUTES = 15;

   private static final int MAX_RESET_ATTEMPTS = 5;

    private static final java.security.SecureRandom RESET_CODES = new java.security.SecureRandom();

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final BranchRepository branchRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RegistrationOtpRepository registrationOtpRepository;
    private final EmailService emailService;
    private final CustomerRepository customerRepository;
    private final PlafondService plafondService;

    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Transactional
    public RegisterResponse register(RegisterRequest request) {

        if (request.getAccountType() == AccountType.USER) {
            return registerUser(request);
        }

        if (request.getAccountType() == AccountType.CUSTOMER) {
            return registerCustomer(request);
        }

        throw new IllegalArgumentException(
                "Invalid account type"
        );
    }

    private RegisterResponse registerUser(RegisterRequest request) {

        if (request.getUsername() == null || request.getUsername().isBlank()) {
            throw BusinessException.badRequest("Username is required");
        }

        if (userRepository.existsByUsername(request.getUsername())) {
            throw BusinessException.conflict("That username is already taken");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw BusinessException.conflict("That email is already in use");
        }

        Role role = roleRepository
                .findById(request.getRoleId())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Role not found"
                        )
                );

        Branch branch = branchRepository
                .findByBranchIdAndIsActive(
                        request.getBranchId(),
                        true
                )
                .orElseThrow(() ->
                        BusinessException.badRequest("Role not found")
                );

        User user = new User();

        user.setUserId(
                UUID.randomUUID().toString()
        );

        user.setUsername(
                request.getUsername()
        );

        user.setEmail(
                request.getEmail()
        );

        user.setPassword(
                passwordEncoder.encode(
                        request.getPassword()
                )
        );

        user.setFullName(
                request.getFullName()
        );

        user.setPhoneNumber(
                request.getPhoneNumber()
        );

        user.setBranch(branch);
        user.setRole(role);
        user.setIsActive(true);

        User savedUser = userRepository.save(user);

        return RegisterResponse.builder()
                .userId(savedUser.getUserId())
                .username(savedUser.getUsername())
                .role(role.getRoleName())
                .build();
    }

    /**
     * Issues the code that has to accompany a customer registration.
     *
     * Unlike forgot-password, this one <em>does</em> say whether the address is
     * already taken. That is deliberate and requested: a signup form has to tell
     * you the email is in use or you cannot proceed, and every registration form
     * on the web leaks exactly this much. The trade is enumeration of registered
     * addresses, which is why the same honesty is not extended to the reset flow.
     */
    @Transactional
    public void requestRegistrationOtp(String rawEmail) {

        // Trimmed but deliberately not lower-cased: email lookups everywhere in
        // this system are case-sensitive, so folding case here would let an
        // address pass this check and then fail login, or vice versa. The app
        // sends the same string to both endpoints, which is all this flow needs.
        String email = rawEmail.trim();

        // Mirrors exactly what registerCustomer checks. Checking more here would
        // turn away addresses that registration would then have accepted.
        if (customerRepository.existsByEmail(email)) {
            throw BusinessException.conflict("That email is already registered");
        }

        registrationOtpRepository.deleteByEmail(email);
        registrationOtpRepository.flush();

        RegistrationOtp otp = new RegistrationOtp();

        otp.setEmail(email);
        otp.setCode(OtpCodes.generate());
        otp.setExpiryDate(LocalDateTime.now().plusMinutes(OtpCodes.TTL_MINUTES));
        otp.setUsed(false);
        otp.setAttempts(0);
        otp.setCreatedAt(LocalDateTime.now());

        registrationOtpRepository.save(otp);

        try {
            emailService.sendRegistrationOtpEmail(email, otp.getCode(), OtpCodes.TTL_MINUTES);
        } catch (Exception e) {
            // Throwing rolls the saved row back, which is what we want: a code
            // nobody received must not sit there looking valid. Unlike
            // forgot-password there is nothing to hide by failing loudly - the
            // caller already knows whether the address is taken.
            log.error("Could not send registration OTP to {}", email, e);
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The code could not be sent. Please try again shortly.");
        }
    }

    /**
     * Consumes the registration code, or refuses the registration.
     *
     * Looked up by email rather than by code so a wrong guess has a row to be
     * counted against - the same reasoning as resolveResetToken.
     */
    private void consumeRegistrationOtp(String email, String submitted) {

        if (submitted == null || submitted.isBlank()) {
            throw BusinessException.badRequest(
                    "A verification code is required. Request one via /api/auth/register/otp.");
        }

        RegistrationOtp otp = registrationOtpRepository.findByEmail(email)
                .orElseThrow(() -> BusinessException.badRequest(
                        "No verification code has been issued for this email. Request one first."));

        if (otp.isUsed()) {
            throw BusinessException.badRequest("That verification code has already been used. Request a new one.");
        }

        if (otp.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw BusinessException.badRequest("That verification code has expired. Request a new one.");
        }

        if (otp.getAttempts() >= OtpCodes.MAX_ATTEMPTS) {
            throw BusinessException.badRequest(
                    "That verification code was disabled after too many attempts. Request a new one.");
        }

        if (!otp.getCode().equals(submitted.trim())) {
            registrationOtpRepository.incrementAttempts(otp.getId());
            throw BusinessException.badRequest("That verification code is not valid.");
        }

        otp.setUsed(true);
        registrationOtpRepository.save(otp);
    }

    private RegisterResponse registerCustomer(RegisterRequest request) {

        String email = request.getEmail().trim();

        if (customerRepository.existsByEmail(email)) {
            throw BusinessException.conflict("That email is already registered");
        }

        // Before the NIK check on purpose: a wrong code should not be told
        // whether the NIK it came with is already on file.
        consumeRegistrationOtp(email, request.getOtp());

        if (customerRepository.existsByNik(request.getNik())) {
            throw BusinessException.conflict("That NIK is already in use");
        }

        Customer customer = new Customer();

        customer.setCustomerId(UUID.randomUUID().toString());

        plafondService.assignDefaultPlafond(customer);

        customer.setCustomerName(
                request.getFullName()
        );

        customer.setEmail(
                request.getEmail()
        );

        customer.setPassword(
                passwordEncoder.encode(
                        request.getPassword()
                )
        );

        customer.setPhoneNumber(
                request.getPhoneNumber()
        );

        customer.setNik(
                request.getNik()
        );

        customer.setAddress(
                request.getAddress()
        );

        customer.setSex(
                request.getSex()
        );

        customer.setBirthPlace(
                request.getBirthPlace()
        );

        customer.setBirthDate(
                request.getBirthDate()
        );

        customer.setOccupation(
                request.getOccupation()
        );

        customer.setCitizenship(
                request.getCitizenship()
        );

        Customer savedCustomer =
                customerRepository.save(customer);

        return RegisterResponse.builder()
                .userId(savedCustomer.getCustomerId())
                .username(savedCustomer.getEmail())
                .role("CUSTOMER")
                .build();
    }

    public AuthResponse login(LoginRequest request) {

        return switch (request.getAccountType()) {

            case USER -> loginUser(request);

            case CUSTOMER -> loginCustomer(request);
        };
    }

    private AuthResponse loginUser(LoginRequest request) {

        User user = userRepository
                .findByUsernameOrEmail(
                        request.getUsernameOrEmail(),
                        request.getUsernameOrEmail()
                )
                .orElseThrow(() ->
                        BusinessException.unauthorized(INVALID_CREDENTIALS)
                );

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new IllegalArgumentException(
                    "User is not active"
            );
        }

        if (!passwordEncoder.matches(
                request.getPassword(),
                user.getPassword()
        )) {
            throw new IllegalArgumentException(
                    "Wrong password"
            );
        }

        AppUser appUser = toAppUser(user);

        String token = jwtService.issue(
                appUser,
                java.time.Instant.now()
        );

        List<MenuResponse> menus =
                getUserMenus(user.getUserId());

        return AuthResponse.builder()
                .token(token)
                .userId(user.getUserId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(
                        user.getRole() != null
                                ? user.getRole().getRoleName()
                                : null
                )
                .menus(menus)
                .build();
    }

    private AuthResponse loginCustomer(LoginRequest request) {

        Customer customer = customerRepository
                .findByEmail(request.getUsernameOrEmail())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Customer email not found"
                        )
                );

        if (!passwordEncoder.matches(
                request.getPassword(),
                customer.getPassword()
        )) {
            throw new IllegalArgumentException(
                    "Wrong password"
            );
        }

        AppCustomer appCustomer = toAppCustomer(customer);

        String token = jwtService.issue(
                appCustomer,
                java.time.Instant.now()
        );

        return AuthResponse.builder()
                .token(token)
                .userId(customer.getCustomerId())
                .username(customer.getEmail())
                .email(customer.getEmail())
                .fullName(customer.getCustomerName())
                .menus(Collections.emptyList())
                .build();
    }

    @Async
    @Transactional
    public void forgotPassword(
            ForgotPasswordRequest request
    ) {
        try {
            String email = request.getEmail();
            AccountType requestedType = request.getAccountType();

            if (requestedType != AccountType.CUSTOMER) {

                User user = userRepository.findByEmail(email).orElse(null);

                if (user != null) {
                    createAndSendResetToken(user, null, AccountType.USER, user.getEmail());
                    return;
                }
            }

            if (requestedType != AccountType.USER) {

                Customer customer = customerRepository.findByEmail(email).orElse(null);

                if (customer != null) {
                    createAndSendResetToken(null, customer, AccountType.CUSTOMER, customer.getEmail());
                    return;
                }
            }

            log.info("Forgot password requested for an unregistered email");

        } catch (Exception e) {
            log.warn("Gagal memproses forgot password", e);
        }
    }

    private void createAndSendResetToken(
            User user,
            Customer customer,
            AccountType accountType,
            String email
    ) {

        if (accountType == AccountType.USER) {
            passwordResetTokenRepository.deleteByUser(user);
        } else {
            passwordResetTokenRepository.deleteByCustomer(customer);
        }

        boolean isCustomer = accountType == AccountType.CUSTOMER;
        String token = isCustomer ? generateResetCode() : UUID.randomUUID().toString();

        PasswordResetToken resetToken = new PasswordResetToken();

        resetToken.setToken(token);
        resetToken.setAccountType(accountType);
        resetToken.setUser(user);
        resetToken.setCustomer(customer);
        resetToken.setExpiryDate(LocalDateTime.now().plusMinutes(RESET_TOKEN_TTL_MINUTES));
        resetToken.setUsed(false);
        resetToken.setAttempts(0);

        passwordResetTokenRepository.save(resetToken);

        if (isCustomer) {
            emailService.sendResetPasswordCodeEmail(email, token, RESET_TOKEN_TTL_MINUTES);
        } else {
            emailService.sendResetPasswordEmail(email, token);
        }
    }

    private String generateResetCode() {

        for (int attempt = 0; attempt < 5; attempt++) {

            String code = String.format("%06d", RESET_CODES.nextInt(1_000_000));

            if (passwordResetTokenRepository.findByToken(code).isEmpty()) {
                return code;
            }
        }

        throw new IllegalStateException("Could not allocate an unused reset code");
    }

    private PasswordResetToken resolveResetToken(ResetPasswordRequest request) {

        String email = request.getEmail();

        if (email == null || email.isBlank()) {
            return passwordResetTokenRepository
                    .findByToken(request.getToken())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid password reset token"));
        }

       Customer customer = customerRepository.findByEmail(email.trim())
                .orElseThrow(() -> new IllegalArgumentException("Invalid password reset code"));

        PasswordResetToken resetToken = passwordResetTokenRepository.findByCustomer(customer)
                .orElseThrow(() -> new IllegalArgumentException("Invalid password reset code"));

        if (resetToken.getAttempts() >= MAX_RESET_ATTEMPTS) {
            throw new IllegalArgumentException(
                    "That reset code was disabled after too many attempts. "
                            + "Please request a new one.");
        }

        if (!resetToken.getToken().equals(request.getToken())) {
            passwordResetTokenRepository.incrementAttempts(resetToken.getId());
            throw new IllegalArgumentException("Invalid password reset code");
        }

        return resetToken;
    }

    @Transactional
    public void resetPassword(
            ResetPasswordRequest request
    ) {

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("The password and its confirmation do not match");
        }

        PasswordResetToken resetToken = resolveResetToken(request);

        if (Boolean.TRUE.equals(resetToken.getUsed())) {

            throw new IllegalArgumentException(
                    "That password reset token has already been used"
            );
        }

        if (resetToken.getExpiryDate()
                .isBefore(LocalDateTime.now())) {

            throw new IllegalArgumentException(
                    "That password reset token has expired"
            );
        }

        String encodedPassword =
                passwordEncoder.encode(request.getNewPassword());

        if (resetToken.getAccountType() == AccountType.CUSTOMER) {

            Customer customer = resetToken.getCustomer();

            if (customer == null) {
                throw new IllegalArgumentException(
                        "Invalid password reset token"
                );
            }

            customer.setPassword(encodedPassword);

            customerRepository.save(customer);

        } else {

            User user = resetToken.getUser();

            if (user == null) {
                throw new IllegalArgumentException(
                        "Invalid password reset token"
                );
            }

            user.setPassword(encodedPassword);

            userRepository.save(user);
        }

        resetToken.setUsed(true);

        passwordResetTokenRepository.save(resetToken);
    }

    private List<MenuResponse> getUserMenus(String userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException("User not found")
                );

        if (user.getRole() == null) {
            return Collections.emptyList();
        }

        return user.getRole()
                .getRoleMenus()
                .stream()
                .filter(roleMenu -> roleMenu.getMenu() != null)
                .map(roleMenu -> MenuResponse.builder()
                        .menuId(roleMenu.getMenu().getMenuId())
                        .menuName(roleMenu.getMenu().getMenuName())
                        .build()
                )
                .toList();
    }

    private AppUser toAppUser(User user) {

        AppUser appUser = new AppUser();

        appUser.setUserId(user.getUserId());
        appUser.setUsername(user.getUsername());
        appUser.setPassword(user.getPassword());

        if (user.getRole() != null) {
            appUser.setRole(
                    user.getRole().getRoleName()
            );
        }

        return appUser;
    }

    private AppCustomer toAppCustomer(Customer customer) {

        AppCustomer appCustomer = new AppCustomer();

        appCustomer.setCustomerId(
                customer.getCustomerId()
        );

        appCustomer.setEmail(
                customer.getEmail()
        );

        appCustomer.setPassword(
                customer.getPassword()
        );

        appCustomer.setFullName(
                customer.getCustomerName()
        );

        return appCustomer;
    }
}
