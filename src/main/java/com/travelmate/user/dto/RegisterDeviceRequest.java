package com.travelmate.user.dto;

import com.travelmate.user.DevicePlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Registers (or refreshes) the caller's FCM push token for a device. {@code locale} is the app's
 * chosen UI language (BCP-47, e.g. "vi"/"en"); optional — null means follow the server default.
 */
public record RegisterDeviceRequest(
        @NotBlank @Size(max = 500) String fcmToken,
        @NotNull DevicePlatform platform,
        @Size(max = 10) String locale) {
}
