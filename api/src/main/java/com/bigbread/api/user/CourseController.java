package com.bigbread.api.user;

import com.bigbread.api.auth.AppUserPrincipal;
import com.bigbread.api.common.ApiException;
import com.bigbread.api.route.RouteOrder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 내 코스(저장한 동선). 로그인이 필요하고, 남의 코스는 보이지 않는다(404). */
@RestController
@RequestMapping("/api/my/courses")
public class CourseController {

    private static final String NOT_FOUND = "코스를 찾을 수 없어요.";

    private final CourseService courses;

    public CourseController(CourseService courses) {
        this.courses = courses;
    }

    /**
     * @param bakeryIds 방문 순서가 확정된 빵집 (1~10곳, 중복 불가). 이 순서 그대로 저장한다.
     * @param order     코스를 만들 때 쓴 순서 기준(표시용). 없으면 MANUAL
     */
    public record CreateRequest(
            @NotBlank @Size(max = 100) String title,
            @Size(max = 500) String description,
            RouteOrder order,
            @NotEmpty @Size(max = CourseService.MAX_STOPS) List<@NotNull @Positive Long> bakeryIds) {
    }

    public record UpdateRequest(@NotBlank @Size(max = 100) String title, @Size(max = 500) String description) {
    }

    @GetMapping
    public List<CourseService.Summary> list(@AuthenticationPrincipal AppUserPrincipal me) {
        return courses.list(me.userId());
    }

    @GetMapping("/{id}")
    public CourseService.Detail get(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable long id) {
        return courses.get(me.userId(), id).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CourseService.Detail create(@AuthenticationPrincipal AppUserPrincipal me,
                                       @Valid @RequestBody CreateRequest request) {
        String description = blankToNull(request.description());
        return courses.create(me.userId(), request.title().strip(), description,
                request.order() == null ? RouteOrder.MANUAL : request.order(), request.bakeryIds());
    }

    @PutMapping("/{id}")
    public CourseService.Detail update(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable long id,
                                       @Valid @RequestBody UpdateRequest request) {
        if (!courses.rename(me.userId(), id, request.title().strip(), blankToNull(request.description()))) {
            throw ApiException.notFound(NOT_FOUND);
        }
        return courses.get(me.userId(), id).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable long id) {
        if (!courses.delete(me.userId(), id)) {
            throw ApiException.notFound(NOT_FOUND);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
