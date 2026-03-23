package com.trackwise.repository;

import com.trackwise.entity.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {
    Optional<NotificationPreference> findByUser_IdAndEventType(Long userId, String eventType);

    List<NotificationPreference> findByUser_Id(Long userId);
}
