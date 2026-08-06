package com.relix.marketplace.storage.service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;

final class TestImageFactory {

    private static final String ONE_PIXEL_WEBP =
            "UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA";

    private TestImageFactory() {
    }

    static byte[] jpeg(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(40, 100, 180));
            graphics.fillRect(0, 0, width, height);
            graphics.setColor(new Color(230, 180, 30));
            graphics.fillRect(0, 0, Math.max(1, width / 2), Math.max(1, height / 2));
        } finally {
            graphics.dispose();
        }
        return write(image, "jpeg");
    }

    static byte[] png(int width, int height, boolean transparent) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(20, 140, 80, 255));
            graphics.fillRect(0, 0, width, height);
            if (transparent) {
                image.setRGB(0, 0, 0x00000000);
            }
        } finally {
            graphics.dispose();
        }
        return write(image, "png");
    }

    static byte[] webp() {
        return Base64.getDecoder().decode(ONE_PIXEL_WEBP);
    }

    static byte[] withExifOrientationAndGps(byte[] jpeg, int orientation) throws IOException {
        if (jpeg.length < 2 || (jpeg[0] & 0xFF) != 0xFF || (jpeg[1] & 0xFF) != 0xD8) {
            throw new IllegalArgumentException("Expected JPEG input");
        }

        ByteBuffer tiff = ByteBuffer.allocate(56).order(ByteOrder.LITTLE_ENDIAN);
        tiff.put((byte) 'I').put((byte) 'I');
        tiff.putShort((short) 42);
        tiff.putInt(8);
        tiff.position(8);
        tiff.putShort((short) 2);

        tiff.putShort((short) 0x0112);
        tiff.putShort((short) 3);
        tiff.putInt(1);
        tiff.putShort((short) orientation);
        tiff.putShort((short) 0);

        tiff.putShort((short) 0x8825);
        tiff.putShort((short) 4);
        tiff.putInt(1);
        tiff.putInt(38);
        tiff.putInt(0);

        tiff.position(38);
        tiff.putShort((short) 1);
        tiff.putShort((short) 0x0001);
        tiff.putShort((short) 2);
        tiff.putInt(2);
        tiff.put((byte) 'N').put((byte) 0).put((byte) 0).put((byte) 0);
        tiff.putInt(0);

        byte[] exifPrefix = new byte[]{'E', 'x', 'i', 'f', 0, 0};
        int payloadLength = exifPrefix.length + tiff.array().length;
        int jpegSegmentLength = payloadLength + 2;
        ByteArrayOutputStream output = new ByteArrayOutputStream(jpeg.length + payloadLength + 4);
        output.write(jpeg, 0, 2);
        output.write(0xFF);
        output.write(0xE1);
        output.write((jpegSegmentLength >>> 8) & 0xFF);
        output.write(jpegSegmentLength & 0xFF);
        output.write(exifPrefix);
        output.write(tiff.array());
        output.write(jpeg, 2, jpeg.length - 2);
        return output.toByteArray();
    }

    private static byte[] write(BufferedImage image, String format) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, format, output)) {
                throw new IOException("No writer for " + format);
            }
            return output.toByteArray();
        } finally {
            image.flush();
        }
    }
}
