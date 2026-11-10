package com.bigbread.api.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bigbread.api.user.UserApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

class BakeryReportApiTest extends UserApiTestSupport {

    private ResultActions send(long user, String json) throws Exception {
        return mvc.perform(withCsrf(post("/api/my/bakery-reports")).with(as(user)).contentType(APPLICATION_JSON).content(json));
    }

    private static String newBakeryJson(String name, String address, String instagram) {
        return "{\"kind\":\"NEW\",\"name\":\"" + name + "\",\"address\":\"" + address + "\""
                + (instagram == null ? "" : ",\"instagram\":\"" + instagram + "\"") + "}";
    }

    @Test
    void 비로그인은_401이다() throws Exception {
        mvc.perform(get("/api/my/bakery-reports")).andExpect(status().isUnauthorized());
        mvc.perform(withCsrf(post("/api/my/bakery-reports")).contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 새_빵집을_제보하면_확인_중으로_저장되고_인스타_계정은_정리된다() throws Exception {
        long me = newUser("나");
        send(me, newBakeryJson("동네 베이커리", "대전 유성구 궁동 1-1", "https://www.instagram.com/Dongne.Bakery/?hl=ko"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("NEW"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.instagramUsername").value("dongne.bakery"));
        mvc.perform(get("/api/my/bakery-reports").with(as(me)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("동네 베이커리"));
        // 남의 제보는 보이지 않는다
        mvc.perform(get("/api/my/bakery-reports").with(as(newUser("다른사람")))).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void 필수값과_인스타_형식이_틀리면_400이다() throws Exception {
        long me = newUser("나");
        send(me, "{\"kind\":\"NEW\",\"name\":\"이름만\"}").andExpect(status().isBadRequest());
        send(me, "{\"kind\":\"NEW\",\"address\":\"주소만\"}").andExpect(status().isBadRequest());
        send(me, "{}").andExpect(status().isBadRequest());
        send(me, newBakeryJson("가게", "대전 중구 1", "빵집 계정")).andExpect(status().isBadRequest());
        send(me, "{\"kind\":\"NEW\",\"name\":\"" + "가".repeat(101) + "\",\"address\":\"대전\"}").andExpect(status().isBadRequest());
    }

    @Test
    void 이미_등록된_빵집과_이미_접수된_제보는_거절한다() throws Exception {
        long me = newUser("나");
        long bakery = newBakery("등록된 빵집", "ACTIVE");
        jdbc.update("UPDATE bakery SET road_address = '대전 서구 둔산로 10' WHERE id = ?", bakery);

        // 공백·대소문자·주소 일부만 달라도 같은 빵집으로 본다
        send(me, newBakeryJson("등록된빵집", "대전 서구 둔산로 10 1층", null)).andExpect(status().isConflict());
        // 이름이 같아도 다른 곳이면 다른 빵집이다
        send(me, newBakeryJson("등록된 빵집", "대전 동구 중앙로 5", null)).andExpect(status().isCreated());
        // 같은 제보를 다른 사람이 또 보내도 접수된 것으로 본다
        send(newUser("다른사람"), newBakeryJson("등록된 빵집", "대전 동구 중앙로 5", null)).andExpect(status().isConflict());
    }

    @Test
    void 폐업과_정보_오류_제보는_대상_빵집이_있어야_한다() throws Exception {
        long me = newUser("나");
        long bakery = newBakery("기존 빵집", "ACTIVE");

        send(me, "{\"kind\":\"CLOSED\",\"bakeryId\":" + bakery + "}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("CLOSED"))
                .andExpect(jsonPath("$.bakeryName").value("기존 빵집"));
        send(me, "{\"kind\":\"CLOSED\",\"bakeryId\":" + bakery + "}").andExpect(status().isConflict());
        send(me, "{\"kind\":\"CLOSED\",\"bakeryId\":999999999}").andExpect(status().isNotFound());
        send(me, "{\"kind\":\"CLOSED\"}").andExpect(status().isNotFound());
        send(me, "{\"kind\":\"WRONG_INFO\",\"bakeryId\":" + bakery + "}").andExpect(status().isBadRequest());
        send(me, "{\"kind\":\"WRONG_INFO\",\"bakeryId\":" + bakery + ",\"note\":\"전화번호가 달라요\"}")
                .andExpect(status().isCreated());
        mvc.perform(get("/api/my/bakery-reports").with(as(me))).andExpect(jsonPath("$[*].kind", contains("WRONG_INFO", "CLOSED")));
    }

    @Test
    void 확인_중인_제보가_5개면_더_보낼_수_없고_처리되면_다시_보낼_수_있다() throws Exception {
        long me = newUser("나");
        for (int i = 0; i < BakeryReportService.MAX_PENDING; i++) {
            send(me, newBakeryJson("제보빵집" + i, "대전 동구 " + i, null)).andExpect(status().isCreated());
        }
        send(me, newBakeryJson("제보빵집 여섯", "대전 동구 9", null)).andExpect(status().isTooManyRequests());

        jdbc.update("UPDATE bakery_report SET status = 'REJECTED', admin_note = '이미 폐업', reviewed_at = now() WHERE user_id = ? AND name = '제보빵집0'", me);
        send(me, newBakeryJson("제보빵집 여섯", "대전 동구 9", null)).andExpect(status().isCreated());
        mvc.perform(get("/api/my/bakery-reports").with(as(me)))
                .andExpect(jsonPath("$[?(@.name=='제보빵집0')].adminNote").value("이미 폐업"))
                .andExpect(jsonPath("$[?(@.name=='제보빵집0')].status").value("REJECTED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bakery_report WHERE user_id = ?", Long.class, me)).isEqualTo(6);
    }
}
