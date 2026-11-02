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
@RequestMapping("/api/admin/users")
class AdminUserController {

    private final AdminUserService service;

    AdminUserController(AdminUserService service) {
        this.service = service;
    }

    @GetMapping
    PageResponse<AdminUserService.Row> search(@RequestParam(required = false) String q,
                                              @RequestParam(defaultValue = "false") boolean suspended,
                                              @RequestParam(defaultValue = "0") @Min(0) int page,
                                              @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return service.search(q, suspended, page, size);
    }

    @GetMapping("/{id}")
    AdminUserService.Detail get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping("/{id}/suspend")
    AdminUserService.Detail suspend(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable long id,
                                    @RequestBody Map<String, String> body) {
        return service.suspend(me.userId(), id, body.get("reason"));
    }

    @PostMapping("/{id}/unsuspend")
    AdminUserService.Detail unsuspend(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable long id) {
        return service.unsuspend(me.userId(), id);
    }

    @PostMapping("/{id}/hide-content")
    AdminUserService.Detail hideContent(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable long id,
                                        @RequestBody Map<String, String> body) {
        return service.hideContent(me.userId(), id, body.get("reason"));
    }
}
