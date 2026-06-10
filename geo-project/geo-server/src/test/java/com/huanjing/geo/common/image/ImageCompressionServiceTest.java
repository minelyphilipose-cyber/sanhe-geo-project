package com.huanjing.geo.common.image;

import com.huanjing.geo.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageCompressionServiceTest {

    private final ImageCompressionService service = new ImageCompressionService();

    @Test
    void compressesLargePngToJpegUnder500Kb() throws Exception {
        byte[] source = noisyPng(2400, 1600);
        assertThat(source.length).isGreaterThan(ImageCompressionService.MAX_IMAGE_BYTES);

        MockMultipartFile file = new MockMultipartFile("file", "sample.png", "image/png", source);
        CompressedImage compressed = service.compressToLimit(file);

        assertThat(compressed.size()).isLessThanOrEqualTo(ImageCompressionService.MAX_IMAGE_BYTES);
        assertThat(compressed.contentType()).isEqualTo("image/jpeg");
        assertThat(compressed.fileName()).isEqualTo("sample.jpg");
        assertThat(compressed.fileType()).isEqualTo("jpg");
        assertThat(ImageIO.read(new java.io.ByteArrayInputStream(compressed.bytes()))).isNotNull();
    }

    @Test
    void keepsSmallImageUnchanged() throws Exception {
        byte[] source = png(32, 32);

        MockMultipartFile file = new MockMultipartFile("file", "icon.png", "image/png", source);
        CompressedImage compressed = service.compressToLimit(file);

        assertThat(compressed.bytes()).isEqualTo(source);
        assertThat(compressed.contentType()).isEqualTo("image/png");
        assertThat(compressed.fileName()).isEqualTo("icon.png");
        assertThat(compressed.fileType()).isEqualTo("png");
    }

    @Test
    void rejectsLargeUnsupportedImageFormat() {
        byte[] source = ("<svg>" + "x".repeat(ImageCompressionService.MAX_IMAGE_BYTES) + "</svg>").getBytes();

        MockMultipartFile file = new MockMultipartFile("file", "vector.svg", "image/svg+xml", source);

        assertThatThrownBy(() -> service.compressToLimit(file))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无法压缩");
    }

    private byte[] noisyPng(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = (x * 31 + y * 17) & 0xff;
                int g = (x * 11 + y * 37) & 0xff;
                int b = (x * 7 + y * 13) & 0xff;
                image.setRGB(x, y, new Color(r, g, b).getRGB());
            }
        }
        return writePng(image);
    }

    private byte[] png(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, Color.BLUE.getRGB());
            }
        }
        return writePng(image);
    }

    private byte[] writePng(BufferedImage image) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
