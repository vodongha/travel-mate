package com.travelmate.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {

    List<UserDevice> findByUserId(Long userId);

    Optional<UserDevice> findByFcmToken(String fcmToken);

    Optional<UserDevice> findByRid(String rid);

    /** Admin search by owner email (case-insensitive); empty q matches all. */
    @Query("select d from UserDevice d where :q = '' or exists "
            + "(select 1 from User u where u.id = d.userId and lower(u.email) like lower(concat('%', :q, '%')))")
    Page<UserDevice> search(@Param("q") String q, Pageable pageable);
}
