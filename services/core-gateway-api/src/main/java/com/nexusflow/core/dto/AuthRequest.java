package com.nexusflow.core.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthRequest {

    @NotBlank(message = "username is required")
    private String username;

    @NotBlank(message = "role is required (e.g. ROLE_ADMIN, ROLE_OPERATOR, ROLE_AUDITOR)")
    private String role;
}
