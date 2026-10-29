package com.bigbread.api.review.photo;

import com.bigbread.api.auth.AppUserPrincipal;
import com.bigbread.api.common.ApiException;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 사진 보기는 누구나, 올리기·지우기는 로그인(/api/my/**)이 필요하다. */
@RestController
public class PhotoController {

    private final PhotoService photos;

    public PhotoController(PhotoService photos) {
        this.photos = photos;
    }

    @PostMapping(value = "/api/my/reviews/{bakeryId}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PhotoService.Photo upload(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable long bakeryId,
                                     @RequestParam("file") MultipartFile file) {
        return photos.add(me.userId(), bakeryId, file);
    }

    @DeleteMapping("/api/my/reviews/{bakeryId}/photos/{photoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable long bakeryId,
                       @PathVariable long photoId) {
        photos.delete(me.userId(), bakeryId, photoId);
    }

    @GetMapping("/api/photos/{id}")
    public ResponseEntity<byte[]> original(@PathVariable String id) {
        return serve(id, false);
    }

    @GetMapping("/api/photos/{id}/thumb")
    public ResponseEntity<byte[]> thumbnail(@PathVariable String id) {
        return serve(id, true);
    }

    private ResponseEntity<byte[]> serve(String id, boolean thumb) {
        PhotoService.Image image = photos.load(id, thumb).orElseThrow(() -> ApiException.notFound("사진을 찾을 수 없어요."));
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                // 신고로 숨겨진 사진이 오래 남지 않게 하루만 캐시한다.
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                .body(image.bytes());
    }
}
