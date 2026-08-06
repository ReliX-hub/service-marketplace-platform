package com.relix.marketplace.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlywayProfileConfigTest {

    private final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

    @Test
    void defaultProfileRunsOnlySchemaMigrations() throws IOException {
        PropertySource<?> properties = load("application.yml");

        assertEquals("classpath:db/migration", properties.getProperty("spring.flyway.locations"));
    }

    @Test
    void devProfileAlsoRunsSeedMigrations() throws IOException {
        PropertySource<?> properties = load("application-dev.yml");

        assertEquals("classpath:db/migration,classpath:db/seed",
                properties.getProperty("spring.flyway.locations"));
    }

    private PropertySource<?> load(String resourceName) throws IOException {
        return loader.load(resourceName, new ClassPathResource(resourceName)).get(0);
    }
}
