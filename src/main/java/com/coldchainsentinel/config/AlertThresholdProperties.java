package com.coldchainsentinel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Configurable environmental threshold limits for alert evaluations.
 *
 * NOTE: These demonstration default limits are baseline samples.
 * Actual safe storage limits strictly depend on the specific commodity (e.g. dairy, produce, pharmaceuticals)
 * and must be tailored per commodity profile in production deployments.
 */
@Component
@ConfigurationProperties(prefix = "coldchain.thresholds")
public class AlertThresholdProperties {

    private BigDecimal temperatureMin = new BigDecimal("0.00");
    private BigDecimal temperatureMax = new BigDecimal("8.00");
    private BigDecimal temperatureCriticalDelta = new BigDecimal("4.00");

    private BigDecimal humidityMin = new BigDecimal("65.00");
    private BigDecimal humidityMax = new BigDecimal("95.00");
    private BigDecimal humidityCriticalDelta = new BigDecimal("10.00");

    public BigDecimal getTemperatureMin() {
        return temperatureMin;
    }

    public void setTemperatureMin(BigDecimal temperatureMin) {
        this.temperatureMin = temperatureMin;
    }

    public BigDecimal getTemperatureMax() {
        return temperatureMax;
    }

    public void setTemperatureMax(BigDecimal temperatureMax) {
        this.temperatureMax = temperatureMax;
    }

    public BigDecimal getTemperatureCriticalDelta() {
        return temperatureCriticalDelta;
    }

    public void setTemperatureCriticalDelta(BigDecimal temperatureCriticalDelta) {
        this.temperatureCriticalDelta = temperatureCriticalDelta;
    }

    public BigDecimal getHumidityMin() {
        return humidityMin;
    }

    public void setHumidityMin(BigDecimal humidityMin) {
        this.humidityMin = humidityMin;
    }

    public BigDecimal getHumidityMax() {
        return humidityMax;
    }

    public void setHumidityMax(BigDecimal humidityMax) {
        this.humidityMax = humidityMax;
    }

    public BigDecimal getHumidityCriticalDelta() {
        return humidityCriticalDelta;
    }

    public void setHumidityCriticalDelta(BigDecimal humidityCriticalDelta) {
        this.humidityCriticalDelta = humidityCriticalDelta;
    }
}
