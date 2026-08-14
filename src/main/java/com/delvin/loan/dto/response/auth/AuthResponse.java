package com.delvin.loan.dto.response.auth;

import com.delvin.loan.dto.response.menu.MenuResponse;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String token;
    private String userId;
    private String username;
    private String role;
    private String email;
    private String fullName;
    private List<MenuResponse> menus;
}
