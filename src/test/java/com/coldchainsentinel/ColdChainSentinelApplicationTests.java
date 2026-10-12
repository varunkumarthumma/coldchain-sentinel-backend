package com.coldchainsentinel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.coldchainsentinel.repository.SensorReadingRepository;
import com.coldchainsentinel.repository.SensorRepository;
import com.coldchainsentinel.repository.StorageFacilityRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Basic Spring Boot context load test.
 * Database autoconfiguration is excluded during unit context loading
 * so that running tests does not depend on an active MySQL instance.
 */
@SpringBootTest(classes = ColdChainSentinelApplication.class)
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration"
})
class ColdChainSentinelApplicationTests {

    @MockitoBean
    private StorageFacilityRepository facilityRepository;

    @MockitoBean
    private SensorRepository sensorRepository;

    @MockitoBean
    private SensorReadingRepository readingRepository;

    @MockitoBean
    private com.coldchainsentinel.repository.AlertRepository alertRepository;

    @MockitoBean
    private com.coldchainsentinel.repository.CommodityProfileRepository commodityProfileRepository;

    @Test
    @DisplayName("Application context loads successfully without requiring MySQL")
    void contextLoads() {
    }


}
