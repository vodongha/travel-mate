package com.travelmate.auth;

import com.travelmate.auth.dto.AuthResponse;
import com.travelmate.auth.dto.ForgotPasswordRequest;
import com.travelmate.auth.dto.GoogleLoginRequest;
import com.travelmate.auth.dto.LoginRequest;
import com.travelmate.auth.dto.RefreshRequest;
import com.travelmate.auth.dto.RegisterRequest;
import com.travelmate.auth.dto.ResetPasswordRequest;
import com.travelmate.auth.dto.VerifyEmailRequest;
import com.travelmate.common.security.AuthPrincipal;
import com.travelmate.common.security.CurrentUser;
import com.travelmate.common.web.ApiResponse;
import com.travelmate.user.UserService;
import com.travelmate.user.dto.ChangePasswordRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Public authentication endpoints (SPEC §7 Module 1). All return the standard envelope. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Map<String, String> OK = Map.of("status", "ok");

    private final AuthService authService;
    private final UserService userService;

    public AuthController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request.email(), request.password(), request.name()));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request.email(), request.password()));
    }

    @PostMapping("/google")
    public ApiResponse<AuthResponse> google(@Valid @RequestBody GoogleLoginRequest request) {
        return ApiResponse.ok(authService.googleLogin(request.idToken()));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/verify-email")
    public ApiResponse<Map<String, String>> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request.token());
        return ApiResponse.ok(OK);
    }

    @PostMapping("/forgot-password")
    public ApiResponse<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.email());
        return ApiResponse.ok(OK); // always 200 — no account enumeration
    }

    @PostMapping("/reset-password")
    public ApiResponse<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.newPassword());
        return ApiResponse.ok(OK);
    }

    /**
     * Change the password of the signed-in account, or set the first password for a Google-only
     * account (omit {@code currentPassword}). Authenticated (see {@code SecurityConfig}); 204 on
     * success, 400 if the current password is wrong.
     */
    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@CurrentUser AuthPrincipal principal,
                               @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(principal.id(), request);
    }
}
