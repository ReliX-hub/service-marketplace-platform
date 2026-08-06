package com.relix.marketplace.storage.service;

import com.relix.marketplace.storage.config.ImageProperties;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageValidationTest {

    @Test
    void acceptsJpegPngAndWebpByTheirRealSignatures() throws IOException {
        ImageValidator validator = validator(defaultProperties());

        assertThat(validator.validate(TestImageFactory.jpeg(12, 8)).format()).isEqualTo(ImageFormat.JPEG);
        assertThat(validator.validate(TestImageFactory.png(12, 8, false)).format()).isEqualTo(ImageFormat.PNG);
        assertThat(validator.validate(TestImageFactory.webp()).format()).isEqualTo(ImageFormat.WEBP);
        assertThat(ImageIO.getImageReadersByFormatName("webp").hasNext()).isTrue();
    }

    @Test
    void rejectsUnsupportedAndSignatureSpoofedContent() {
        ImageValidator validator = validator(defaultProperties());

        assertThatThrownBy(() -> validator.validate("GIF89a".getBytes()))
                .isInstanceOfSatisfying(InvalidImageException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("IMAGE_TYPE_UNSUPPORTED");
                    assertThat(error.getDetails()).containsKey("accepted");
                });

        byte[] fakePng = new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                1, 2, 3, 4
        };
        assertThatThrownBy(() -> validator.validate(fakePng))
                .isInstanceOfSatisfying(InvalidImageException.class,
                        error -> assertThat(error.getCode()).isEqualTo("IMAGE_CORRUPT"));
    }

    @Test
    void enforcesSourceByteAndHeaderDimensionLimits() throws IOException {
        ImageProperties byteLimited = defaultProperties();
        byteLimited.setMaxSourceBytes(10);
        assertThatThrownBy(() -> validator(byteLimited).validate(TestImageFactory.png(20, 20, false)))
                .isInstanceOfSatisfying(InvalidImageException.class,
                        error -> assertThat(error.getCode()).isEqualTo("IMAGE_TOO_LARGE"));

        ImageProperties dimensionLimited = defaultProperties();
        dimensionLimited.setMaxSourceDimension(10);
        assertThatThrownBy(() -> validator(dimensionLimited).validate(TestImageFactory.png(20, 8, false)))
                .isInstanceOfSatisfying(InvalidImageException.class,
                        error -> assertThat(error.getCode()).isEqualTo("IMAGE_DIMENSIONS_REJECTED"));

        ImageProperties pixelLimited = defaultProperties();
        pixelLimited.setMaxSourcePixels(100);
        assertThatThrownBy(() -> validator(pixelLimited).validate(TestImageFactory.png(11, 10, false)))
                .isInstanceOfSatisfying(InvalidImageException.class,
                        error -> assertThat(error.getCode()).isEqualTo("IMAGE_DIMENSIONS_REJECTED"));
    }

    private ImageValidator validator(ImageProperties properties) {
        return new ImageValidator(properties);
    }

    private ImageProperties defaultProperties() {
        return new ImageProperties();
    }
}
