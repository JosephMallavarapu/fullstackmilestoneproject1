package com.trackwise.repository;

import com.trackwise.entity.AuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, Long entityId);

    List<AuditLog> findTop100ByOrderByCreatedAtDesc();

    @Query("SELECT a FROM AuditLog a WHERE a.performedBy.id = :userId ORDER BY a.createdAt DESC")
    List<AuditLog> findByUser(@Param("userId") Long userId, Pageable pageable);
}
