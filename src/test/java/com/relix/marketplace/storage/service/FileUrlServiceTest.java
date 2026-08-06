package com.relix.marketplace.storage.service;

import com.relix.marketplace.storage.config.StorageProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FileUrlServiceTest {

    @Test
    void persistsRelativePathAndLeavesItRelativeWithoutBaseUrl() {
        StorageProperties properties = new StorageProperties();
        FileUrlService service = new FileUrlService(properties);

        String storedPath = service.pathForKey("photo-1.jpg");

        assertThat(storedPath).isEqualTo("/api/files/photo-1.jpg");
        assertThat(service.toExternalUrl(storedPath)).isEqualTo(storedPath);
    }

    @Test
    void appliesNormalizedBaseUrlOnlyWhenRenderingManagedPath() {
        StorageProperties properties = new StorageProperties();
        properties.setPublicBaseUrl(" https://media.example.com/ ");
        FileUrlService service = new FileUrlService(properties);

        assertThat(service.toExternalUrl("/api/files/photo-1.jpg"))
                .isEqualTo("https://media.example.com/api/files/photo-1.jpg");
        assertThat(service.toExternalUrl("https://legacy.example.com/photo.jpg"))
                .isEqualTo("https://legacy.example.com/photo.jpg");
    }
}
