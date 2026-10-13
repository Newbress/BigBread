package com.bigbread.api.route;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 동선 계산 요청.
 *
 * @param bakeryIds 방문할 빵집 ID (1~{@value RouteOptimizer#MAX_STOPS}개, 중복 불가)
 * @param start     출발 위치. 없으면 첫 빵집이 출발점이 된다
 * @param order     방문 순서 기준. 없으면 {@link RouteOrder#NEAREST}
 */
public record RouteRequest(
        @NotEmpty @Size(max = RouteOptimizer.MAX_STOPS) List<@NotNull @Positive Long> bakeryIds,
        @Valid Start start,
        RouteOrder order) {

    /** 출발 위치 (대한민국 위경도 범위) */
    public record Start(
            @NotNull @DecimalMin("33.0") @DecimalMax("39.0") Double lat,
            @NotNull @DecimalMin("124.0") @DecimalMax("132.0") Double lng) {
    }

    public RouteOrder orderOrDefault() {
        return order == null ? RouteOrder.NEAREST : order;
    }

    public Point startPoint() {
        return start == null ? null : new Point(start.lat(), start.lng());
    }
}
