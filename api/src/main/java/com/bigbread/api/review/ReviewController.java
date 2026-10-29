package com.bigbread.api.review;

import com.bigbread.api.auth.AppUserPrincipal;
import com.bigbread.api.common.ApiException;
import com.bigbread.api.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 리뷰 조회는 누구나, 쓰기·수정·삭제는 로그인(/api/my/**)이 필요하다. */
@RestController
@Validated
public class ReviewController {

    private final ReviewService reviews;

    public ReviewController(ReviewService reviews) {
        this.reviews = reviews;
    }

    @GetMapping("/api/bakeries/{bakeryId}/reviews")
    public PageResponse<ReviewService.Item> list(
            @AuthenticationPrincipal AppUserPrincipal me,
            @PathVariable long bakeryId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return reviews.list(bakeryId, me == null ? null : me.userId(), page, size);
    }

    @GetMapping("/api/my/reviews")
    public List<ReviewService.MyReview> mine(@AuthenticationPrincipal AppUserPrincipal me) {
        return reviews.listMine(me.userId());
    }

    @GetMapping("/api/my/reviews/{bakeryId}")
    public ReviewService.Item mineFor(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable long bakeryId) {
        return reviews.mine(me.userId(), bakeryId).orElseThrow(() -> ApiException.notFound("쓴 리뷰가 없어요."));
    }

    @PutMapping("/api/my/reviews/{bakeryId}")
    public ReviewService.Item save(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable long bakeryId,
                                   @Valid @RequestBody ReviewRequest request) {
        return reviews.save(me.userId(), bakeryId, request.rating(), request.content());
    }

    @DeleteMapping("/api/my/reviews/{bakeryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable long bakeryId) {
        reviews.delete(me.userId(), bakeryId);
    }
}
