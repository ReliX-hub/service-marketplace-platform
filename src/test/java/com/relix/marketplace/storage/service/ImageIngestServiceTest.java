package com.relix.marketplace.storage.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.GpsDirectory;
import com.relix.marketplace.storage.config.ImageProperties;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class ImageIngestServiceTest {

    @Test
    void normalizesOpaqueInputsToJpegAndTransparentInputsToPng() throws IOException {
        ImageIngestService service = service(defaultProperties());

        ImageIngestResult opaque = service.ingest(TestImageFactory.png(30, 20, false));
        assertThat(opaque.sourceFormat()).isEqualTo(ImageFormat.PNG);
        assertThat(opaque.thumb().contentType()).isEqualTo("image/jpeg");
        assertThat(opaque.large().contentType()).isEqualTo("image/jpeg");

        ImageIngestResult transparent = service.ingest(TestImageFactory.png(30, 20, true));
        assertThat(transparent.thumb().contentType()).isEqualTo("image/png");
        assertThat(transparent.large().contentType()).isEqualTo("image/png");
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(transparent.large().bytes()));
        assertThat(decoded.getRGB(0, 0) >>> 24).isZero();
        decoded.flush();
    }

    @Test
    void acceptsWebpAsInputButNeverWritesWebpVariants() {
        ImageIngestResult result = service(defaultProperties()).ingest(TestImageFactory.webp());

        assertThat(result.sourceFormat()).isEqualTo(ImageFormat.WEBP);
        assertThat(result.thumb().contentType()).isIn("image/jpeg", "image/png");
        assertThat(result.large().contentType()).isEqualTo(result.thumb().contentType());
    }

    @Test
    void appliesExifOrientationAndStripsExifGpsAndOtherSourceMetadata() throws Exception {
        byte[] source = TestImageFactory.withExifOrientationAndGps(
                TestImageFactory.jpeg(30, 20),
                6);
        Metadata sourceMetadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(source));
        assertThat(sourceMetadata.getFirstDirectoryOfType(ExifIFD0Directory.class)).isNotNull();
        assertThat(sourceMetadata.getFirstDirectoryOfType(GpsDirectory.class)).isNotNull();

        ImageIngestResult result = service(defaultProperties()).ingest(source);

        assertThat(result.large().width()).isEqualTo(20);
        assertThat(result.large().height()).isEqualTo(30);
        Metadata outputMetadata = ImageMetadataReader.readMetadata(
                new ByteArrayInputStream(result.large().bytes()));
        assertThat(outputMetadata.getFirstDirectoryOfType(ExifIFD0Directory.class)).isNull();
        assertThat(outputMetadata.getFirstDirectoryOfType(GpsDirectory.class)).isNull();
    }

    @Test
    void supportsEveryExifOrientationTransform() {
        BufferedImage source = numberedImage();

        assertPixels(ImageIngestService.applyOrientation(source, 1), new int[][]{
                {1, 2, 3}, {4, 5, 6}
        });
        assertPixels(ImageIngestService.applyOrientation(source, 2), new int[][]{
                {3, 2, 1}, {6, 5, 4}
        });
        assertPixels(ImageIngestService.applyOrientation(source, 3), new int[][]{
                {6, 5, 4}, {3, 2, 1}
        });
        assertPixels(ImageIngestService.applyOrientation(source, 4), new int[][]{
                {4, 5, 6}, {1, 2, 3}
        });
        assertPixels(ImageIngestService.applyOrientation(source, 5), new int[][]{
                {1, 4}, {2, 5}, {3, 6}
        });
        assertPixels(ImageIngestService.applyOrientation(source, 6), new int[][]{
                {4, 1}, {5, 2}, {6, 3}
        });
        assertPixels(ImageIngestService.applyOrientation(source, 7), new int[][]{
                {6, 3}, {5, 2}, {4, 1}
        });
        assertPixels(ImageIngestService.applyOrientation(source, 8), new int[][]{
                {3, 6}, {2, 5}, {1, 4}
        });
        source.flush();
    }

    @Test
    void calculatesHeaderDrivenSubsamplingBeforeDecodeAndBoundsVariants() throws IOException {
        assertThat(ImageIngestService.calculateSubsampling(6000, 4000, 1400)).isEqualTo(4);
        assertThat(ImageIngestService.calculateSubsampling(2799, 1200, 1400)).isEqualTo(1);

        ImageProperties properties = defaultProperties();
        properties.setLargeWidth(40);
        properties.setThumbWidth(12);
        ImageIngestResult result = service(properties).ingest(TestImageFactory.jpeg(120, 80));

        assertThat(Math.max(result.large().width(), result.large().height())).isLessThanOrEqualTo(40);
        assertThat(Math.max(result.thumb().width(), result.thumb().height())).isLessThanOrEqualTo(12);
    }

    private ImageIngestService service(ImageProperties properties) {
        return new ImageIngestService(new ImageValidator(properties), properties);
    }

    private ImageProperties defaultProperties() {
        return new ImageProperties();
    }

    private BufferedImage numberedImage() {
        BufferedImage image = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
        int value = 1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, 0xFF000000 | value++);
            }
        }
        return image;
    }

    private void assertPixels(BufferedImage image, int[][] expected) {
        assertThat(image.getHeight()).isEqualTo(expected.length);
        assertThat(image.getWidth()).isEqualTo(expected[0].length);
        for (int y = 0; y < expected.length; y++) {
            for (int x = 0; x < expected[y].length; x++) {
                assertThat(image.getRGB(x, y) & 0xFF)
                        .as("pixel at (%s,%s)", x, y)
                        .isEqualTo(expected[y][x]);
            }
        }
    }
}
