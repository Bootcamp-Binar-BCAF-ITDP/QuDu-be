package com.delvin.loan.service;

import com.delvin.loan.dto.response.auth.RegisterResponse;
import com.delvin.loan.model.Branch;
import com.delvin.loan.repository.BranchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.delvin.loan.dto.request.auth.LoginRequest;
import com.delvin.loan.dto.request.auth.RegisterRequest;
import com.delvin.loan.dto.response.auth.AuthResponse;
import com.delvin.loan.model.AppUser;
import com.delvin.loan.model.Role;
import com.delvin.loan.model.User;
import com.delvin.loan.repository.RoleRepository;
import com.delvin.loan.repository.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import com.delvin.loan.dto.request.auth.ForgotPasswordRequest;
import com.delvin.loan.dto.request.auth.ResetPasswordRequest;
import com.delvin.loan.model.PasswordResetToken;
import com.delvin.loan.repository.PasswordResetTokenRepository;

import java.time.LocalDateTime;
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

    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Transactional
    public RegisterResponse register(RegisterRequest request) {

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException(
                    "Username sudah digunakan"
            );
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException(
                    "Email sudah digunakan"
            );
        }

        Role role = roleRepository
                .findById(request.getRoleId())
                .orElseThrow(() -> new IllegalArgumentException("Role tidak ditemukan: " + request.getRoleId()));

        Branch branch = branchRepository.findByBranchIdAndIsActive(request.getBranchId(), true)
                .orElseThrow(() -> new IllegalArgumentException("Branch tidak ditemukan atau tidak aktif: " + request.getBranchId()));

        User user = new User();

        user.setUserId(UUID.randomUUID().toString());
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(
                passwordEncoder.encode(request.getPassword())
        );
        user.setFullName(request.getFullName());
        user.setPhoneNumber(request.getPhoneNumber());
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

    public AuthResponse login(LoginRequest request) {

        Authentication authentication =
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                request.getUsernameOrEmail(),
                                request.getPassword()
                        )
                );

        AppUser appUser =
                (AppUser) authentication.getPrincipal();

        String token = jwtService.issue(
                appUser,
                java.time.Instant.now()
        );

        return AuthResponse.builder()
                .token(token)
                .userId(appUser.getUserId())
                .username(appUser.getUsername())
                .role(appUser.getRole())
                .build();
    }

    @Transactional
    public void forgotPassword(
            ForgotPasswordRequest request
    ) {

        User user = userRepository
                .findByEmail(request.getEmail())
                .orElse(null);

        // Do not reveal whether an email exists
        if (user == null) {
            return;
        }

        // Remove old reset token
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

        // Mark token as used
        resetToken.setUsed(true);

        passwordResetTokenRepository.save(resetToken);
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
}
