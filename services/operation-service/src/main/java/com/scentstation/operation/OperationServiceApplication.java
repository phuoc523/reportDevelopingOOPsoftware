package com.scentstation.operation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class OperationServiceApplication {

    // Khởi động Spring Boot application của operation-service.
    public static void main(String[] args) {
        SpringApplication.run(OperationServiceApplication.class, args);
    }
}
