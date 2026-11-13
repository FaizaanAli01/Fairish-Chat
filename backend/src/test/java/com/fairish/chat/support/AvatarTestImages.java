package com.fairish.chat.support;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Generates small, real image payloads for avatar tests. No external image
 * fixtures are checked into the repository.
 */
public final class AvatarTestImages {

    private AvatarTestImages() {
    }

    public static byte[] jpeg(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(30, 144, 255));
            graphics.fillRect(0, 0, width, height);
            graphics.setColor(new Color(220, 20, 60));
            graphics.fillOval(width / 4, height / 4, width / 2, height / 2);
        } finally {
            graphics.dispose();
        }
        return encode(image, "jpg");
    }

    public static byte[] png(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(0, 128, 0, 255));
            graphics.fillRect(0, 0, width, height);
            graphics.setColor(new Color(255, 215, 0, 255));
            graphics.fillOval(width / 4, height / 4, width / 2, height / 2);
        } finally {
            graphics.dispose();
        }
        return encode(image, "png");
    }

    /**
     * A PNG that is fully transparent outside a centered opaque square.
     */
    public static byte[] transparentPng(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setComposite(java.awt.AlphaComposite.Clear);
            graphics.fillRect(0, 0, width, height);
            graphics.setComposite(java.awt.AlphaComposite.SrcOver);
            graphics.setColor(new Color(255, 0, 0, 255));
            graphics.fillRect(width / 4, height / 4, width / 2, height / 2);
        } finally {
            graphics.dispose();
        }
        return encode(image, "png");
    }

    /**
     * A valid JPEG with an EXIF APP1 segment and a COM comment inserted. The
     * payload contains a recognizable marker so tests can prove re-encoding
     * removes embedded metadata.
     */
    public static byte[] jpegWithMetadata(String marker) {
        byte[] jpeg = jpeg(640, 480);
        byte[] comment = marker.getBytes(StandardCharsets.ISO_8859_1);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            output.write(jpeg, 0, 2); // SOI (FF D8)
            writeSegment(output, 0xE1, concat("Exif\u0000\u0000".getBytes(StandardCharsets.ISO_8859_1), comment));
            writeSegment(output, 0xFE, comment);
            output.write(jpeg, 2, jpeg.length - 2);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return output.toByteArray();
    }

    public static byte[] webp() {
        byte[] bytes = new byte[32];
        bytes[0] = 'R';
        bytes[1] = 'I';
        bytes[2] = 'F';
        bytes[3] = 'F';
        bytes[4] = 20;
        bytes[8] = 'W';
        bytes[9] = 'E';
        bytes[10] = 'B';
        bytes[11] = 'P';
        return bytes;
    }

    public static byte[] svg() {
        return ("<?xml version=\"1.0\"?><svg xmlns=\"http://www.w3.org/2000/svg\" "
                + "width=\"64\" height=\"64\"><rect width=\"64\" height=\"64\"/></svg>")
                .getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] gif() {
        return new byte[]{'G', 'I', 'F', '8', '9', 'a', 1, 0, 1, 0};
    }

    /**
     * JPEG magic bytes followed by garbage: a plausible extension/content type
     * but undecodable pixels.
     */
    public static byte[] corruptJpeg() {
        byte[] bytes = new byte[256];
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xD8;
        bytes[2] = (byte) 0xFF;
        for (int i = 3; i < bytes.length; i++) {
            bytes[i] = (byte) (i * 31);
        }
        return bytes;
    }

    private static byte[] encode(BufferedImage image, String format) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(image, format, output)) {
                throw new IllegalStateException("No ImageIO writer available for " + format);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return output.toByteArray();
    }

    private static void writeSegment(ByteArrayOutputStream output, int marker, byte[] payload) throws IOException {
        output.write(0xFF);
        output.write(marker);
        int length = payload.length + 2;
        output.write((length >> 8) & 0xFF);
        output.write(length & 0xFF);
        output.write(payload);
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = new byte[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}
