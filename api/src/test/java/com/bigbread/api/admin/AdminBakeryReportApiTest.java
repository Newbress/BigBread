package com.bigbread.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class AdminBakeryReportApiTest extends AdminApiTestBase {

    private long newReport(long user, String kind, String name, String address, Long bakery, String instagram) {
        return jdbc.queryForObject("""
                INSERT INTO bakery_report (user_id, kind, name, address, bakery_id, instagram_username, note)
                VALUES (?, ?, ?, ?, ?, ?, '메모') RETURNING id
                """, Long.class, user, kind, name, address, bakery, instagram);
    }

    @Test
    void 확인할_제보는_오래된_순으로_보이고_상태로_걸러진다() throws Exception {
        long admin = newAdmin("운영자");
        long user = newUser("제보자");
        long first = newReport(user, "NEW", "제보1", "대전 서구 1", null, null);
        long second = newReport(user, "NEW", "제보2", "대전 서구 2", null, null);
        jdbc.update("UPDATE bakery_report SET created_at = now() - interval '1 day' WHERE id = ?", first);
        jdbc.update("UPDATE bakery_report SET status = 'REJECTED' WHERE id = ?", second);

        adminGet(admin, "/api/admin/bakery-reports?status=PENDING")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id==" + first + ")].reporterNickname").value("제보자"))
                .andExpect(jsonPath("$.items[?(@.id==" + second + ")]", hasSize(0)));
        adminGet(admin, "/api/admin/bakery-reports?status=REJECTED")
                .andExpect(jsonPath("$.items[?(@.id==" + second + ")]", hasSize(1)));
    }

    @Test
    void 새_빵집은_좌표를_직접_넣어_승인하면_빵집으로_등록된다() throws Exception {
        long admin = newAdmin("운영자");
        long user = newUser("제보자");
        long report = newReport(user, "NEW", "새로 생긴 빵집", "대전 유성구 궁동 1", null, "new.bakery");
        when(lookup.find(any(), any())).thenReturn(Optional.empty());

        // 위치를 못 찾으면 직접 넣으라고 안내한다
        adminPost(admin, "/api/admin/bakery-reports/{id}/approve", "{}", report)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("직접 넣어")));

        adminPost(admin, "/api/admin/bakery-reports/{id}/approve",
                "{\"lat\":36.36,\"lng\":127.35,\"districtCode\":\"YUSEONG\",\"adminNote\":\"등록했어요\"}", report)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.bakeryName").value("새로 생긴 빵집"));

        long bakeryId = jdbc.queryForObject("SELECT bakery_id FROM bakery_report WHERE id = ?", Long.class, report);
        assertThat(q("SELECT status FROM bakery WHERE id = ?", bakeryId)).isEqualTo("ACTIVE");
        assertThat(q("SELECT instagram_username FROM bakery WHERE id = ?", bakeryId)).isEqualTo("new.bakery");
        mvc.perform(get("/api/bakeries/{id}", bakeryId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.district.code").value("YUSEONG"));
        // 제보한 사람은 결과와 안내를 본다
        mvc.perform(get("/api/my/bakery-reports").with(as(user)))
                .andExpect(jsonPath("$[0].status").value("APPROVED"))
                .andExpect(jsonPath("$[0].adminNote").value("등록했어요"));
        // 이미 처리된 제보는 다시 처리할 수 없다
        adminPost(admin, "/api/admin/bakery-reports/{id}/approve", "{}", report).andExpect(status().isConflict());
        adminPost(admin, "/api/admin/bakery-reports/{id}/reject", "{\"adminNote\":\"x\"}", report).andExpect(status().isConflict());
    }

    @Test
    void 카카오에서_찾으면_위치와_카카오_장소를_채운다() throws Exception {
        long admin = newAdmin("운영자");
        long report = newReport(newUser("제보자"), "NEW", "카카오 빵집", "대전 서구 둔산동", null, null);
        String kakaoId = "k" + System.nanoTime();
        when(lookup.find("카카오 빵집", "대전 서구 둔산동")).thenReturn(Optional.of(new PlaceLookup.Place(
                kakaoId, "카카오 빵집", "대전 서구 둔산로 1", "대전 서구 둔산동 1", 36.35, 127.38, "042-111-2222")));

        adminPost(admin, "/api/admin/bakery-reports/{id}/approve", "{}", report).andExpect(status().isOk());
        long bakeryId = jdbc.queryForObject("SELECT bakery_id FROM bakery_report WHERE id = ?", Long.class, report);
        assertThat(q("SELECT kakao_place_id FROM bakery WHERE id = ?", bakeryId)).isEqualTo(kakaoId);
        assertThat(q("SELECT phone FROM bakery WHERE id = ?", bakeryId)).isEqualTo("042-111-2222");
        mvc.perform(get("/api/bakeries/{id}", bakeryId)).andExpect(jsonPath("$.district.code").value("SEO"))
                .andExpect(jsonPath("$.links.kakaoMap").value("https://place.map.kakao.com/" + kakaoId));

        // 같은 카카오 장소가 이미 등록돼 있으면 중복 등록하지 않는다
        long again = newReport(newUser("제보자2"), "NEW", "카카오 빵집2", "대전 서구 둔산동", null, null);
        when(lookup.find("카카오 빵집2", "대전 서구 둔산동")).thenReturn(Optional.of(new PlaceLookup.Place(
                kakaoId, "카카오 빵집", "대전 서구 둔산로 1", "대전 서구 둔산동 1", 36.35, 127.38, null)));
        adminPost(admin, "/api/admin/bakery-reports/{id}/approve", "{}", again).andExpect(status().isConflict());
    }

    @Test
    void 대전_밖이거나_좌표가_이상하면_등록하지_않는다() throws Exception {
        long admin = newAdmin("운영자");
        long report = newReport(newUser("제보자"), "NEW", "서울 빵집", "서울 강남구", null, null);
        when(lookup.find(any(), any())).thenReturn(Optional.of(new PlaceLookup.Place(
                "x1", "서울 빵집", "", "서울 강남구 역삼동 1", 37.5, 127.0, null)));
        adminPost(admin, "/api/admin/bakery-reports/{id}/approve", "{}", report).andExpect(status().isBadRequest());
        adminPost(admin, "/api/admin/bakery-reports/{id}/approve", "{\"lat\":10,\"lng\":127,\"districtCode\":\"DONG\"}", report)
                .andExpect(status().isBadRequest());
        adminPost(admin, "/api/admin/bakery-reports/{id}/approve", "{\"lat\":36.3,\"lng\":127.4}", report)
                .andExpect(status().isBadRequest());
        adminPost(admin, "/api/admin/bakery-reports/{id}/approve", "{\"lat\":36.3,\"lng\":127.4,\"districtCode\":\"NOPE\"}", report)
                .andExpect(status().isBadRequest());
        assertThat(q("SELECT status FROM bakery_report WHERE id = ?", report)).isEqualTo("PENDING");
    }

    @Test
    void 폐업_제보를_승인하면_빵집이_닫히고_목록에서_사라진다() throws Exception {
        long admin = newAdmin("운영자");
        long bakery = newBakery("닫을 빵집", "ACTIVE");
        long report = newReport(newUser("제보자"), "CLOSED", null, null, bakery, null);

        adminPost(admin, "/api/admin/bakery-reports/{id}/approve", "{}", report).andExpect(status().isOk());
        assertThat(q("SELECT status FROM bakery WHERE id = ?", bakery)).isEqualTo("CLOSED");
        mvc.perform(get("/api/bakeries/{id}", bakery)).andExpect(status().isNotFound());
    }

    @Test
    void 정보_오류_제보는_인스타_반영을_고를_수_있다() throws Exception {
        long admin = newAdmin("운영자");
        long bakery = newBakery("정보 빵집", "ACTIVE");
        long keep = newReport(newUser("제보자1"), "WRONG_INFO", null, null, bakery, "right.account");
        adminPost(admin, "/api/admin/bakery-reports/{id}/approve", "{}", keep).andExpect(status().isOk());
        assertThat(q("SELECT instagram_username FROM bakery WHERE id = ?", bakery)).isNull();

        long apply = newReport(newUser("제보자2"), "WRONG_INFO", null, null, bakery, "right.account");
        adminPost(admin, "/api/admin/bakery-reports/{id}/approve", "{\"applyInstagram\":true}", apply).andExpect(status().isOk());
        assertThat(q("SELECT instagram_username FROM bakery WHERE id = ?", bakery)).isEqualTo("right.account");
    }

    @Test
    void 반려는_사유가_필요하고_제보한_사람에게_보인다() throws Exception {
        long admin = newAdmin("운영자");
        long user = newUser("제보자");
        long report = newReport(user, "NEW", "반려될 빵집", "대전 동구 1", null, null);

        adminPost(admin, "/api/admin/bakery-reports/{id}/reject", "{}", report).andExpect(status().isBadRequest());
        adminPost(admin, "/api/admin/bakery-reports/{id}/reject", "{\"adminNote\":\"   \"}", report).andExpect(status().isBadRequest());
        adminPost(admin, "/api/admin/bakery-reports/{id}/reject", "{\"adminNote\":\"이미 폐업한 곳이에요\"}", report)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        mvc.perform(get("/api/my/bakery-reports").with(as(user)))
                .andExpect(jsonPath("$[0].adminNote").value("이미 폐업한 곳이에요"));
        adminPost(admin, "/api/admin/bakery-reports/{id}/approve", "{}", 999_999_999L).andExpect(status().isNotFound());
    }

    @Test
    void 처리하면_작업_기록이_남는다() throws Exception {
        long admin = newAdmin("운영자");
        long report = newReport(newUser("제보자"), "NEW", "기록될 빵집", "대전 동구 1", null, null);
        adminPost(admin, "/api/admin/bakery-reports/{id}/reject", "{\"adminNote\":\"중복\"}", report).andExpect(status().isOk());
        adminGet(admin, "/api/admin/audit-logs?targetType=BAKERY_REPORT&targetId=" + report)
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].action").value("BAKERY_REPORT_REJECT"))
                .andExpect(jsonPath("$.items[0].adminNickname").value("운영자"))
                .andExpect(jsonPath("$.items[0].detail").value("중복"));
    }
}
