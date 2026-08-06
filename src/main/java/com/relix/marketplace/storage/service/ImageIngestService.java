package com.relix.marketplace.storage.service;

import com.relix.marketplace.storage.config.ImageProperties;
import lombok.RequiredArgsConstructor;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ImageIngestService {

    private static final float JPEG_QUALITY = 0.88F;

    private final ImageValidator validator;
    private final ImageProperties properties;

    public ImageIngestResult ingest(byte[] source) {
        ImageDescriptor descriptor = validator.validate(source);
        BufferedImage decoded = decodeSubsampled(source, descriptor);
        BufferedImage oriented = applyOrientation(decoded, descriptor.exifOrientation());
        if (oriented != decoded) {
            decoded.flush();
        }

        try {
            boolean hasTransparency = hasTransparentPixels(oriented);
            ImageFormat outputFormat = hasTransparency ? ImageFormat.PNG : ImageFormat.JPEG;
            BufferedImage large = resizeWithin(oriented, properties.getLargeWidth(), hasTransparency);
            BufferedImage thumb = resizeWithin(large, properties.getThumbWidth(), hasTransparency);
            try {
                return new ImageIngestResult(
                        descriptor.format(),
                        encode(thumb, outputFormat),
                        encode(large, outputFormat));
            } finally {
                thumb.flush();
                large.flush();
            }
        } catch (InvalidImageException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidImageException(
                    "Image normalization failed",
                    "IMAGE_CORRUPT",
                    Map.of(),
                    e);
        } finally {
            oriented.flush();
        }
    }

    private BufferedImage decodeSubsampled(byte[] source, ImageDescriptor descriptor) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
            if (input == null) {
                throw new IOException("Image input stream is unavailable");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("No ImageIO reader is registered for the source image");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                ImageReadParam readParam = reader.getDefaultReadParam();
                int subsampling = calculateSubsampling(
                        descriptor.width(),
                        descriptor.height(),
                        properties.getLargeWidth());
                if (subsampling > 1) {
                    readParam.setSourceSubsampling(subsampling, subsampling, 0, 0);
                }
                BufferedImage image = reader.read(0, readParam);
                if (image == null) {
                    throw new IOException("Image reader returned no pixels");
                }
                return image;
            } finally {
                reader.dispose();
            }
        } catch (Exception e) {
            throw new InvalidImageException(
                    "Image content is corrupt or incomplete",
                    "IMAGE_CORRUPT",
                    Map.of(),
                    e);
        }
    }

    static int calculateSubsampling(int width, int height, int targetMaximumDimension) {
        int maximumDimension = Math.max(width, height);
        return Math.max(1, maximumDimension / targetMaximumDimension);
    }

    static BufferedImage applyOrientation(BufferedImage source, int orientation) {
        if (orientation <= 1 || orientation > 8) {
            return source;
        }

        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        boolean swapsAxes = orientation >= 5;
        int targetWidth = swapsAxes ? sourceHeight : sourceWidth;
        int targetHeight = swapsAxes ? sourceWidth : sourceHeight;
        int imageType = source.getColorModel().hasAlpha()
                ? BufferedImage.TYPE_INT_ARGB
                : BufferedImage.TYPE_INT_RGB;
        BufferedImage target = new BufferedImage(targetWidth, targetHeight, imageType);

        for (int y = 0; y < sourceHeight; y++) {
            for (int x = 0; x < sourceWidth; x++) {
                int targetX;
                int targetY;
                switch (orientation) {
                    case 2 -> {
                        targetX = sourceWidth - 1 - x;
                        targetY = y;
                    }
                    case 3 -> {
                        targetX = sourceWidth - 1 - x;
                        targetY = sourceHeight - 1 - y;
                    }
                    case 4 -> {
                        targetX = x;
                        targetY = sourceHeight - 1 - y;
                    }
                    case 5 -> {
                        targetX = y;
                        targetY = x;
                    }
                    case 6 -> {
                        targetX = sourceHeight - 1 - y;
                        targetY = x;
                    }
                    case 7 -> {
                        targetX = sourceHeight - 1 - y;
                        targetY = sourceWidth - 1 - x;
                    }
                    case 8 -> {
                        targetX = y;
                        targetY = sourceWidth - 1 - x;
                    }
                    default -> throw new IllegalStateException("Unexpected orientation " + orientation);
                }
                target.setRGB(targetX, targetY, source.getRGB(x, y));
            }
        }
        return target;
    }

    private BufferedImage resizeWithin(BufferedImage source, int maximumDimension, boolean alpha) throws IOException {
        BufferedImage resized;
        if (Math.max(source.getWidth(), source.getHeight()) <= maximumDimension) {
            resized = source;
        } else {
            resized = Thumbnails.of(source)
                    .size(maximumDimension, maximumDimension)
                    .keepAspectRatio(true)
                    .asBufferedImage();
        }
        return copyToNormalizedType(resized, alpha);
    }

    private BufferedImage copyToNormalizedType(BufferedImage source, boolean alpha) {
        int imageType = alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage normalized = new BufferedImage(source.getWidth(), source.getHeight(), imageType);
        Graphics2D graphics = normalized.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
            if (alpha) {
                graphics.setComposite(AlphaComposite.Src);
            } else {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, normalized.getWidth(), normalized.getHeight());
            }
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return normalized;
    }

    private boolean hasTransparentPixels(BufferedImage image) {
        if (!image.getColorModel().hasAlpha()) {
            return false;
        }
        int[] row = new int[image.getWidth()];
        for (int y = 0; y < image.getHeight(); y++) {
            image.getRGB(0, y, image.getWidth(), 1, row, 0, image.getWidth());
            for (int pixel : row) {
                if ((pixel >>> 24) != 0xFF) {
                    return true;
                }
            }
        }
        return false;
    }

    private EncodedImage encode(BufferedImage image, ImageFormat outputFormat) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(outputFormat.getImageIoName());
        if (!writers.hasNext()) {
            throw new IOException("No ImageIO writer is registered for " + outputFormat);
        }

        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(output);
            ImageWriteParam writeParam = writer.getDefaultWriteParam();
            if (outputFormat == ImageFormat.JPEG && writeParam.canWriteCompressed()) {
                writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                writeParam.setCompressionQuality(JPEG_QUALITY);
            }
            writer.write(null, new IIOImage(image, null, null), writeParam);
            output.flush();
            return new EncodedImage(
                    bytes.toByteArray(),
                    outputFormat.getContentType(),
                    image.getWidth(),
                    image.getHeight());
        } finally {
            writer.dispose();
        }
    }
}
