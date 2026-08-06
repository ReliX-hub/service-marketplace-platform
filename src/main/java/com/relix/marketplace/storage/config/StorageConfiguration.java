package com.relix.marketplace.storage.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({StorageProperties.class, ImageProperties.class})
public class StorageConfiguration {
}
