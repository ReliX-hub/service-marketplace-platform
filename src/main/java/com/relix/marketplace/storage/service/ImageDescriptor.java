package com.relix.marketplace.storage.service;

public record ImageDescriptor(
        ImageFormat format,
        int width,
        int height,
        int exifOrientation) {
}
