package com.trackwise.repository;

import com.trackwise.entity.ErpIntegration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ErpIntegrationRepository extends JpaRepository<ErpIntegration, Long> {
    Optional<ErpIntegration> findByProvider(ErpIntegration.ErpProvider provider);

    List<ErpIntegration> findByIsActiveTrue();
}
