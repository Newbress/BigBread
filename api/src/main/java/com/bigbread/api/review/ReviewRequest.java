package com.bigbread.api.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record ReviewRequest(
        @Min(value = 1, message = "평점은 1~5점이에요.") @Max(value = 5, message = "평점은 1~5점이에요.") int rating,
        @Size(max = ReviewService.MAX_CONTENT_LENGTH, message = "리뷰는 1000자까지 쓸 수 있어요.") String content) {
}
