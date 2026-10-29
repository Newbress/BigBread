package com.bigbread.api.review.photo;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;

/** 테스트용 사진 바이트를 만든다. */
public final class PhotoFixtures {

    private PhotoFixtures() {
    }

    /** 왼쪽 절반 빨강, 오른쪽 절반 파랑. */
    public static BufferedImage redBlue(int w, int h) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, w / 2, h);
        g.setColor(Color.BLUE);
        g.fillRect(w / 2, 0, w - w / 2, h);
        g.dispose();
        return image;
    }

    public static byte[] jpeg(int w, int h) {
        return write(redBlue(w, h), "jpg");
    }

    public static byte[] png(BufferedImage image) {
        return write(image, "png");
    }

    public static byte[] gif(int w, int h) {
        return write(redBlue(w, h), "gif");
    }

    /** EXIF 방향(1~8)과 가짜 GPS 문자열이 든 JPEG. 촬영 정보가 저장본에 남지 않는지 볼 때 쓴다. */
    public static byte[] jpegWithExif(int w, int h, int orientation) {
        byte[] jpeg = jpeg(w, h);
        byte[] tiff = {
                'I', 'I', 0x2A, 0x00, 0x08, 0x00, 0x00, 0x00, // 헤더, 첫 IFD 위치
                0x01, 0x00,                                   // 항목 1개
                0x12, 0x01, 0x03, 0x00, 0x01, 0x00, 0x00, 0x00, (byte) orientation, 0x00, 0x00, 0x00, // Orientation
                0x00, 0x00, 0x00, 0x00                        // 다음 IFD 없음
        };
        byte[] marker = "Exif\0\0".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        byte[] secret = "GPS-SECRET-LOCATION".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        int length = 2 + marker.length + tiff.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2); // SOI
        out.write(0xFF);
        out.write(0xE1);
        out.write(length >> 8);
        out.write(length & 0xFF);
        out.writeBytes(marker);
        out.writeBytes(tiff);
        // 두 번째 APP1(주석 영역)에 위치 정보 흉내를 넣는다.
        out.write(0xFF);
        out.write(0xFE);
        out.write((secret.length + 2) >> 8);
        out.write((secret.length + 2) & 0xFF);
        out.writeBytes(secret);
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    private static byte[] write(BufferedImage image, String format) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, format, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
