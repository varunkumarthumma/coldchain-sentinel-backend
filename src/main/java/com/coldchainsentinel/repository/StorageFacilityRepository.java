package com.coldchainsentinel.repository;

import com.coldchainsentinel.model.StorageFacility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for StorageFacility entity.
 */
@Repository
public interface StorageFacilityRepository extends JpaRepository<StorageFacility, Long> {

    Page<StorageFacility> findByActive(boolean active, Pageable pageable);

    boolean existsByCommodityProfileId(Long commodityProfileId);

}

