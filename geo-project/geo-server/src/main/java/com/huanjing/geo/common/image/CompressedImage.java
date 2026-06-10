package com.huanjing.geo.common.image;

public record CompressedImage(
        byte[] bytes,
        String contentType,
        String fileName,
        String fileType,
        long size
) {
}
