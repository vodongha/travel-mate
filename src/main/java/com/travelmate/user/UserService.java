package com.travelmate.user;

import com.travelmate.common.exception.ApiException;
import com.travelmate.common.exception.ErrorCode;
import com.travelmate.common.money.SupportedCurrencies;
import com.travelmate.user.dto.ChangePasswordRequest;
import com.travelmate.user.dto.DeviceResponse;
import com.travelmate.user.dto.RegisterDeviceRequest;
import com.travelmate.user.dto.UpdateMeRequest;
import com.travelmate.user.dto.UserResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Current-user profile, password, account deletion and FCM device registration (SPEC §7 Module 1). */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final AuthTokenRepository authTokenRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       UserDeviceRepository userDeviceRepository,
                       AuthTokenRepository authTokenRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userDeviceRepository = userDeviceRepository;
        this.authTokenRepository = authTokenRepository;
        this.passwordEncoder = passwordEncoder;
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
        if (request.phone() != null) {
            user.setPhone(request.phone().isBlank() ? null : request.phone().trim());
        }
        if (request.timezone() != null) {
            user.setTimezone(request.timezone());
        }
        if (request.defaultCurrency() != null) {
            String currency = request.defaultCurrency().toUpperCase();
            if (!SupportedCurrencies.isSupported(currency)) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        "Unsupported currency: " + currency + ".");
            }
            user.setDefaultCurrency(currency);
        }
        return UserResponse.from(user);
    }

    /**
     * Change the password — or set the <i>first</i> password for a Google-only account (omit
     * {@code currentPassword}). When the account already has a password, the current one is
     * required and verified (400 if wrong). Changing it revokes all refresh tokens so other
     * sessions must re-login.
     */
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = loadUser(userId);
        if (user.hasPassword()) {
            if (request.currentPassword() == null
                    || !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Current password is incorrect.");
            }
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        // Force re-login everywhere after a password change.
        authTokenRepository.markAllUnusedAsUsed(userId, AuthTokenType.REFRESH, Instant.now());
    }

    /**
     * Self-service account deletion (store policy): soft-delete the user (set IS_DELETED) and revoke
     * their refresh tokens so any live session is cut off. The {@code @SQLRestriction} on {@link User}
     * then hides the row everywhere, and {@code JwtAuthenticationFilter} rejects the now-orphaned
     * access token.
     */
    @Transactional
    public void deleteMe(Long userId) {
        User user = loadUser(userId);
        authTokenRepository.markAllUnusedAsUsed(userId, AuthTokenType.REFRESH, Instant.now());
        user.setDeleted(true);
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
