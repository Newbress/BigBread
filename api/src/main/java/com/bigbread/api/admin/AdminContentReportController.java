package com.bigbread.api.admin;

import com.bigbread.api.auth.AppUserPrincipal;
import com.bigbread.api.common.ApiException;
import com.bigbread.api.common.PageResponse;
import com.bigbread.api.report.ContentReportService.TargetType;
import com.bigbread.api.review.photo.PhotoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
class AdminContentReportController {

    record ResolveRequest(@NotNull TargetType targetType, @NotNull @Positive Long targetId,
                          @NotNull AdminContentReportService.Action action) {
    }

    private final AdminContentReportService service;
    private final PhotoService photos;

    AdminContentReportController(AdminContentReportService service, PhotoService photos) {
        this.service = service;
        this.photos = photos;
    }

    @GetMapping("/api/admin/content-reports")
    PageResponse<AdminContentReportService.Group> list(@RequestParam(defaultValue = "PENDING") String status,
                                                       @RequestParam(defaultValue = "0") @Min(0) int page,
                                                       @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return service.list(!"HANDLED".equals(status), page, size);
    }

    @PostMapping("/api/admin/content-reports/resolve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resolve(@AuthenticationPrincipal AppUserPrincipal me, @Valid @RequestBody ResolveRequest request) {
        service.resolve(me.userId(), request.targetType(), request.targetId(), request.action());
    }

    /** 신고된 사진을 확인할 때 쓴다 (숨겨진 사진도 볼 수 있다). */
    @GetMapping("/api/admin/photos/{id}")
    ResponseEntity<byte[]> photo(@PathVariable String id) {
        return image(id, false);
    }

    @GetMapping("/api/admin/photos/{id}/thumb")
    ResponseEntity<byte[]> thumb(@PathVariable String id) {
        return image(id, true);
    }

    private ResponseEntity<byte[]> image(String id, boolean thumb) {
        PhotoService.Image image = photos.loadAny(id, thumb).orElseThrow(() -> ApiException.notFound("사진을 찾을 수 없어요."));
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate())
                .body(image.bytes());
    }
}
