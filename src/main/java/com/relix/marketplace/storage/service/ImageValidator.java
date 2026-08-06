package com.relix.marketplace.storage.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.relix.marketplace.storage.config.ImageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ImageValidator {

    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };
    private static final List<String> ACCEPTED_TYPES = List.of(
            ImageFormat.JPEG.getContentType(),
            ImageFormat.PNG.getContentType(),
            ImageFormat.WEBP.getContentType());

    private final ImageProperties properties;

    public ImageDescriptor validate(byte[] source) {
        if (source == null || source.length == 0) {
            throw corrupt("Image file must not be empty", null);
        }
        if (source.length > properties.getMaxSourceBytes()) {
            throw new InvalidImageException(
                    "Image exceeds the configured source size limit",
                    "IMAGE_TOO_LARGE",
                    Map.of(
                            "maxBytes", properties.getMaxSourceBytes(),
                            "actualBytes", source.length));
        }

        ImageFormat format = sniffFormat(source);
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
            if (input == null) {
                throw corrupt("Image stream could not be opened", null);
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw corrupt("Image content could not be decoded", null);
            }

            ImageReader reader = readers.next();
            try {
                if (!format.matchesReaderName(reader.getFormatName())) {
                    throw corrupt("Image signature does not match decoded content", null);
                }
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                validateDimensions(width, height);
                return new ImageDescriptor(format, width, height, extractOrientation(source));
            } finally {
                reader.dispose();
            }
        } catch (InvalidImageException e) {
            throw e;
        } catch (Exception e) {
            throw corrupt("Image content is corrupt or incomplete", e);
        }
    }

    private ImageFormat sniffFormat(byte[] source) {
        if (source.length >= 3
                && (source[0] & 0xFF) == 0xFF
                && (source[1] & 0xFF) == 0xD8
                && (source[2] & 0xFF) == 0xFF) {
            return ImageFormat.JPEG;
        }
        if (startsWith(source, PNG_SIGNATURE)) {
            return ImageFormat.PNG;
        }
        if (source.length >= 12
                && asciiEquals(source, 0, "RIFF")
                && asciiEquals(source, 8, "WEBP")) {
            return ImageFormat.WEBP;
        }
        throw new InvalidImageException(
                "Only JPEG, PNG, and WebP images are supported",
                "IMAGE_TYPE_UNSUPPORTED",
                Map.of("accepted", ACCEPTED_TYPES));
    }

    private void validateDimensions(int width, int height) {
        long pixels = (long) width * (long) height;
        if (width <= 0
                || height <= 0
                || width > properties.getMaxSourceDimension()
                || height > properties.getMaxSourceDimension()
                || pixels > properties.getMaxSourcePixels()) {
            throw new InvalidImageException(
                    "Image dimensions exceed the configured safety limits",
                    "IMAGE_DIMENSIONS_REJECTED",
                    Map.of(
                            "width", width,
                            "height", height,
                            "pixels", pixels,
                            "maxDimension", properties.getMaxSourceDimension(),
                            "maxPixels", properties.getMaxSourcePixels()));
        }
    }

    private int extractOrientation(byte[] source) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(source));
            ExifIFD0Directory directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (directory == null) {
                return 1;
            }
            Integer orientation = directory.getInteger(ExifIFD0Directory.TAG_ORIENTATION);
            return orientation != null && orientation >= 1 && orientation <= 8 ? orientation : 1;
        } catch (Exception ignored) {
            // Broken optional metadata must not prevent safe pixel re-encoding.
            return 1;
        }
    }

    private boolean startsWith(byte[] source, byte[] signature) {
        if (source.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if (source[index] != signature[index]) {
                return false;
            }
        }
        return true;
    }

    private boolean asciiEquals(byte[] source, int offset, String expected) {
        for (int index = 0; index < expected.length(); index++) {
            if (source[offset + index] != (byte) expected.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    private InvalidImageException corrupt(String message, Throwable cause) {
        return new InvalidImageException(message, "IMAGE_CORRUPT", Map.of(), cause);
    }
}
