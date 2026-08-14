package com.delvin.loan.service;

import com.delvin.loan.common.AccountType;
import com.delvin.loan.dto.response.auth.RegisterResponse;
import com.delvin.loan.dto.response.menu.MenuResponse;
import com.delvin.loan.model.*;
import com.delvin.loan.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final BranchRepository branchRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailService emailService;
    private final CustomerRepository customerRepository;
    private final PlafondRepository plafondRepository;

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
                "Account type tidak valid"
        );
    }

    private RegisterResponse registerUser(RegisterRequest request) {

        if (request.getUsername() == null ||
                request.getUsername().isBlank()) {

            throw new IllegalArgumentException(
                    "Username wajib diisi"
            );
        }

        if (userRepository.existsByUsername(
                request.getUsername()
        )) {

            throw new IllegalArgumentException(
                    "Username sudah digunakan"
            );
        }

        if (userRepository.existsByEmail(
                request.getEmail()
        )) {

            throw new IllegalArgumentException(
                    "Email sudah digunakan"
            );
        }

        Role role = roleRepository
                .findById(request.getRoleId())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Role tidak ditemukan"
                        )
                );

        Branch branch = branchRepository
                .findByBranchIdAndIsActive(
                        request.getBranchId(),
                        true
                )
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Branch tidak ditemukan atau tidak aktif"
                        )
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

        User savedUser =
                userRepository.save(user);

        return RegisterResponse.builder()
                .userId(savedUser.getUserId())
                .username(savedUser.getUsername())
                .role(role.getRoleName())
                .build();
    }

    private RegisterResponse registerCustomer(RegisterRequest request) {

        if (customerRepository.existsByEmail(
                request.getEmail()
        )) {

            throw new IllegalArgumentException(
                    "Email sudah digunakan"
            );
        }

        if (customerRepository.existsByNik(
                request.getNik()
        )) {

            throw new IllegalArgumentException(
                    "NIK sudah digunakan"
            );
        }

        Plafond plafond = plafondRepository
                .findById(1)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Plafond tidak ditemukan"
                        )
                );

        Customer customer = new Customer();

        customer.setCustomerId(
                UUID.randomUUID().toString()
        );

        customer.setPlafond(plafond);

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

                // RegisterResponse currently uses username,
                // so return customer email
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
                        new IllegalArgumentException(
                                "Username atau email tidak ditemukan"
                        )
                );

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new IllegalArgumentException(
                    "User tidak aktif"
            );
        }

        if (!passwordEncoder.matches(
                request.getPassword(),
                user.getPassword()
        )) {
            throw new IllegalArgumentException(
                    "Password salah"
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
                                "Email customer tidak ditemukan"
                        )
                );

        if (!passwordEncoder.matches(
                request.getPassword(),
                customer.getPassword()
        )) {
            throw new IllegalArgumentException(
                    "Password salah"
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

    @Transactional
    public void forgotPassword(
            ForgotPasswordRequest request
    ) {

        User user = userRepository
                .findByEmail(request.getEmail())
                .orElse(null);

        if (user == null) {
            return;
        }

        passwordResetTokenRepository.deleteByUser(user);

        String token = UUID.randomUUID().toString();

        PasswordResetToken resetToken =
                new PasswordResetToken();

        resetToken.setToken(token);
        resetToken.setUser(user);
        resetToken.setExpiryDate(
                LocalDateTime.now().plusMinutes(15)
        );
        resetToken.setUsed(false);

        passwordResetTokenRepository.save(resetToken);

        emailService.sendResetPasswordEmail(
                user.getEmail(),
                token
        );
    }

    @Transactional
    public void resetPassword(
            ResetPasswordRequest request
    ) {

        if (!request.getNewPassword()
                .equals(request.getConfirmPassword())) {

            throw new IllegalArgumentException(
                    "Password dan konfirmasi password tidak sama"
            );
        }

        PasswordResetToken resetToken =
                passwordResetTokenRepository
                        .findByToken(request.getToken())
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Token reset password tidak valid"
                                )
                        );

        if (Boolean.TRUE.equals(resetToken.getUsed())) {

            throw new IllegalArgumentException(
                    "Token reset password sudah digunakan"
            );
        }

        if (resetToken.getExpiryDate()
                .isBefore(LocalDateTime.now())) {

            throw new IllegalArgumentException(
                    "Token reset password sudah kadaluarsa"
            );
        }

        User user = resetToken.getUser();

        user.setPassword(
                passwordEncoder.encode(
                        request.getNewPassword()
                )
        );

        userRepository.save(user);

        resetToken.setUsed(true);

        passwordResetTokenRepository.save(resetToken);
    }

    private List<MenuResponse> getUserMenus(String userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException("User tidak ditemukan")
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
