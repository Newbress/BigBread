package com.bigbread.api.user;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class CourseApiTest extends UserApiTestSupport {

    private ResultActions create(long userId, String json) throws Exception {
        return mvc.perform(withCsrf(post("/api/my/courses")).with(as(userId))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String body(String title, String order, long... ids) {
        StringBuilder list = new StringBuilder();
        for (long id : ids) {
            list.append(list.length() == 0 ? "" : ",").append(id);
        }
        return "{\"title\":\"" + title + "\"" + (order == null ? "" : ",\"order\":\"" + order + "\"")
                + ",\"bakeryIds\":[" + list + "]}";
    }

    @Test
    void 비로그인은_401이다() throws Exception {
        mvc.perform(get("/api/my/courses")).andExpect(status().isUnauthorized());
        mvc.perform(withCsrf(post("/api/my/courses")).contentType(MediaType.APPLICATION_JSON).content(body("t", null, 1)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 저장하면_보낸_순서_그대로_빵집이_저장된다() throws Exception {
        long me = newUser("나");
        long a = newBakery("코스A", "ACTIVE");
        long b = newBakery("코스B", "ACTIVE");
        long c = newBakery("코스C", "ACTIVE");

        create(me, body("  주말 산책  ", "NEAREST", c, a, b))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("주말 산책"))
                .andExpect(jsonPath("$.order").value("NEAREST"))
                .andExpect(jsonPath("$.stops[*].name", contains("코스C", "코스A", "코스B")))
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void 순서_기준을_생략하면_MANUAL이다() throws Exception {
        long me = newUser("나");
        long a = newBakery("코스A", "ACTIVE");
        create(me, body("기본", null, a)).andExpect(status().isCreated()).andExpect(jsonPath("$.order").value("MANUAL"));
    }

    @Test
    void 목록은_최근_순서이고_개수와_이름이_함께_온다() throws Exception {
        long me = newUser("나");
        long a = newBakery("코스A", "ACTIVE");
        long b = newBakery("코스B", "ACTIVE");
        create(me, body("첫번째", "MANUAL", a));
        create(me, "{\"title\":\"두번째\",\"description\":\"설명\",\"order\":\"MANUAL\",\"bakeryIds\":[" + b + "," + a + "]}");

        mvc.perform(get("/api/my/courses").with(as(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", contains("두번째", "첫번째")))
                .andExpect(jsonPath("$[0].stopCount").value(2))
                .andExpect(jsonPath("$[0].stopNames", contains("코스B", "코스A")))
                .andExpect(jsonPath("$[0].description").value("설명"))
                .andExpect(jsonPath("$[1].stopCount").value(1));
    }

    @Test
    void 상세는_순서대로_빵집_요약을_돌려준다() throws Exception {
        long me = newUser("나");
        long a = newBakery("코스A", "ACTIVE");
        long b = newBakery("코스B", "ACTIVE");
        String created = create(me, body("상세", "POPULARITY", b, a)).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(created, "$.id")).longValue();

        mvc.perform(get("/api/my/courses/{id}", id).with(as(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stops[*].id", contains((int) b, (int) a)))
                .andExpect(jsonPath("$.stops[0].lat").exists())
                .andExpect(jsonPath("$.order").value("POPULARITY"));
    }

    @Test
    void 잘못된_입력은_400이다() throws Exception {
        long me = newUser("나");
        long a = newBakery("코스A", "ACTIVE");
        long hidden = newBakery("숨김", "HIDDEN");

        create(me, body("   ", null, a)).andExpect(status().isBadRequest());
        create(me, body("t".repeat(101), null, a)).andExpect(status().isBadRequest());
        create(me, "{\"title\":\"빈\",\"bakeryIds\":[]}").andExpect(status().isBadRequest());
        create(me, body("열한곳", null, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)).andExpect(status().isBadRequest());
        create(me, body("중복", null, a, a)).andExpect(status().isBadRequest());
        create(me, body("없는", null, a, 999_999_999L)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("999999999")));
        create(me, body("숨김", null, a, hidden)).andExpect(status().isBadRequest());
        create(me, body("순서", "FASTEST", a)).andExpect(status().isBadRequest());
        create(me, "{\"title\":\"음수\",\"bakeryIds\":[-1]}").andExpect(status().isBadRequest());
        mvc.perform(get("/api/my/courses").with(as(me))).andExpect(jsonPath("$", empty()));
    }

    @Test
    void 남의_코스는_조회_수정_삭제_모두_404이고_내_코스는_그대로다() throws Exception {
        long me = newUser("나");
        long other = newUser("남");
        long a = newBakery("코스A", "ACTIVE");
        String created = create(me, body("내 코스", null, a)).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(created, "$.id")).longValue();

        mvc.perform(get("/api/my/courses/{id}", id).with(as(other))).andExpect(status().isNotFound());
        mvc.perform(withCsrf(put("/api/my/courses/{id}", id)).with(as(other)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"가로채기\"}")).andExpect(status().isNotFound());
        mvc.perform(withCsrf(delete("/api/my/courses/{id}", id)).with(as(other))).andExpect(status().isNotFound());
        mvc.perform(get("/api/my/courses").with(as(other))).andExpect(jsonPath("$", empty()));

        mvc.perform(get("/api/my/courses/{id}", id).with(as(me))).andExpect(jsonPath("$.title").value("내 코스"));
    }

    @Test
    void 이름을_바꾸고_지울_수_있다() throws Exception {
        long me = newUser("나");
        long a = newBakery("코스A", "ACTIVE");
        String created = create(me, body("이전", null, a)).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(created, "$.id")).longValue();

        mvc.perform(withCsrf(put("/api/my/courses/{id}", id)).with(as(me)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\" 새 이름 \",\"description\":\"  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("새 이름"))
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.stops", hasSize(1)));
        mvc.perform(withCsrf(put("/api/my/courses/{id}", id)).with(as(me)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"\"}")).andExpect(status().isBadRequest());

        mvc.perform(withCsrf(delete("/api/my/courses/{id}", id)).with(as(me))).andExpect(status().isNoContent());
        mvc.perform(get("/api/my/courses/{id}", id).with(as(me))).andExpect(status().isNotFound());
        mvc.perform(withCsrf(delete("/api/my/courses/{id}", id)).with(as(me))).andExpect(status().isNotFound());
    }

    @Test
    void 저장_뒤_숨김_처리된_빵집은_상세와_목록에서_빠진다() throws Exception {
        long me = newUser("나");
        long a = newBakery("남는빵집", "ACTIVE");
        long b = newBakery("나중에숨김", "ACTIVE");
        String created = create(me, body("숨김테스트", null, b, a)).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(created, "$.id")).longValue();

        jdbc.update("UPDATE bakery SET status = 'HIDDEN' WHERE id = ?", b);

        mvc.perform(get("/api/my/courses/{id}", id).with(as(me))).andExpect(jsonPath("$.stops[*].name", contains("남는빵집")));
        mvc.perform(get("/api/my/courses").with(as(me)))
                .andExpect(jsonPath("$[0].stopCount").value(1))
                .andExpect(jsonPath("$[0].stopNames", contains("남는빵집")));
    }

    @Test
    void 사용자당_코스는_최대_개수까지만_저장된다() throws Exception {
        long me = newUser("나");
        long a = newBakery("코스A", "ACTIVE");
        jdbc.update("INSERT INTO course (user_id, title) SELECT ?, '코스' || g FROM generate_series(1, ?) g", me, CourseService.MAX_COURSES);

        create(me, body("하나 더", null, a)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("최대 " + CourseService.MAX_COURSES)));
    }

    @Test
    void 빵집을_지워도_코스_저장_제약은_삭제를_막는다() throws Exception {
        // course_stop.bakery_id는 ON DELETE 동작이 없다: 코스에 쓰이는 빵집을 실수로 지우면 막혀야 한다 (숨김 처리로 운영한다).
        long me = newUser("나");
        long a = newBakery("지울수없음", "ACTIVE");
        create(me, body("묶임", null, a)).andExpect(status().isCreated());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("DELETE FROM bakery WHERE id = ?", a))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void CSRF_토큰이_없는_저장은_403이다() throws Exception {
        long me = newUser("나");
        long a = newBakery("코스A", "ACTIVE");
        mvc.perform(post("/api/my/courses").with(as(me)).contentType(MediaType.APPLICATION_JSON).content(body("t", null, a)))
                .andExpect(status().isForbidden());
    }
}
