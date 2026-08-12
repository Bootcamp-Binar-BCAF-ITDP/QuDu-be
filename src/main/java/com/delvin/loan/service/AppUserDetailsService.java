package com.delvin.loan.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import com.delvin.loan.model.AppUser;
import com.delvin.loan.model.User;
import com.delvin.loan.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String usernameOrEmail) {

        User user = userRepository
                .findByUsernameOrEmail(usernameOrEmail, usernameOrEmail)
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                "Username atau email tidak ditemukan"
                        )
                );

        if (Boolean.FALSE.equals(user.getIsActive())) {
            throw new UsernameNotFoundException(
                    "User tidak aktif"
            );
        }

        AppUser appUser = new AppUser();

        appUser.setUserId(user.getUserId());
        appUser.setUsername(user.getUsername());
        appUser.setPassword(user.getPassword());

        if (user.getRole() != null) {
            appUser.setRole(user.getRole().getRoleName());
        }

        return appUser;
    }
}
