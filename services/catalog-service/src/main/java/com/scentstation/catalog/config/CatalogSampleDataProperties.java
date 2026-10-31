package com.scentstation.catalog.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "catalog.sample-data")
public class CatalogSampleDataProperties {

    private boolean enabled = false;
}