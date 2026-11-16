package com.scentstation.operation.maintenance.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class MaintenanceSampleDataProfileTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void shouldNotRegisterInitializerOutsideLocalProfile() {
        assertTrue(applicationContext
                .getBeansOfType(MaintenanceSampleDataInitializer.class)
                .isEmpty());
    }
}
