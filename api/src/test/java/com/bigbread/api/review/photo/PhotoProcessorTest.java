package com.bigbread.api.review.photo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class PhotoProcessorTest {

    private static final PhotoProperties PROPS = new PhotoProperties(null, null, null, null, null, null, null, null, null);

    private final PhotoProcessor processor = new PhotoProcessor(PROPS, new CommandHeifConverter(PROPS));

    private static BufferedImage decode(byte[] bytes) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    private static boolean near(int rgb, Color expected) {
        Color c = new Color(rgb);
        return Math.abs(c.getRed() - expected.getRed()) < 40 && Math.abs(c.getGreen() - expected.getGreen()) < 40
                && Math.abs(c.getBlue() - expected.getBlue()) < 40;
    }

    @Test
    void 큰_사진은_긴_변_2560으로_줄이고_작은_사진은_그대로_둔다() throws Exception {
        PhotoProcessor.Processed big = processor.process(PhotoFixtures.jpeg(4000, 3000));
        assertThat(big.width()).isEqualTo(2560);
        assertThat(big.height()).isEqualTo(1920);
        BufferedImage thumb = decode(big.thumb());
        assertThat(Math.max(thumb.getWidth(), thumb.getHeight())).isEqualTo(480);

        PhotoProcessor.Processed small = processor.process(PhotoFixtures.jpeg(800, 600));
        assertThat(small.width()).isEqualTo(800);
        assertThat(small.height()).isEqualTo(600);
        assertThat(decode(small.main()).getWidth()).isEqualTo(800);
    }

    @Test
    void 저장본에는_EXIF와_숨은_데이터가_남지_않는다() throws Exception {
        byte[] original = PhotoFixtures.jpegWithExif(600, 400, 1);
        assertThat(new String(original, StandardCharsets.ISO_8859_1)).contains("GPS-SECRET-LOCATION").contains("Exif");

        PhotoProcessor.Processed processed = processor.process(original);
        for (byte[] file : List.of(processed.main(), processed.thumb())) {
            String text = new String(file, StandardCharsets.ISO_8859_1);
            assertThat(text).doesNotContain("GPS-SECRET-LOCATION").doesNotContain("Exif");
        }
    }

    @Test
    void EXIF_방향대로_돌려서_저장한다() throws Exception {
        // 가로 400x200, 왼쪽 빨강/오른쪽 파랑. 방향 6(시계 방향 90도)이면 세로 200x400이 되고 위가 빨강이다.
        PhotoProcessor.Processed rotated = processor.process(PhotoFixtures.jpegWithExif(400, 200, 6));
        assertThat(rotated.width()).isEqualTo(200);
        assertThat(rotated.height()).isEqualTo(400);
        BufferedImage image = decode(rotated.main());
        assertThat(near(image.getRGB(100, 40), Color.RED)).isTrue();
        assertThat(near(image.getRGB(100, 360), Color.BLUE)).isTrue();

        // 방향 8(반시계 90도)이면 위가 파랑이다.
        BufferedImage ccw = decode(processor.process(PhotoFixtures.jpegWithExif(400, 200, 8)).main());
        assertThat(near(ccw.getRGB(100, 40), Color.BLUE)).isTrue();

        // 방향 3(180도)이면 가로 그대로, 왼쪽이 파랑이다.
        BufferedImage upsideDown = decode(processor.process(PhotoFixtures.jpegWithExif(400, 200, 3)).main());
        assertThat(upsideDown.getWidth()).isEqualTo(400);
        assertThat(near(upsideDown.getRGB(40, 100), Color.BLUE)).isTrue();

        // 방향 2(좌우 반전)
        BufferedImage mirrored = decode(processor.process(PhotoFixtures.jpegWithExif(400, 200, 2)).main());
        assertThat(near(mirrored.getRGB(40, 100), Color.BLUE)).isTrue();
    }

    @Test
    void 투명한_PNG는_흰_배경에_합친다() throws Exception {
        BufferedImage transparent = new BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB);
        PhotoProcessor.Processed processed = processor.process(PhotoFixtures.png(transparent));
        assertThat(near(decode(processed.main()).getRGB(100, 50), Color.WHITE)).isTrue();
    }

    @Test
    void WebP도_읽는다() throws Exception {
        byte[] webp = getClass().getResourceAsStream("/photos/sample.webp").readAllBytes();
        PhotoProcessor.Processed processed = processor.process(webp);
        assertThat(processed.width()).isEqualTo(640);
        assertThat(processed.height()).isEqualTo(480);
    }

    @Test
    void 형식은_확장자가_아니라_내용으로_판별한다() {
        assertThatThrownBy(() -> processor.process("그냥 글자".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(PhotoRejectedException.class).hasMessage(PhotoProcessor.UNSUPPORTED);
        assertThatThrownBy(() -> processor.process(PhotoFixtures.gif(50, 50)))
                .isInstanceOf(PhotoRejectedException.class).hasMessage(PhotoProcessor.UNSUPPORTED);
        // 서명만 JPEG이고 내용이 깨진 파일
        byte[] broken = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01, 0x02};
        assertThatThrownBy(() -> processor.process(broken)).isInstanceOf(PhotoRejectedException.class);
    }

    @Test
    void 한도를_넘는_크기와_해상도는_거부한다() {
        assertThatThrownBy(() -> processor.process(new byte[10 * 1024 * 1024 + 1]))
                .isInstanceOf(PhotoRejectedException.class).hasMessageContaining("10MB");

        PhotoProperties small = new PhotoProperties(null, null, null, null, null, null, 1_000L, null, null);
        PhotoProcessor strict = new PhotoProcessor(small, new CommandHeifConverter(small));
        assertThatThrownBy(() -> strict.process(PhotoFixtures.jpeg(100, 100)))
                .isInstanceOf(PhotoRejectedException.class).hasMessageContaining("해상도");
    }

    @Test
    void HEIC는_변환_명령이_있으면_JPEG로_바꿔_저장한다() throws Exception {
        assumeTrue(commandExists("heif-convert"), "heif-convert가 설치된 환경에서만 확인한다");
        byte[] heic = getClass().getResourceAsStream("/photos/sample.heic").readAllBytes();
        PhotoProcessor.Processed processed = processor.process(heic);
        assertThat(processed.width()).isEqualTo(1200);
        assertThat(processed.height()).isEqualTo(800);
        assertThat(decode(processed.main())).isNotNull();
    }

    @Test
    void HEIC_변환_명령이_없으면_JPEG로_저장하라고_안내한다() throws Exception {
        byte[] heic = getClass().getResourceAsStream("/photos/sample.heic").readAllBytes();

        PhotoProperties off = new PhotoProperties(null, null, null, null, null, null, null, "", null);
        assertThatThrownBy(() -> new PhotoProcessor(off, new CommandHeifConverter(off)).process(heic))
                .isInstanceOf(PhotoRejectedException.class).hasMessage(CommandHeifConverter.UNAVAILABLE);

        PhotoProperties missing = new PhotoProperties(null, null, null, null, null, null, null, "no-such-heif-tool {in} {out}", null);
        assertThatThrownBy(() -> new PhotoProcessor(missing, new CommandHeifConverter(missing)).process(heic))
                .isInstanceOf(PhotoRejectedException.class).hasMessage(CommandHeifConverter.UNAVAILABLE);
    }

    private static boolean commandExists(String name) {
        String path = System.getenv("PATH");
        if (path == null) {
            return false;
        }
        for (String dir : path.split(java.io.File.pathSeparator)) {
            if (Files.isExecutable(Path.of(dir, name))) {
                return true;
            }
        }
        return false;
    }
}
