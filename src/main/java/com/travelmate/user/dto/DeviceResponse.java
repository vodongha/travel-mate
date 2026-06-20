package com.travelmate.user.dto;

import com.travelmate.user.DevicePlatform;
import com.travelmate.user.UserDevice;

import java.time.Instant;

public record DeviceResponse(String rid, DevicePlatform platform, Instant lastSeenAt) {

    public static DeviceResponse from(UserDevice device) {
        return new DeviceResponse(device.getRid(), device.getPlatform(), device.getLastSeenAt());
    }
}
