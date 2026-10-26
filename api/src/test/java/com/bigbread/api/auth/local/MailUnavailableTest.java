package com.bigbread.api.auth.local;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** dev가 아닌 환경(메일 발송 수단 없음)에서는 회원가입을 막고, 아무것도 저장하지 않는다. */
@SpringBootTest(properties = "bigbread.kakao.rest-api-key=")
@AutoConfigureMockMvc
@Transactional
class MailUnavailableTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 메일_수단이_없으면_가입은_503이고_대기_목록에도_남지_않는다() throws Exception {
        Cookie xsrf = mvc.perform(get("/api/me")).andReturn().getResponse().getCookie("XSRF-TOKEN");

        mvc.perform(post("/api/auth/signup").cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"nomail01\",\"email\":\"n@example.com\",\"password\":\"BreadLover7!x\","
                                + "\"nickname\":\"메일없음\",\"agreedToTerms\":true}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("지금은 회원가입을 사용할 수 없어요."));

        Long pending = jdbc.queryForObject("SELECT count(*) FROM pending_signup", Long.class);
        org.assertj.core.api.Assertions.assertThat(pending).isZero();
    }
}
