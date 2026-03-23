package com.trackwise.repository;

import com.trackwise.entity.OcrScan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface OcrScanRepository extends JpaRepository<OcrScan, Long> {
    List<OcrScan> findBySubmittedBy_IdOrderByCreatedAtDesc(Long userId);

    @Query("SELECT o FROM OcrScan o WHERE o.status = 'PENDING' ORDER BY o.createdAt")
    List<OcrScan> findPendingScans();
}
