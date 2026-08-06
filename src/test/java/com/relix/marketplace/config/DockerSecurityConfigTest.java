package com.relix.marketplace.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DockerSecurityConfigTest {

    @Test
    void dockerProfileRequiresAnExplicitJwtSecret() throws IOException {
        PropertySource<?> properties = new YamlPropertySourceLoader()
                .load("application-docker.yml", new ClassPathResource("application-docker.yml"))
                .get(0);

        assertEquals("${JWT_SECRET}", properties.getProperty("jwt.secret"));
    }

    @Test
    void developmentProfileBindsDirectRunsToLoopback() throws IOException {
        PropertySource<?> properties = new YamlPropertySourceLoader()
                .load("application-dev.yml", new ClassPathResource("application-dev.yml"))
                .get(0);
        PropertySource<?> testProperties = new YamlPropertySourceLoader()
                .load("application-test.yml", new ClassPathResource("application-test.yml"))
                .get(0);

        assertEquals("${SERVER_ADDRESS:127.0.0.1}", properties.getProperty("server.address"));
        assertEquals("127.0.0.1", testProperties.getProperty("server.address"));
        assertEquals("always", properties.getProperty("management.endpoint.health.show-details"));
    }

    @Test
    void nonDevelopmentHealthDetailsRequireAuthorization() throws IOException {
        PropertySource<?> properties = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"))
                .get(0);

        assertEquals("when-authorized", properties.getProperty("management.endpoint.health.show-details"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void developmentComposeDoesNotPublishServicesBeyondLoopback() throws IOException {
        Map<String, Object> compose = new Yaml().load(Files.readString(Path.of("docker-compose.yml")));
        Map<String, Object> services = (Map<String, Object>) compose.get("services");
        Map<String, Object> api = (Map<String, Object>) services.get("api");
        Map<String, Object> database = (Map<String, Object>) services.get("db");
        Map<String, Object> environment = (Map<String, Object>) api.get("environment");

        assertEquals(
                List.of("127.0.0.1:${MARKETPLACE_API_HOST_PORT:-8080}:8080"),
                api.get("ports"));
        assertEquals(
                List.of("127.0.0.1:${MARKETPLACE_DB_HOST_PORT:-5432}:5432"),
                database.get("ports"));
        assertEquals("0.0.0.0", environment.get("SERVER_ADDRESS"));
        String developmentSecret = String.valueOf(environment.get("JWT_SECRET"));
        assertEquals(
                "${JWT_SECRET:-dGhpcyBpcyBhIHZlcnkgbG9uZyBzZWNyZXQga2V5IGZvciBqd3QgdG9rZW4gZ2VuZXJhdGlvbiB0aGF0IGlzIGF0IGxlYXN0IDI1NiBiaXRz}",
                developmentSecret);
    }

    @Test
    void runtimeImageProvidesHealthcheckToolAndRunsAsNonRoot() throws IOException {
        String dockerfile = Files.readString(Path.of("Dockerfile"));

        assertTrue(dockerfile.contains("apt-get install -y --no-install-recommends curl"));
        assertTrue(dockerfile.contains("USER 10001:10001"));
    }
}
