package com.relix.marketplace.storage.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {

    @NotBlank
    @Pattern(regexp = "local", message = "only the local storage provider is currently supported")
    private String provider = "local";

    @Valid
    private Local local = new Local();

    private String publicBaseUrl = "";

    @Getter
    @Setter
    public static class Local {

        @NotBlank
        private String root = "/var/marketplace/files";
    }
}
