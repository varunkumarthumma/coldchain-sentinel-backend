package com.coldchainsentinel.repository;

import com.coldchainsentinel.model.CommodityProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA Repository for CommodityProfile entities.
 */
@Repository
public interface CommodityProfileRepository extends JpaRepository<CommodityProfile, Long> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    Optional<CommodityProfile> findByName(String name);

    Page<CommodityProfile> findByActive(boolean active, Pageable pageable);
}
