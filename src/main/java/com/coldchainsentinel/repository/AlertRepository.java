package com.coldchainsentinel.repository;

import com.coldchainsentinel.model.Alert;
import com.coldchainsentinel.model.AlertSeverity;
import com.coldchainsentinel.model.AlertStatus;
import com.coldchainsentinel.model.AlertType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for Alert entity.
 */
@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {

    Optional<Alert> findBySensorIdAndAlertTypeAndStatus(Long sensorId, AlertType alertType, AlertStatus status);

    boolean existsBySensorIdAndAlertTypeAndStatus(Long sensorId, AlertType alertType, AlertStatus status);

    List<Alert> findBySensorIdOrderByCreatedAtDesc(Long sensorId);

    Page<Alert> findByStatusAndSeverity(AlertStatus status, AlertSeverity severity, Pageable pageable);

    Page<Alert> findByStatus(AlertStatus status, Pageable pageable);

    Page<Alert> findBySeverity(AlertSeverity severity, Pageable pageable);

    List<Alert> findBySensorIdAndCreatedAtBetweenOrderByCreatedAtDesc(Long sensorId, java.time.LocalDateTime from, java.time.LocalDateTime to);

    List<Alert> findByFacilityIdAndCreatedAtBetweenOrderByCreatedAtDesc(Long facilityId, java.time.LocalDateTime from, java.time.LocalDateTime to);

}
