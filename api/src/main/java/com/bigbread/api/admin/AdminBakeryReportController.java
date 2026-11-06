package com.bigbread.api.admin;

import com.bigbread.api.auth.AppUserPrincipal;
import com.bigbread.api.common.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/admin/bakery-reports")
class AdminBakeryReportController {

    private final AdminBakeryReportService service;

    AdminBakeryReportController(AdminBakeryReportService service) {
        this.service = service;
    }

    @GetMapping
    PageResponse<AdminBakeryReportService.Row> list(@RequestParam(defaultValue = "PENDING") String status,
                                                    @RequestParam(defaultValue = "0") @Min(0) int page,
                                                    @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return service.list("ALL".equals(status) ? null : status, page, size);
    }

    @PostMapping("/{id}/approve")
    AdminBakeryReportService.Row approve(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable long id,
                                         @RequestBody(required = false) AdminBakeryReportService.Approval body) {
        return service.approve(me.userId(), id, body == null
                ? new AdminBakeryReportService.Approval(null, null, null, null, null) : body);
    }

    @PostMapping("/{id}/reject")
    AdminBakeryReportService.Row reject(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable long id,
                                        @RequestBody Map<String, String> body) {
        return service.reject(me.userId(), id, body.get("adminNote"));
    }
}
