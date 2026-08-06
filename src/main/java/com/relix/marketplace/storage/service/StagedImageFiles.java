package com.relix.marketplace.storage.service;

import java.util.List;

public record StagedImageFiles(Long thumbId, Long largeId) {

    public List<Long> ids() {
        return List.of(thumbId, largeId);
    }
}
