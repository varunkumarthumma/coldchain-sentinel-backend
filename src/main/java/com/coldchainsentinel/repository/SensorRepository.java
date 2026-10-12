package com.coldchainsentinel.repository;

import com.coldchainsentinel.model.Sensor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for Sensor entity.
 */
@Repository
public interface SensorRepository extends JpaRepository<Sensor, Long> {

    boolean existsBySensorCode(String sensorCode);

    Optional<Sensor> findBySensorCode(String sensorCode);

    List<Sensor> findByFacilityId(Long facilityId);

    List<Sensor> findByFacilityIdAndActive(Long facilityId, boolean active);

    Page<Sensor> findByFacilityId(Long facilityId, Pageable pageable);

    Page<Sensor> findByActive(boolean active, Pageable pageable);

}
