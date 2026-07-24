package com.huanjing.geo.common.image;

import com.huanjing.geo.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;

@Service
public class ImageCompressionService {

    public static final int MAX_IMAGE_BYTES = 500 * 1024;

    private static final long MAX_PIXELS = 50_000_000L;
    private static final int MIN_DIMENSION = 320;
    private static final float MIN_QUALITY = 0.35f;
    private static final float MAX_QUALITY = 0.88f;

    public CompressedImage compressToLimit(MultipartFile file) {
        byte[] original = readBytes(file);
        String originalName = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename().trim()
                : "image";
        String contentType = file.getContentType();
        String originalType = resolveFileType(originalName, contentType);

        if (isPassThroughType(originalType)) {
            if (original.length > MAX_IMAGE_BYTES) {
                throw new BizException(400, "当前图片格式无法压缩，请上传 500KB 以内的 SVG/GIF/WebP，或改用 JPG/PNG");
            }
            return new CompressedImage(
                    original,
                    StringUtils.hasText(contentType) ? contentType : contentTypeFor(originalType),
                    originalName,
                    originalType,
                    original.length
            );
        }

        if (original.length <= MAX_IMAGE_BYTES) {
            return new CompressedImage(
                    original,
                    StringUtils.hasText(contentType) ? contentType : contentTypeFor(originalType),
                    originalName,
                    originalType,
                    original.length
            );
        }

        BufferedImage source = readImage(original);
        validateImageSize(source);

        double scale = 1.0d;
        while (true) {
            int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
            BufferedImage scaled = scaleAndFlatten(source, width, height);
            byte[] lowestQualityBytes = writeJpeg(scaled, MIN_QUALITY);

            if (lowestQualityBytes.length <= MAX_IMAGE_BYTES) {
                byte[] optimized = optimizeQuality(scaled);
                String jpgName = replaceExtension(originalName, "jpg");
                return new CompressedImage(optimized, "image/jpeg", jpgName, "jpg", optimized.length);
            }

            if (width <= MIN_DIMENSION || height <= MIN_DIMENSION) {
                break;
            }

            double ratio = Math.sqrt((double) MAX_IMAGE_BYTES / lowestQualityBytes.length) * 0.92d;
            scale *= Math.max(0.55d, Math.min(0.85d, ratio));
        }

        throw new BizException(400, "图片压缩后仍超过 500KB，请上传分辨率更低的 JPG/PNG 图片");
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new BizException(400, "读取上传图片失败", ex);
        }
    }

    private BufferedImage readImage(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw new BizException(400, "图片超过 500KB 且当前格式无法压缩，请上传 JPG/PNG 图片");
            }
            return image;
        } catch (IOException ex) {
            throw new BizException(400, "解析上传图片失败", ex);
        }
    }

    private void validateImageSize(BufferedImage image) {
        long pixels = (long) image.getWidth() * image.getHeight();
        if (pixels > MAX_PIXELS) {
            throw new BizException(400, "图片分辨率过大，请先降低分辨率后再上传");
        }
    }

    private BufferedImage scaleAndFlatten(BufferedImage source, int width, int height) {
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setColor(java.awt.Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private byte[] optimizeQuality(BufferedImage image) {
        byte[] best = writeJpeg(image, MIN_QUALITY);
        float low = MIN_QUALITY;
        float high = MAX_QUALITY;
        for (int i = 0; i < 7; i++) {
            float mid = (low + high) / 2f;
            byte[] candidate = writeJpeg(image, mid);
            if (candidate.length <= MAX_IMAGE_BYTES) {
                best = candidate;
                low = mid;
            } else {
                high = mid;
            }
        }
        return best;
    }

    private byte[] writeJpeg(BufferedImage image, float quality) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new BizException(500, "JPG 图片编码器不可用");
        }
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            ImageWriteParam params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(quality);
            writer.write(null, new IIOImage(image, null, null), params);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new BizException(500, "压缩图片失败", ex);
        } finally {
            writer.dispose();
        }
    }

    private String resolveFileType(String fileName, String contentType) {
        String suffix = suffix(fileName);
        if (StringUtils.hasText(suffix)) {
            return suffix;
        }
        if (!StringUtils.hasText(contentType)) {
            return "jpg";
        }
        String normalized = contentType.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            case "image/svg+xml" -> "svg";
            default -> "jpg";
        };
    }

    private String suffix(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return null;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String contentTypeFor(String fileType) {
        return switch (fileType == null ? "" : fileType.toLowerCase(Locale.ROOT)) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "svg" -> "image/svg+xml";
            default -> "application/octet-stream";
        };
    }

    private boolean isPassThroughType(String fileType) {
        return switch (fileType == null ? "" : fileType.toLowerCase(Locale.ROOT)) {
            case "svg", "gif", "webp" -> true;
            default -> false;
        };
    }

    private String replaceExtension(String fileName, String extension) {
        String baseName = StringUtils.hasText(fileName) ? fileName.trim() : "image";
        int dot = baseName.lastIndexOf('.');
        if (dot > 0) {
            baseName = baseName.substring(0, dot);
        }
        return baseName + "." + extension;
    }

}
