package com.bigbread.api.review.photo;

import com.twelvemonkeys.imageio.metadata.Directory;
import com.twelvemonkeys.imageio.metadata.Entry;
import com.twelvemonkeys.imageio.metadata.jpeg.JPEG;
import com.twelvemonkeys.imageio.metadata.jpeg.JPEGSegment;
import com.twelvemonkeys.imageio.metadata.jpeg.JPEGSegmentUtil;
import com.twelvemonkeys.imageio.metadata.tiff.TIFF;
import com.twelvemonkeys.imageio.metadata.tiff.TIFFReader;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.stereotype.Component;

/**
 * 올라온 사진을 받아서 저장할 JPEG로 다시 만든다.
 * <ul>
 *   <li>형식은 확장자·Content-Type이 아니라 <b>파일 내용</b>으로 판별한다 (JPEG, PNG, WebP, HEIC).</li>
 *   <li>다시 인코딩하므로 EXIF(촬영 위치 GPS 등)와 숨은 데이터가 모두 사라진다.</li>
 *   <li>EXIF 방향대로 돌려서 저장하고, 긴 변이 {@code maxEdge}보다 크면 줄이고 작으면 그대로 둔다.</li>
 *   <li>투명한 PNG/WebP는 흰 배경에 합친다.</li>
 * </ul>
 */
@Component
public class PhotoProcessor {

    /** 저장할 사진과 썸네일. */
    public record Processed(byte[] main, byte[] thumb, int width, int height) {
    }

    enum Format { JPEG, PNG, WEBP, HEIC }

    static final String UNSUPPORTED = "지원하지 않는 사진 형식이에요. JPEG, PNG, WebP, HEIC 사진을 올려 주세요.";
    static final String UNREADABLE = "사진을 읽지 못했어요. 다른 사진으로 다시 시도해 주세요.";

    private final PhotoProperties props;
    private final HeifConverter heif;

    public PhotoProcessor(PhotoProperties props, HeifConverter heif) {
        this.props = props;
        this.heif = heif;
    }

    public Processed process(byte[] input) {
        if (input.length > props.maxBytes()) {
            throw new PhotoRejectedException("사진은 한 장에 " + props.maxBytes() / (1024 * 1024) + "MB까지 올릴 수 있어요.");
        }
        Format format = sniff(input);
        byte[] bytes = input;
        if (format == Format.HEIC) {
            bytes = heif.toJpeg(input);
            format = sniff(bytes);
            if (format == null) {
                throw new PhotoRejectedException(UNREADABLE);
            }
        }
        try {
            BufferedImage decoded = decode(bytes);
            int orientation = format == Format.JPEG ? exifOrientation(bytes) : 1;
            BufferedImage upright = orient(decoded, orientation);
            BufferedImage main = fit(upright, props.maxEdge());
            BufferedImage thumb = fit(main, props.thumbEdge());
            return new Processed(encode(main, props.quality()), encode(thumb, 0.82), main.getWidth(), main.getHeight());
        } catch (OutOfMemoryError e) {
            throw new PhotoRejectedException("사진이 너무 커서 처리하지 못했어요. 더 작은 사진으로 올려 주세요.");
        } catch (IOException | RuntimeException e) {
            if (e instanceof PhotoRejectedException rejected) {
                throw rejected;
            }
            throw new PhotoRejectedException(UNREADABLE);
        }
    }

    /** 파일 앞부분의 서명으로 형식을 알아낸다. 모르는 형식이면 거부한다. */
    static Format sniff(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return Format.JPEG;
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return Format.PNG;
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return Format.WEBP;
        }
        if (b.length >= 12 && b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p') {
            String brand = new String(b, 8, 4, java.nio.charset.StandardCharsets.ISO_8859_1);
            if (List.of("heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1").contains(brand)) {
                return Format.HEIC;
            }
        }
        throw new PhotoRejectedException(UNSUPPORTED);
    }

    /** 해상도를 먼저 확인하고(거대한 이미지 방어), 너무 크면 읽는 도중에 줄여서 메모리를 아낀다. */
    private BufferedImage decode(byte[] bytes) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw new PhotoRejectedException(UNSUPPORTED);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if ((long) width * height > props.maxPixels()) {
                    throw new PhotoRejectedException("사진 해상도가 너무 커요. 더 작은 사진으로 올려 주세요.");
                }
                ImageReadParam param = reader.getDefaultReadParam();
                int subsample = Math.max(1, Math.max(width, height) / props.maxEdge());
                if (subsample > 1) {
                    param.setSourceSubsampling(subsample, subsample, 0, 0);
                }
                return reader.read(0, param);
            } finally {
                reader.dispose();
            }
        }
    }

    /** JPEG의 EXIF 방향(1~8). 없거나 읽을 수 없으면 1. */
    static int exifOrientation(byte[] jpeg) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(jpeg))) {
            List<JPEGSegment> segments = JPEGSegmentUtil.readSegments(in, JPEG.APP1, "Exif");
            if (segments.isEmpty()) {
                return 1;
            }
            try (InputStream data = segments.get(0).data()) {
                data.read(); // "Exif\0" 뒤의 패딩 한 바이트
                Directory exif = new TIFFReader().read(new MemoryCacheImageInputStream(data));
                Entry entry = exif.getEntryById(TIFF.TAG_ORIENTATION);
                if (entry != null && entry.getValue() instanceof Number n && n.intValue() >= 1 && n.intValue() <= 8) {
                    return n.intValue();
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // 방향 정보를 못 읽으면 그대로 쓴다.
        }
        return 1;
    }

    /** EXIF 방향에 맞게 돌리고, 투명한 부분은 흰색으로 채운 RGB 이미지를 만든다. */
    static BufferedImage orient(BufferedImage src, int orientation) {
        int w = src.getWidth();
        int h = src.getHeight();
        boolean swap = orientation >= 5;
        AffineTransform t = switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0);
            case 3 -> new AffineTransform(-1, 0, 0, -1, w, h);
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, h);
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
            case 6 -> new AffineTransform(0, 1, -1, 0, h, 0);
            case 7 -> new AffineTransform(0, -1, -1, 0, h, w);
            case 8 -> new AffineTransform(0, -1, 1, 0, 0, w);
            default -> new AffineTransform();
        };
        BufferedImage out = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, out.getWidth(), out.getHeight());
            g.drawImage(src, t, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    /** 긴 변이 maxEdge를 넘으면 비율을 유지하며 줄인다 (절반씩 줄이다 마지막에 맞춰서 화질 손상을 줄인다). */
    static BufferedImage fit(BufferedImage src, int maxEdge) {
        int longEdge = Math.max(src.getWidth(), src.getHeight());
        if (longEdge <= maxEdge) {
            return src;
        }
        double scale = (double) maxEdge / longEdge;
        int targetW = Math.max(1, (int) Math.round(src.getWidth() * scale));
        int targetH = Math.max(1, (int) Math.round(src.getHeight() * scale));
        BufferedImage current = src;
        while (current.getWidth() / 2 >= targetW && current.getHeight() / 2 >= targetH) {
            current = resize(current, current.getWidth() / 2, current.getHeight() / 2, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        }
        return resize(current, targetW, targetH, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    }

    private static BufferedImage resize(BufferedImage src, int w, int h, Object interpolation) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    /** 메타데이터 없이 JPEG로 인코딩한다 (새 이미지를 쓰므로 원본의 EXIF는 따라오지 않는다). */
    private static byte[] encode(BufferedImage image, double quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IOException("JPEG 인코더가 없어요.");
        }
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality((float) quality);
            param.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
            writer.setOutput(out);
            writer.write(null, new IIOImage(image, null, null), param);
            out.flush();
            return bytes.toByteArray();
        } finally {
            writer.dispose();
        }
    }
}
