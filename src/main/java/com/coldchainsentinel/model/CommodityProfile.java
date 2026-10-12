package com.coldchainsentinel.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * JPA entity representing a Commodity-Specific Storage Profile.
 * Defines optimal temperature and humidity boundaries for stored perishable goods.
 */
@Entity
@Table(
        name = "commodity_profiles",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_commodity_profiles_name", columnNames = "name")
        }
)
public class CommodityProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 120)
    private String name;

    @Column(name = "commodity_name", nullable = false, length = 120)
    private String commodityName;

    @Column(name = "variety", length = 120)
    private String variety;

    @Column(name = "target_temperature_min", nullable = false, precision = 6, scale = 2)
    private BigDecimal targetTemperatureMin;

    @Column(name = "target_temperature_max", nullable = false, precision = 6, scale = 2)
    private BigDecimal targetTemperatureMax;

    @Column(name = "target_humidity_min", nullable = false, precision = 6, scale = 2)
    private BigDecimal targetHumidityMin;

    @Column(name = "target_humidity_max", nullable = false, precision = 6, scale = 2)
    private BigDecimal targetHumidityMax;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public CommodityProfile() {
    }

    public CommodityProfile(String name,
                            String commodityName,
                            String variety,
                            BigDecimal targetTemperatureMin,
                            BigDecimal targetTemperatureMax,
                            BigDecimal targetHumidityMin,
                            BigDecimal targetHumidityMax) {
        this.name = name;
        this.commodityName = commodityName;
        this.variety = variety;
        this.targetTemperatureMin = targetTemperatureMin;
        this.targetTemperatureMax = targetTemperatureMax;
        this.targetHumidityMin = targetHumidityMin;
        this.targetHumidityMax = targetHumidityMax;
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

    public String getCommodityName() {
        return commodityName;
    }

    public void setCommodityName(String commodityName) {
        this.commodityName = commodityName;
    }

    public String getVariety() {
        return variety;
    }

    public void setVariety(String variety) {
        this.variety = variety;
    }

    public BigDecimal getTargetTemperatureMin() {
        return targetTemperatureMin;
    }

    public void setTargetTemperatureMin(BigDecimal targetTemperatureMin) {
        this.targetTemperatureMin = targetTemperatureMin;
    }

    public BigDecimal getTargetTemperatureMax() {
        return targetTemperatureMax;
    }

    public void setTargetTemperatureMax(BigDecimal targetTemperatureMax) {
        this.targetTemperatureMax = targetTemperatureMax;
    }

    public BigDecimal getTargetHumidityMin() {
        return targetHumidityMin;
    }

    public void setTargetHumidityMin(BigDecimal targetHumidityMin) {
        this.targetHumidityMin = targetHumidityMin;
    }

    public BigDecimal getTargetHumidityMax() {
        return targetHumidityMax;
    }

    public void setTargetHumidityMax(BigDecimal targetHumidityMax) {
        this.targetHumidityMax = targetHumidityMax;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
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
