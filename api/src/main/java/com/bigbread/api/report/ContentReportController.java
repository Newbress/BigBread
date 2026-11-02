package com.bigbread.api.report;

import com.bigbread.api.auth.AppUserPrincipal;
import com.bigbread.api.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 리뷰·사진 신고 (로그인 필요). */
@RestController
@RequestMapping("/api/my/reports")
public class ContentReportController {

    public record Request(
            @NotNull ContentReportService.TargetType targetType,
            @NotNull @Positive Long targetId,
            @NotNull ContentReportService.Reason reason,
            @Size(max = 500) String detail) {
    }

    private final ContentReportService reports;

    public ContentReportController(ContentReportService reports) {
        this.reports = reports;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> report(@AuthenticationPrincipal AppUserPrincipal me, @Valid @RequestBody Request request) {
        if (request.reason() == ContentReportService.Reason.OTHER
                && (request.detail() == null || request.detail().isBlank())) {
            throw ApiException.badRequest("'기타'를 고르면 내용을 적어 주세요.");
        }
        reports.report(me.userId(), request.targetType(), request.targetId(), request.reason(), request.detail());
        return Map.of("message", "신고가 접수됐어요. 운영자가 확인할게요.");
    }
}
