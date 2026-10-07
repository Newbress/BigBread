package com.bigbread.api.bakery;

import com.bigbread.api.common.PageResponse;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bakeries")
public class BakeryController {

    private final BakeryQueryService service;

    public BakeryController(BakeryQueryService service) {
        this.service = service;
    }

    /**
     * 빵집 목록. 구 필터, 반경 검색(내 위치), 정렬을 조합할 수 있다.
     * 예) /api/bakeries?district=YUSEONG&sort=POPULARITY
     *     /api/bakeries?lat=36.3277&lng=127.4274&radius=2000&sort=DISTANCE
     */
    @GetMapping
    public PageResponse<BakerySummary> search(
            @RequestParam(required = false) String district,
            @RequestParam(required = false) @DecimalMin("33.0") @DecimalMax("39.0") Double lat,
            @RequestParam(required = false) @DecimalMin("124.0") @DecimalMax("132.0") Double lng,
            @RequestParam(required = false) @Positive @Max(20000) Integer radius,
            @RequestParam(defaultValue = "POPULARITY") BakerySort sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return service.search(new BakerySearchCondition(district, lat, lng, radius, sort, page, size));
    }

    @GetMapping("/{id}")
    public BakeryDetail get(@PathVariable long id) {
        return service.get(id);
    }
}
