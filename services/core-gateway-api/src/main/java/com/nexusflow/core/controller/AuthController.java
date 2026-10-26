package com.nexusflow.core.controller;

import com.nexusflow.core.dto.AuthRequest;
import com.nexusflow.core.dto.AuthResponse;
import com.nexusflow.core.service.JwtTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Enterprise JWT Authentication & Token Issuance")
public class AuthController {

    private final JwtTokenService jwtTokenService;

    @PostMapping("/token")
    @Operation(summary = "Generate a JWT Bearer token", description = "Issues a signed JWT token with assigned role (ROLE_ADMIN, ROLE_OPERATOR, ROLE_AUDITOR).")
    public ResponseEntity<AuthResponse> generateToken(@Valid @RequestBody AuthRequest request) {
        String token = jwtTokenService.generateToken(request.getUsername(), request.getRole());

        return ResponseEntity.ok(AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresInMs(jwtTokenService.getExpirationMs())
                .username(request.getUsername())
                .role(request.getRole())
                .build());
    }
}
