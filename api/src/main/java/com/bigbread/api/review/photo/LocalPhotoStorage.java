package com.bigbread.api.review.photo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 로컬 폴더에 저장한다 (개발용). 폴더 밖으로 나가는 키는 거부한다. */
@Component
@ConditionalOnProperty(name = "bigbread.photos.storage", havingValue = "local", matchIfMissing = true)
public class LocalPhotoStorage implements PhotoStorage {

    private final Path root;

    public LocalPhotoStorage(PhotoProperties properties) {
        this.root = properties.dir().toAbsolutePath().normalize();
    }

    @Override
    public void put(String key, byte[] data) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, data);
        } catch (IOException e) {
            throw new UncheckedIOException("사진을 저장하지 못했어요: " + key, e);
        }
    }

    @Override
    public Optional<byte[]> read(String key) {
        Path target = resolve(key);
        try {
            return Files.isRegularFile(target) ? Optional.of(Files.readAllBytes(target)) : Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("사진을 읽지 못했어요: " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("사진을 지우지 못했어요: " + key, e);
        }
    }

    private Path resolve(String key) {
        if (!PhotoKeys.isValidKey(key)) {
            throw new IllegalArgumentException("잘못된 사진 키입니다.");
        }
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("잘못된 사진 키입니다.");
        }
        return path;
    }
}
