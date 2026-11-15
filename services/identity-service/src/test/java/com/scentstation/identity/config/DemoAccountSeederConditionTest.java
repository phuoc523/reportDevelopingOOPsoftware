package com.scentstation.identity.config;

import com.scentstation.identity.repository.StaffUserRepository;
import com.scentstation.identity.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DemoAccountSeederConditionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SeederConfiguration.class);

    @Test
    void demoSeederIsDisabledByDefault() {
        contextRunner.run(context ->
                assertThat(context).doesNotHaveBean(DemoAccountSeeder.class));
    }

    @Test
    void demoSeederIsCreatedOnlyWhenEnabled() {
        contextRunner
                .withPropertyValues("demo.accounts.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(DemoAccountSeeder.class));
    }

    @Configuration(proxyBeanMethods = false)
    @Import(DemoAccountSeeder.class)
    static class SeederConfiguration {

        @Bean
        StaffUserRepository staffUserRepository() {
            return mock(StaffUserRepository.class);
        }

        @Bean
        TenantRepository tenantRepository() {
            return mock(TenantRepository.class);
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            return mock(PasswordEncoder.class);
        }
    }
}
