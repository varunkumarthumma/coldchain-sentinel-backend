package com.coldchainsentinel.dto;

import java.util.List;

/**
 * Response payload representing the outcome of a batch sensor reading ingestion.
 */
public record BatchSensorReadingResponse(
        Long sensorId,
        String sensorCode,
        int totalIngested,
        List<SensorReadingResponse> readings
) {
}
