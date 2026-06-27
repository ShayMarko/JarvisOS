package com.jarvis.notification;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NotificationRepository extends JpaRepository<Notification, String> {

    List<Notification> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByReadFalse();

    @Modifying
    @Query("update Notification n set n.read = true where n.read = false")
    void markAllRead();

    /** Mark every notification tied to an actionable target (e.g. an approval id) read — used when it's resolved. */
    @Modifying
    @Query("update Notification n set n.read = true where n.actionId = :actionId and n.read = false")
    int markReadByActionId(String actionId);
}
