package com.travelmate.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelmate.notification.NotificationStatus;
import com.travelmate.notification.NotificationType;
import com.travelmate.notification.ScheduledNotification;
import com.travelmate.notification.ScheduledNotificationRepository;
import com.travelmate.notification.fcm.FcmSender;
import com.travelmate.trip.Trip;
import com.travelmate.trip.TripMember;
import com.travelmate.trip.TripMemberRepository;
import com.travelmate.trip.TripRepository;
import com.travelmate.user.User;
import com.travelmate.user.UserDevice;
import com.travelmate.user.UserDeviceRepository;
import com.travelmate.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Admin-side management of {@link ScheduledNotification}s: list them, compose + push one immediately
 * (to a whole trip or a single user), and cancel a pending one. Composed messages are free text, so
 * they're stored as a {@code {title, body, deeplink}} payload — the dispatcher/sender already send
 * that verbatim (no localization key).
 */
@Service
public class AdminNotificationService {

    private final ScheduledNotificationRepository repository;
    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final UserRepository userRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final FcmSender fcmSender;
    private final ObjectMapper objectMapper;
    private final AdminService adminService;

    public AdminNotificationService(ScheduledNotificationRepository repository,
                                    TripRepository tripRepository,
                                    TripMemberRepository tripMemberRepository,
                                    UserRepository userRepository,
                                    UserDeviceRepository userDeviceRepository,
                                    FcmSender fcmSender, ObjectMapper objectMapper,
                                    AdminService adminService) {
        this.repository = repository;
        this.tripRepository = tripRepository;
        this.tripMemberRepository = tripMemberRepository;
        this.userRepository = userRepository;
        this.userDeviceRepository = userDeviceRepository;
        this.fcmSender = fcmSender;
        this.objectMapper = objectMapper;
        this.adminService = adminService;
    }

    @Transactional(readOnly = true)
    public Page<ScheduledNotification> list(String q, Pageable pageable) {
        return repository.search(q == null ? "" : q.trim(), pageable);
    }

    /**
     * Compose a notification and push it now. {@code target} is "TRIP" or "USER"; {@code identifier}
     * is a trip rid or a user email. Records a SENT row for the history and audits it.
     */
    @Transactional
    public int sendNow(Long actorId, String target, String identifier,
                       String title, String body, String deeplink) {
        if (title == null || title.isBlank()) {
            throw new AdminActionException("Title is required.");
        }
        Long tripId = null;
        Long userId = null;
        final List<Long> recipients;
        if ("USER".equalsIgnoreCase(target)) {
            User user = userRepository.findByEmail(identifier.trim().toLowerCase())
                    .orElseThrow(() -> new AdminActionException("No user with that email."));
            userId = user.getId();
            recipients = List.of(userId);
        } else {
            Trip trip = tripRepository.findByRid(identifier.trim())
                    .orElseThrow(() -> new AdminActionException("No trip with that id."));
            tripId = trip.getId();
            recipients = tripMemberRepository.findByTripId(tripId).stream()
                    .map(TripMember::getUserId).filter(Objects::nonNull).distinct().toList();
        }

        final String payload = payload(title.trim(), body == null ? "" : body.trim(),
                deeplink == null ? "" : deeplink.trim());
        int devices = 0;
        for (Long uid : recipients) {
            for (UserDevice device : userDeviceRepository.findByUserId(uid)) {
                fcmSender.send(device.getFcmToken(), payload);
                devices++;
            }
        }

        ScheduledNotification record = new ScheduledNotification();
        record.setTripId(tripId);
        record.setUserId(userId);
        record.setType(NotificationType.ADMIN);
        record.setPayload(payload);
        record.setScheduledAt(Instant.now());
        record.setSentAt(Instant.now());
        record.setStatus(NotificationStatus.SENT);
        repository.save(record);

        adminService.audit(actorId, "NOTIFICATION_PUSH", "NOTIFICATION", record.getRid(),
                "target=" + target + " id=" + identifier + " devices=" + devices);
        return devices;
    }

    /** Cancel every still-pending, already-due notification at once (clears a stuck backlog). */
    @Transactional
    public int cancelOverduePending(Long actorId) {
        int count = repository.cancelOverduePending(Instant.now());
        if (count > 0) {
            adminService.audit(actorId, "NOTIFICATION_CANCEL_OVERDUE", "NOTIFICATION", null,
                    "count=" + count);
        }
        return count;
    }

    /** Cancel a pending notification (e.g. an auto-generated reminder) by rid. */
    @Transactional
    public void cancel(Long actorId, String rid) {
        ScheduledNotification n = repository.findByRid(rid)
                .orElseThrow(() -> new AdminActionException("Notification not found."));
        if (n.getStatus() != NotificationStatus.PENDING) {
            throw new AdminActionException("Only pending notifications can be cancelled.");
        }
        n.setStatus(NotificationStatus.CANCELLED);
        adminService.audit(actorId, "NOTIFICATION_CANCEL", "NOTIFICATION", rid, null);
    }

    private String payload(String title, String body, String deeplink) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("title", title);
        map.put("body", body);
        map.put("deeplink", deeplink);
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize payload", e);
        }
    }
}
