package com.trackwise.repository;

import com.trackwise.entity.ErpSyncLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ErpSyncLogRepository extends JpaRepository<ErpSyncLog, Long> {
    List<ErpSyncLog> findByIntegration_IdOrderBySyncedAtDesc(Long integrationId, Pageable pageable);

    List<ErpSyncLog> findTop50ByOrderBySyncedAtDesc();
}
