package com.bigbread.api.route;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/routes")
public class RouteController {

    private final RouteService service;

    public RouteController(RouteService service) {
        this.service = service;
    }

    /**
     * 선택한 빵집들의 도보 동선을 계산한다. (조회성이지만 빵집 ID 목록이 길 수 있어 POST를 쓴다.)
     * 예) {"bakeryIds":[1,2,3], "start":{"lat":36.3283,"lng":127.428}, "order":"NEAREST"}
     */
    @PostMapping
    public RouteResponse plan(@Valid @RequestBody RouteRequest request) {
        return service.plan(request);
    }
}
