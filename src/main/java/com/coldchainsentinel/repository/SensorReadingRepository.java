package com.coldchainsentinel.repository;

import com.coldchainsentinel.model.SensorReading;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Spring Data JPA repository for SensorReading entity.
 */
@Repository
public interface SensorReadingRepository extends JpaRepository<SensorReading, Long> {

    Page<SensorReading> findBySensorId(Long sensorId, Pageable pageable);

    Page<SensorReading> findBySensorIdOrderByRecordedAtDesc(Long sensorId, Pageable pageable);

    List<SensorReading> findBySensorIdAndRecordedAtBetweenOrderByRecordedAtDesc(
            Long sensorId,
            LocalDateTime from,
            LocalDateTime to
    );

    List<SensorReading> findBySensorFacilityIdAndRecordedAtBetweenOrderByRecordedAtDesc(
            Long facilityId,
            LocalDateTime from,
            LocalDateTime to
    );
}
