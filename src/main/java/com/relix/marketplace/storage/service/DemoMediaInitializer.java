package com.relix.marketplace.storage.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DemoMediaInitializer implements ApplicationRunner {

    private final DemoMediaBootstrapService bootstrapService;

    @Override
    public void run(ApplicationArguments args) {
        int installed = bootstrapService.installMissingImages();
        if (installed > 0) {
            log.info("Installed normalized development images for {} seeded tickets", installed);
        }
    }
}
