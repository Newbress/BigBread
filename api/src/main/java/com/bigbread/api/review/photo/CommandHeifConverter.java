package com.bigbread.api.review.photo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 외부 명령(libheif의 {@code heif-convert}, 또는 ImageMagick {@code magick})으로 HEIC를 JPEG로 바꾼다.
 * 쉘을 거치지 않고, 업로드 내용과 무관한 고정 인자와 임시 폴더의 경로만 넘긴다.
 * 아이폰 사진은 대부분 브라우저(Safari)가 올리기 전에 JPEG로 바꿔 주기 때문에, 이 경로는 데스크톱에서 HEIC 파일을 직접 고른 경우에 쓰인다.
 */
@Component
public class CommandHeifConverter implements HeifConverter {

    private static final Logger log = LoggerFactory.getLogger(CommandHeifConverter.class);
    private static final long TIMEOUT_SECONDS = 30;
    static final String UNAVAILABLE =
            "이 서버에서는 HEIC 사진을 바로 올릴 수 없어요. 사진을 JPEG로 저장해서 다시 올려 주세요.";
    static final String FAILED = "HEIC 사진을 읽지 못했어요. 사진을 JPEG로 저장해서 다시 올려 주세요.";

    private final List<String> template;

    public CommandHeifConverter(PhotoProperties properties) {
        String command = properties.heifCommand().strip();
        this.template = command.isEmpty() ? List.of() : List.of(command.split("\\s+"));
    }

    @Override
    public byte[] toJpeg(byte[] heic) {
        if (template.isEmpty()) {
            throw new PhotoRejectedException(UNAVAILABLE);
        }
        Path dir = null;
        try {
            dir = Files.createTempDirectory("bigbread-heic-");
            Path in = dir.resolve("in.heic");
            Path out = dir.resolve("out.jpg");
            Files.write(in, heic);

            List<String> command = new ArrayList<>();
            for (String token : template) {
                command.add(switch (token) {
                    case "{in}" -> in.toString();
                    case "{out}" -> out.toString();
                    default -> token;
                });
            }
            Process process;
            try {
                process = new ProcessBuilder(command)
                        .redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .start();
            } catch (IOException e) {
                log.warn("HEIC 변환 명령을 실행하지 못했어요 ({}): {}", template.get(0), e.getMessage());
                throw new PhotoRejectedException(UNAVAILABLE);
            }
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                log.warn("HEIC 변환이 {}초 안에 끝나지 않았어요.", TIMEOUT_SECONDS);
                throw new PhotoRejectedException(FAILED);
            }
            if (process.exitValue() != 0) {
                throw new PhotoRejectedException(FAILED);
            }
            Path result = Files.exists(out) ? out : firstJpeg(dir);
            if (result == null) {
                throw new PhotoRejectedException(FAILED);
            }
            return Files.readAllBytes(result);
        } catch (IOException e) {
            log.warn("HEIC 변환 중 입출력 오류: {}", e.getMessage());
            throw new PhotoRejectedException(FAILED);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PhotoRejectedException(FAILED);
        } finally {
            deleteQuietly(dir);
        }
    }

    /** 사진이 여러 장 든 파일은 {@code out-1.jpg}처럼 번호가 붙어 나온다. 첫 장을 쓴다. */
    private static Path firstJpeg(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".jpg")).sorted().findFirst().orElse(null);
        }
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try (Stream<Path> files = Files.walk(dir)) {
            files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
            // 임시 폴더는 OS가 정리한다.
        }
    }
}
