package com.coldchainsentinel.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * JPA entity representing an IoT Telemetry Sensor installed in a storage facility.
 */
@Entity
@Table(
        name = "sensors",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_sensors_sensor_code", columnNames = "sensor_code")
        }
)
public class Sensor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sensor_code", nullable = false, unique = true, length = 80)
    private String sensorCode;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "sensor_type", nullable = false, length = 32)
    private SensorType sensorType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "facility_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sensors_facility"))
    private StorageFacility facility;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "token_hash", length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Sensor() {
    }

    public Sensor(String sensorCode, String name, SensorType sensorType, StorageFacility facility) {
        this.sensorCode = sensorCode;
        this.name = name;
        this.sensorType = sensorType;
        this.facility = facility;
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

    public String getSensorCode() {
        return sensorCode;
    }

    public void setSensorCode(String sensorCode) {
        this.sensorCode = sensorCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public SensorType getSensorType() {
        return sensorType;
    }

    public void setSensorType(SensorType sensorType) {
        this.sensorType = sensorType;
    }

    public StorageFacility getFacility() {
        return facility;
    }

    public void setFacility(StorageFacility facility) {
        this.facility = facility;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
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
