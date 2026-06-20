package com.travelmate.user.dto;

import com.travelmate.user.DevicePlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Registers (or refreshes) the caller's FCM push token for a device. */
public record RegisterDeviceRequest(
        @NotBlank @Size(max = 500) String fcmToken,
        @NotNull DevicePlatform platform) {
}
