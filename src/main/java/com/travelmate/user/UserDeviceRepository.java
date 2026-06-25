package com.travelmate.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {

    List<UserDevice> findByUserId(Long userId);

    Optional<UserDevice> findByFcmToken(String fcmToken);

    Optional<UserDevice> findByRid(String rid);
}
