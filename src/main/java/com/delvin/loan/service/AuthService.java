package com.delvin.loan.service;

import com.delvin.loan.dto.response.auth.RegisterResponse;
import com.delvin.loan.model.Branch;
import com.delvin.loan.repository.BranchRepository;
import lombok.RequiredArgsConstructor;
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

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final BranchRepository branchRepository;

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
                                request.getUsername(),
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
