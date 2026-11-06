package com.bigbread.api.admin;

import com.bigbread.api.auth.AppUserPrincipal;
import com.bigbread.api.common.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/admin/bakeries")
class AdminBakeryController {

    private final AdminBakeryService service;

    AdminBakeryController(AdminBakeryService service) {
        this.service = service;
    }

    @GetMapping
    PageResponse<AdminBakeryService.Row> search(@RequestParam(required = false) String q,
                                                @RequestParam(required = false) String status,
                                                @RequestParam(defaultValue = "0") @Min(0) int page,
                                                @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return service.search(q, status, page, size);
    }

    @GetMapping("/{id}")
    AdminBakeryService.Detail get(@PathVariable long id) {
        return service.get(id);
    }

    @PatchMapping("/{id}")
    AdminBakeryService.Detail update(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable long id,
                                     @RequestBody Map<String, Object> body) {
        return service.update(me.userId(), id, body);
    }
}
