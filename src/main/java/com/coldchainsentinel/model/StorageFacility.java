package com.coldchainsentinel.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * JPA entity representing a monitored Cold Chain Storage Facility.
 */
@Entity
@Table(name = "storage_facilities")
public class StorageFacility {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "location", nullable = false, length = 255)
    private String location;

    @Column(name = "capacity_in_tonnes", nullable = false, precision = 12, scale = 2)
    private BigDecimal capacityInTonnes;

    @Enumerated(EnumType.STRING)
    @Column(name = "facility_type", nullable = false, length = 32)
    private FacilityType facilityType;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commodity_profile_id", foreignKey = @ForeignKey(name = "fk_storage_facilities_commodity_profile"))
    private CommodityProfile commodityProfile;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;


    public StorageFacility() {
    }

    public StorageFacility(String name, String location, BigDecimal capacityInTonnes, FacilityType facilityType) {
        this.name = name;
        this.location = location;
        this.capacityInTonnes = capacityInTonnes;
        this.facilityType = facilityType;
        this.active = true;
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public BigDecimal getCapacityInTonnes() {
        return capacityInTonnes;
    }

    public void setCapacityInTonnes(BigDecimal capacityInTonnes) {
        this.capacityInTonnes = capacityInTonnes;
    }

    public FacilityType getFacilityType() {
        return facilityType;
    }

    public void setFacilityType(FacilityType facilityType) {
        this.facilityType = facilityType;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public CommodityProfile getCommodityProfile() {
        return commodityProfile;
    }

    public void setCommodityProfile(CommodityProfile commodityProfile) {
        this.commodityProfile = commodityProfile;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

