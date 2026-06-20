package com.travelmate.notification;

import com.travelmate.notification.fcm.FcmSender;
import com.travelmate.trip.TripMember;
import com.travelmate.trip.TripMemberRepository;
import com.travelmate.user.UserDevice;
import com.travelmate.user.UserDeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Drains due {@link ScheduledNotification}s and pushes them via {@link FcmSender}. Idempotent: each
 * row flips PENDING → SENT (or FAILED) once, so a restart never re-sends an already-delivered one.
 * Recipients: the row's {@code userId} if set, otherwise every account-holding member of its trip.
 */
@Service
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);
    private static final int BATCH = 200;

    private final ScheduledNotificationRepository repository;
    private final TripMemberRepository tripMemberRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final FcmSender fcmSender;

    public NotificationDispatcher(ScheduledNotificationRepository repository,
                                  TripMemberRepository tripMemberRepository,
                                  UserDeviceRepository userDeviceRepository,
                                  FcmSender fcmSender) {
        this.repository = repository;
        this.tripMemberRepository = tripMemberRepository;
        this.userDeviceRepository = userDeviceRepository;
        this.fcmSender = fcmSender;
    }

    /** Send everything due at or before {@code now}; returns how many rows were delivered. */
    @Transactional
    public int dispatchDue(Instant now) {
        List<ScheduledNotification> due = repository
                .findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
                        NotificationStatus.PENDING, now, Limit.of(BATCH));
        int delivered = 0;
        for (ScheduledNotification n : due) {
            try {
                for (Long userId : recipients(n)) {
                    for (UserDevice device : userDeviceRepository.findByUserId(userId)) {
                        fcmSender.send(device.getFcmToken(), n.getPayload());
                    }
                }
                n.setStatus(NotificationStatus.SENT);
                n.setSentAt(now);
                delivered++;
            } catch (RuntimeException ex) {
                log.warn("Failed to dispatch notification {}: {}", n.getId(), ex.toString());
                n.setStatus(NotificationStatus.FAILED);
            }
        }
        return delivered;
    }

    private List<Long> recipients(ScheduledNotification n) {
        if (n.getUserId() != null) {
            return List.of(n.getUserId());
        }
        if (n.getTripId() != null) {
            return tripMemberRepository.findByTripId(n.getTripId()).stream()
                    .map(TripMember::getUserId)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .toList();
        }
        return List.of();
    }
}
