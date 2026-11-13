package com.fairish.chat.service;

import com.fairish.chat.exception.InvalidAvatarException;
import com.fairish.chat.support.AvatarTestImages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvatarImageProcessorTest {

    private static final int FIVE_MB = 5 * 1024 * 1024;

    private AvatarImageProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new AvatarImageProcessor(FIVE_MB, 512);
    }

    @Test
    @DisplayName("Valid JPEG is normalized to a 512x512 JPEG")
    void shouldProcessJpeg() throws Exception {
        ProcessedAvatar result = processor.process(AvatarTestImages.jpeg(1200, 800));

        assertEquals(AvatarFormat.JPEG, result.format());
        assertEquals("image/jpeg", result.contentType());
        assertEquals(512, result.width());
        assertEquals(512, result.height());

        BufferedImage decoded = decode(result.content());
        assertEquals(512, decoded.getWidth());
        assertEquals(512, decoded.getHeight());
        assertTrue(startsWith(result.content(), new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}));
    }

    @Test
    @DisplayName("Valid PNG is normalized to a 512x512 PNG")
    void shouldProcessPng() throws Exception {
        ProcessedAvatar result = processor.process(AvatarTestImages.png(400, 900));

        assertEquals(AvatarFormat.PNG, result.format());
        assertEquals("image/png", result.contentType());

        BufferedImage decoded = decode(result.content());
        assertEquals(512, decoded.getWidth());
        assertEquals(512, decoded.getHeight());
        assertTrue(startsWith(result.content(), new byte[]{
                (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}));
    }

    @Test
    @DisplayName("PNG transparency is preserved")
    void shouldPreservePngTransparency() throws Exception {
        ProcessedAvatar result = processor.process(AvatarTestImages.transparentPng(800, 800));

        BufferedImage decoded = decode(result.content());
        int alphaAtCorner = (decoded.getRGB(0, 0) >> 24) & 0xFF;
        int alphaAtCenter = (decoded.getRGB(256, 256) >> 24) & 0xFF;

        assertEquals(0, alphaAtCorner);
        assertEquals(255, alphaAtCenter);
    }

    @Test
    @DisplayName("Embedded EXIF/comment metadata is stripped on re-encode")
    void shouldStripMetadata() {
        String marker = "GPSLatitude=51.5074;GPSLongitude=-0.1278";
        byte[] input = AvatarTestImages.jpegWithMetadata(marker);
        assertTrue(new String(input, StandardCharsets.ISO_8859_1).contains(marker),
                "fixture should contain the metadata marker");

        ProcessedAvatar result = processor.process(input);

        String output = new String(result.content(), StandardCharsets.ISO_8859_1);
        assertFalse(output.contains(marker));
        assertFalse(output.contains("GPSLatitude"));
        assertFalse(output.contains("Exif"));
    }

    @Test
    @DisplayName("Empty input is rejected")
    void shouldRejectEmptyInput() {
        InvalidAvatarException ex = assertThrows(InvalidAvatarException.class,
                () -> processor.process(new byte[0]));
        assertTrue(ex.getMessage().contains("select an image"));
    }

    @Test
    @DisplayName("Null input is rejected")
    void shouldRejectNullInput() {
        assertThrows(InvalidAvatarException.class, () -> processor.process(null));
    }

    @Test
    @DisplayName("Uploads larger than the configured maximum are rejected")
    void shouldRejectOversizedInput() {
        AvatarImageProcessor tiny = new AvatarImageProcessor(1024, 512);
        byte[] validButLarge = AvatarTestImages.jpeg(200, 200);
        assertTrue(validButLarge.length > 1024, "fixture should exceed the tiny limit");

        InvalidAvatarException ex = assertThrows(InvalidAvatarException.class,
                () -> tiny.process(validButLarge));
        assertTrue(ex.getMessage().contains("MB or smaller"));
    }

    @Test
    @DisplayName("WebP is explicitly rejected")
    void shouldRejectWebp() {
        InvalidAvatarException ex = assertThrows(InvalidAvatarException.class,
                () -> processor.process(AvatarTestImages.webp()));
        assertTrue(ex.getMessage().contains("WebP"));
    }

    @Test
    @DisplayName("SVG is explicitly rejected")
    void shouldRejectSvg() {
        InvalidAvatarException ex = assertThrows(InvalidAvatarException.class,
                () -> processor.process(AvatarTestImages.svg()));
        assertTrue(ex.getMessage().contains("SVG"));
    }

    @Test
    @DisplayName("Other unsupported formats are rejected")
    void shouldRejectGif() {
        InvalidAvatarException ex = assertThrows(InvalidAvatarException.class,
                () -> processor.process(AvatarTestImages.gif()));
        assertTrue(ex.getMessage().contains("JPEG or PNG"));
    }

    @Test
    @DisplayName("A file with a valid JPEG signature but corrupt pixels is rejected")
    void shouldRejectCorruptImage() {
        InvalidAvatarException ex = assertThrows(InvalidAvatarException.class,
                () -> processor.process(AvatarTestImages.corruptJpeg()));
        assertTrue(ex.getMessage().toLowerCase().contains("corrupt")
                || ex.getMessage().toLowerCase().contains("read"));
    }

    @Test
    @DisplayName("Processing is deterministic in output dimensions for square inputs")
    void shouldKeepSquareInputsSquare() throws Exception {
        ProcessedAvatar result = processor.process(AvatarTestImages.png(512, 512));
        BufferedImage decoded = decode(result.content());
        assertEquals(512, decoded.getWidth());
        assertEquals(512, decoded.getHeight());
    }

    private BufferedImage decode(byte[] content) throws Exception {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
        assertNotNull(image, "processed output should be a decodable image");
        return image;
    }

    private boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
