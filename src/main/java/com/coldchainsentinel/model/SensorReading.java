package com.coldchainsentinel.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * JPA entity representing a telemetry reading captured by a sensor.
 */
@Entity
@Table(
        name = "sensor_readings",
        indexes = {
                @Index(name = "idx_sensor_readings_sensor_recorded", columnList = "sensor_id, recorded_at")
        }
)
public class SensorReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sensor_id", nullable = false, foreignKey = @ForeignKey(name = "fk_readings_sensor"))
    private Sensor sensor;

    @Column(name = "temperature_celsius", precision = 6, scale = 2)
    private BigDecimal temperatureCelsius;

    @Column(name = "humidity_percentage", precision = 5, scale = 2)
    private BigDecimal humidityPercentage;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_source", nullable = false, length = 20)
    private ReadingDataSource dataSource;

    public SensorReading() {
    }

    public SensorReading(Sensor sensor,
                         BigDecimal temperatureCelsius,
                         BigDecimal humidityPercentage,
                         LocalDateTime recordedAt,
                         ReadingDataSource dataSource) {
        this.sensor = sensor;
        this.temperatureCelsius = temperatureCelsius;
        this.humidityPercentage = humidityPercentage;
        this.recordedAt = recordedAt;
        this.dataSource = dataSource;
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.recordedAt == null) {
            this.recordedAt = LocalDateTime.now();
        }
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Sensor getSensor() {
        return sensor;
    }

    public void setSensor(Sensor sensor) {
        this.sensor = sensor;
    }

    public BigDecimal getTemperatureCelsius() {
        return temperatureCelsius;
    }

    public void setTemperatureCelsius(BigDecimal temperatureCelsius) {
        this.temperatureCelsius = temperatureCelsius;
    }

    public BigDecimal getHumidityPercentage() {
        return humidityPercentage;
    }

    public void setHumidityPercentage(BigDecimal humidityPercentage) {
        this.humidityPercentage = humidityPercentage;
    }

    public LocalDateTime getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(LocalDateTime recordedAt) {
        this.recordedAt = recordedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public ReadingDataSource getDataSource() {
        return dataSource;
    }

    public void setDataSource(ReadingDataSource dataSource) {
        this.dataSource = dataSource;
    }
}
