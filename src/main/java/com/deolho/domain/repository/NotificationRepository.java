package com.deolho.domain.repository;

import com.deolho.domain.entity.Notification;
import com.deolho.domain.enums.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByErrorRecordId(Long errorId);

    List<Notification> findByStatus(NotificationStatus status);

    long countByStatus(NotificationStatus status);
}
