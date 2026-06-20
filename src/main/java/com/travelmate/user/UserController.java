package com.travelmate.user;

import com.travelmate.common.security.AuthPrincipal;
import com.travelmate.common.security.CurrentUser;
import com.travelmate.common.web.ApiResponse;
import com.travelmate.user.dto.DeviceResponse;
import com.travelmate.user.dto.RegisterDeviceRequest;
import com.travelmate.user.dto.UpdateMeRequest;
import com.travelmate.user.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Current-user profile + device registration (SPEC §7 Module 1). Requires authentication. */
@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ApiResponse<UserResponse> me(@CurrentUser AuthPrincipal principal) {
        return ApiResponse.ok(userService.getMe(principal.id()));
    }

    @PatchMapping
    public ApiResponse<UserResponse> updateMe(@CurrentUser AuthPrincipal principal,
                                              @Valid @RequestBody UpdateMeRequest request) {
        return ApiResponse.ok(userService.updateMe(principal.id(), request));
    }

    @PostMapping("/devices")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DeviceResponse> registerDevice(@CurrentUser AuthPrincipal principal,
                                                      @Valid @RequestBody RegisterDeviceRequest request) {
        return ApiResponse.ok(userService.registerDevice(principal.id(), request));
    }

    /** Self-service account deletion: soft-delete the account and revoke its sessions. 204. */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMe(@CurrentUser AuthPrincipal principal) {
        userService.deleteMe(principal.id());
    }
}
