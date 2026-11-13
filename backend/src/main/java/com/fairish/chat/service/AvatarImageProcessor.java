package com.fairish.chat.service;

import com.fairish.chat.exception.InvalidAvatarException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Validates uploaded avatar bytes and normalizes them to a square image.
 *
 * <p>Format detection uses magic bytes, not the client-supplied content type
 * or filename. Re-encoding through ImageIO also strips EXIF/GPS and any other
 * embedded metadata.</p>
 */
@Component
public class AvatarImageProcessor {

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };
    private static final byte[] RIFF_MAGIC = {'R', 'I', 'F', 'F'};
    private static final byte[] WEBP_MAGIC = {'W', 'E', 'B', 'P'};
    private static final long MAX_PIXELS = 40_000_000L;

    private final long maxUploadBytes;
    private final int outputSize;

    public AvatarImageProcessor(
            @Value("${app.avatar.max-upload-bytes:5242880}") long maxUploadBytes,
            @Value("${app.avatar.output-size:512}") int outputSize) {
        this.maxUploadBytes = maxUploadBytes;
        this.outputSize = outputSize;
    }

    public ProcessedAvatar process(byte[] input) {
        if (input == null || input.length == 0) {
            throw new InvalidAvatarException("Please select an image file");
        }
        if (input.length > maxUploadBytes) {
            throw new InvalidAvatarException("Avatar must be " + (maxUploadBytes / (1024 * 1024)) + " MB or smaller");
        }

        AvatarFormat format = detectFormat(input);

        BufferedImage source;
        try {
            source = ImageIO.read(new ByteArrayInputStream(input));
        } catch (IOException ex) {
            throw new InvalidAvatarException("The uploaded image could not be read", ex);
        }
        if (source == null || source.getWidth() <= 0 || source.getHeight() <= 0) {
            throw new InvalidAvatarException("The uploaded image is corrupt or malformed");
        }
        if ((long) source.getWidth() * source.getHeight() > MAX_PIXELS) {
            throw new InvalidAvatarException("The uploaded image dimensions are too large");
        }

        BufferedImage normalized = cropAndScale(source, format);
        byte[] output = encode(normalized, format);
        return new ProcessedAvatar(output, format.getContentType(), outputSize, outputSize, format);
    }

    public long getMaxUploadBytes() {
        return maxUploadBytes;
    }

    public int getOutputSize() {
        return outputSize;
    }

    private AvatarFormat detectFormat(byte[] data) {
        if (startsWith(data, JPEG_MAGIC)) {
            return AvatarFormat.JPEG;
        }
        if (startsWith(data, PNG_MAGIC)) {
            return AvatarFormat.PNG;
        }
        if (isWebp(data)) {
            throw new InvalidAvatarException("WebP images are not supported. Upload a JPEG or PNG.");
        }
        if (looksLikeSvg(data)) {
            throw new InvalidAvatarException("SVG images are not supported. Upload a JPEG or PNG.");
        }
        throw new InvalidAvatarException("Unsupported image format. Upload a JPEG or PNG.");
    }

    private boolean isWebp(byte[] data) {
        return data.length >= 12
                && startsWith(data, RIFF_MAGIC)
                && matchesAt(data, 8, WEBP_MAGIC);
    }

    private boolean looksLikeSvg(byte[] data) {
        int limit = Math.min(data.length, 4096);
        String head = new String(data, 0, limit, StandardCharsets.ISO_8859_1)
                .toLowerCase(Locale.ROOT);
        return head.contains("<svg") || head.contains("image/svg");
    }

    private boolean startsWith(byte[] data, byte[] prefix) {
        return matchesAt(data, 0, prefix);
    }

    private boolean matchesAt(byte[] data, int offset, byte[] expected) {
        if (data.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if (data[offset + i] != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private BufferedImage cropAndScale(BufferedImage source, AvatarFormat format) {
        int width = source.getWidth();
        int height = source.getHeight();
        int side = Math.min(width, height);
        int x = (width - side) / 2;
        int y = (height - side) / 2;

        int outputType = format == AvatarFormat.JPEG
                ? BufferedImage.TYPE_INT_RGB
                : BufferedImage.TYPE_INT_ARGB;
        BufferedImage output = new BufferedImage(outputSize, outputSize, outputType);

        Graphics2D graphics = output.createGraphics();
        try {
            if (format == AvatarFormat.JPEG) {
                // Flatten transparency onto white before JPEG encoding.
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, outputSize, outputSize);
            }
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,
                    RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source,
                    0, 0, outputSize, outputSize,
                    x, y, x + side, y + side,
                    null);
        } finally {
            graphics.dispose();
        }
        return output;
    }

    private byte[] encode(BufferedImage image, AvatarFormat format) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            boolean written = ImageIO.write(image, format.getImageIoName(), output);
            if (!written) {
                throw new InvalidAvatarException("No image encoder is available for the uploaded format");
            }
        } catch (IOException ex) {
            throw new InvalidAvatarException("The uploaded image could not be processed", ex);
        }
        return output.toByteArray();
    }
}
