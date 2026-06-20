package com.travelmate.user;

import com.travelmate.common.exception.ApiException;
import com.travelmate.common.exception.ErrorCode;
import com.travelmate.user.dto.DeviceResponse;
import com.travelmate.user.dto.RegisterDeviceRequest;
import com.travelmate.user.dto.UpdateMeRequest;
import com.travelmate.user.dto.UserResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Current-user profile and FCM device registration (SPEC §7 Module 1). */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserDeviceRepository userDeviceRepository;

    public UserService(UserRepository userRepository, UserDeviceRepository userDeviceRepository) {
        this.userRepository = userRepository;
        this.userDeviceRepository = userDeviceRepository;
    }

    @Transactional(readOnly = true)
    public UserResponse getMe(Long userId) {
        return UserResponse.from(loadUser(userId));
    }

    @Transactional
    public UserResponse updateMe(Long userId, UpdateMeRequest request) {
        User user = loadUser(userId);
        if (request.name() != null) {
            user.setName(request.name().trim());
        }
        if (request.avatar() != null) {
            // Oracle stores '' as NULL; treat blank as "clear it".
            user.setAvatar(request.avatar().isBlank() ? null : request.avatar());
        }
        if (request.timezone() != null) {
            user.setTimezone(request.timezone());
        }
        if (request.defaultCurrency() != null) {
            user.setDefaultCurrency(request.defaultCurrency().toUpperCase());
        }
        return UserResponse.from(user);
    }

    /**
     * Register or refresh a device's FCM token. The token is globally unique among live rows, so
     * if it already exists (e.g. reinstall, or it moved to another account) we re-point it to the
     * current user rather than failing.
     */
    @Transactional
    public DeviceResponse registerDevice(Long userId, RegisterDeviceRequest request) {
        UserDevice device = userDeviceRepository.findByFcmToken(request.fcmToken())
                .orElseGet(UserDevice::new);
        device.setUserId(userId);
        device.setFcmToken(request.fcmToken());
        device.setPlatform(request.platform());
        device.setLastSeenAt(Instant.now());
        return DeviceResponse.from(userDeviceRepository.save(device));
    }

    private User loadUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found."));
    }
}
