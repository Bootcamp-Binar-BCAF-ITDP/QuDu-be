package com.delvin.loan.service;

import com.delvin.loan.common.AccountType;
import com.delvin.loan.model.Customer;
import com.delvin.loan.repository.CustomerRepository;
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
    private final CustomerRepository customerRepository;

    @Override
    public UserDetails loadUserByUsername(
            String usernameOrEmail
    ) {

        // CHECK INTERNAL USER

        User user = userRepository
                .findByUsernameOrEmail(
                        usernameOrEmail,
                        usernameOrEmail
                )
                .orElse(null);

        if (user != null) {

            if (Boolean.FALSE.equals(user.getIsActive())) {
                throw new UsernameNotFoundException(
                        "User tidak aktif"
                );
            }

            AppUser appUser = new AppUser();

            appUser.setUserId(user.getUserId());
            appUser.setUsername(user.getUsername());
            appUser.setPassword(user.getPassword());
            appUser.setAccountType(AccountType.USER);

            if (user.getRole() != null) {
                appUser.setRole(
                        user.getRole().getRoleName()
                );
            }

            return appUser;
        }

        // CHECK CUSTOMER

        Customer customer = customerRepository
                .findByEmail(usernameOrEmail)
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                "User atau customer tidak ditemukan"
                        )
                );

        AppUser appUser = new AppUser();

        appUser.setUserId(customer.getCustomerId());

        // Customer logs in with email
        appUser.setUsername(customer.getEmail());

        appUser.setPassword(customer.getPassword());
        appUser.setRole("CUSTOMER");
        appUser.setAccountType(AccountType.CUSTOMER);

        return appUser;
    }
}
