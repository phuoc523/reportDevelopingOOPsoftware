package com.scentstation.catalog.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CatalogSampleDataProperties.class)
public class CatalogSampleDataConfig {
}