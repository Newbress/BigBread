package com.bigbread.api.report;

import com.bigbread.api.auth.AppUserPrincipal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 빵집 제보 (로그인 필요). 보낸 제보와 처리 결과를 볼 수 있다. */
@RestController
@RequestMapping("/api/my/bakery-reports")
public class BakeryReportController {

    private final BakeryReportService reports;

    public BakeryReportController(BakeryReportService reports) {
        this.reports = reports;
    }

    @GetMapping
    public List<BakeryReportService.View> mine(@AuthenticationPrincipal AppUserPrincipal me) {
        return reports.mine(me.userId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BakeryReportService.View create(@AuthenticationPrincipal AppUserPrincipal me,
                                           @RequestBody BakeryReportService.Request request) {
        return reports.create(me.userId(), request);
    }
}
