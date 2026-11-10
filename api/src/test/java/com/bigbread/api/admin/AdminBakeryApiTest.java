package com.bigbread.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class AdminBakeryApiTest extends AdminApiTestBase {

    @Test
    void 숨김_폐업_빵집도_이름으로_검색하고_상태로_거를_수_있다() throws Exception {
        long admin = newAdmin("운영자");
        long active = newBakery("관리검색 활성", "ACTIVE");
        long hidden = newBakery("관리검색 숨김", "HIDDEN");
        long closed = newBakery("관리검색 폐업", "CLOSED");

        adminGet(admin, "/api/admin/bakeries?q=관리검색&size=50")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.items[*].id").value(org.hamcrest.Matchers.containsInAnyOrder(
                        (int) active, (int) hidden, (int) closed)));
        adminGet(admin, "/api/admin/bakeries?q=관리검색&status=HIDDEN")
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(hidden));
        // % 와 _ 는 글자 그대로 검색한다
        adminGet(admin, "/api/admin/bakeries?q=%25").andExpect(jsonPath("$.totalElements").value(0));
        adminGet(admin, "/api/admin/bakeries?status=WHAT").andExpect(status().isBadRequest());
    }

    @Test
    void 상태를_바꾸면_공개_화면에서_숨겨지고_다시_표시할_수_있다() throws Exception {
        long admin = newAdmin("운영자");
        long bakery = newBakery("상태 빵집", "ACTIVE");
        mvc.perform(get("/api/bakeries/{id}", bakery)).andExpect(status().isOk());

        adminPatch(admin, "/api/admin/bakeries/{id}", "{\"status\":\"HIDDEN\"}", bakery)
                .andExpect(status().isOk()).andExpect(jsonPath("$.summary.status").value("HIDDEN"));
        mvc.perform(get("/api/bakeries/{id}", bakery)).andExpect(status().isNotFound());

        adminPatch(admin, "/api/admin/bakeries/{id}", "{\"status\":\"ACTIVE\"}", bakery).andExpect(status().isOk());
        mvc.perform(get("/api/bakeries/{id}", bakery)).andExpect(status().isOk());
        adminPatch(admin, "/api/admin/bakeries/{id}", "{\"status\":\"DELETED\"}", bakery).andExpect(status().isBadRequest());
    }

    @Test
    void 인스타_계정과_전화번호를_고치고_지울_수_있다() throws Exception {
        long admin = newAdmin("운영자");
        long bakery = newBakery("연락 빵집", "ACTIVE");

        adminPatch(admin, "/api/admin/bakeries/{id}", "{\"instagramUsername\":\"@Bread.Shop_1\",\"phone\":\"042-123-4567\"}", bakery)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.instagramUsername").value("bread.shop_1"))
                .andExpect(jsonPath("$.phone").value("042-123-4567"));
        mvc.perform(get("/api/bakeries/{id}", bakery))
                .andExpect(jsonPath("$.links.instagram").value("https://www.instagram.com/bread.shop_1/"));

        adminPatch(admin, "/api/admin/bakeries/{id}", "{\"instagramUsername\":null,\"phone\":\"\"}", bakery)
                .andExpect(jsonPath("$.summary.instagramUsername").doesNotExist())
                .andExpect(jsonPath("$.phone").doesNotExist());
        adminPatch(admin, "/api/admin/bakeries/{id}", "{\"instagramUsername\":\"빵집 계정\"}", bakery).andExpect(status().isBadRequest());
        adminPatch(admin, "/api/admin/bakeries/{id}", "{\"phone\":\"<script>\"}", bakery).andExpect(status().isBadRequest());
    }

    @Test
    void 영업시간은_형식을_검사해서_저장하고_공개_화면에_나온다() throws Exception {
        long admin = newAdmin("운영자");
        long bakery = newBakery("시간 빵집", "ACTIVE");

        adminPatch(admin, "/api/admin/bakeries/{id}",
                "{\"openingHours\":{\"mon\":\"closed\",\"tue\":\"08:00-21:00\",\"sat\":\"10:00-02:00\"}}", bakery)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.hasOpeningHours").value(true))
                .andExpect(jsonPath("$.openingHours.tue").value("08:00-21:00"));
        mvc.perform(get("/api/bakeries/{id}", bakery))
                .andExpect(jsonPath("$.openingHours.mon").value("closed"))
                .andExpect(jsonPath("$.openingHours.sat").value("10:00-02:00"));

        adminPatch(admin, "/api/admin/bakeries/{id}", "{\"openingHours\":{\"mon\":\"아침부터 저녁\"}}", bakery).andExpect(status().isBadRequest());
        adminPatch(admin, "/api/admin/bakeries/{id}", "{\"openingHours\":{\"mon\":\"25:00-26:00\"}}", bakery).andExpect(status().isBadRequest());
        adminPatch(admin, "/api/admin/bakeries/{id}", "{\"openingHours\":{\"funday\":\"closed\"}}", bakery).andExpect(status().isBadRequest());
        adminPatch(admin, "/api/admin/bakeries/{id}", "{\"openingHours\":\"always\"}", bakery).andExpect(status().isBadRequest());

        adminPatch(admin, "/api/admin/bakeries/{id}", "{\"openingHours\":null}", bakery)
                .andExpect(jsonPath("$.summary.hasOpeningHours").value(false));
    }

    @Test
    void 모르는_항목이나_빈_요청은_거절하고_바뀐_내용은_기록된다() throws Exception {
        long admin = newAdmin("운영자");
        long bakery = newBakery("기록 빵집", "ACTIVE");
        adminPatch(admin, "/api/admin/bakeries/{id}", "{\"name\":\"다른 이름\"}", bakery).andExpect(status().isBadRequest());
        adminPatch(admin, "/api/admin/bakeries/{id}", "{}", bakery).andExpect(status().isBadRequest());
        adminPatch(admin, "/api/admin/bakeries/{id}", "{\"status\":\"HIDDEN\"}", 999_999_999L).andExpect(status().isNotFound());
        assertThat(q("SELECT name FROM bakery WHERE id = ?", bakery)).isEqualTo("기록 빵집");

        adminPatch(admin, "/api/admin/bakeries/{id}", "{\"status\":\"CLOSED\"}", bakery).andExpect(status().isOk());
        adminGet(admin, "/api/admin/audit-logs?targetType=BAKERY&targetId=" + bakery)
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].detail").value(org.hamcrest.Matchers.containsString("ACTIVE → CLOSED")));
        adminGet(admin, "/api/admin/bakeries/{id}", bakery).andExpect(jsonPath("$.summary.name").value("기록 빵집"));
    }
}
