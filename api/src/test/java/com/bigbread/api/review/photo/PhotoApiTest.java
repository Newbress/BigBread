package com.bigbread.api.review.photo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bigbread.api.user.UserApiTestSupport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

class PhotoApiTest extends UserApiTestSupport {

    private static final Path DIR = createDir();

    private static Path createDir() {
        try {
            return Files.createTempDirectory("bigbread-photo-test-");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void photoDir(DynamicPropertyRegistry registry) {
        registry.add("bigbread.photos.dir", DIR::toString);
    }

    @AfterAll
    static void cleanUp() throws IOException {
        try (Stream<Path> files = Files.walk(DIR)) {
            files.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    private void review(long user, long bakery) throws Exception {
        mvc.perform(withCsrf(put("/api/my/reviews/{id}", bakery)).with(as(user))
                .contentType(APPLICATION_JSON).content("{\"rating\":5,\"content\":\"사진 리뷰\"}")).andExpect(status().isOk());
    }

    private ResultActions upload(long user, long bakery, byte[] bytes, String name) throws Exception {
        return mvc.perform(withCsrf(multipart("/api/my/reviews/{id}/photos", bakery)
                .file(new MockMultipartFile("file", name, "application/octet-stream", bytes))).with(as(user)));
    }

    private static long countFiles() throws IOException {
        try (Stream<Path> files = Files.walk(DIR)) {
            return files.filter(Files::isRegularFile).count();
        }
    }

    @Test
    void 비로그인은_올릴_수_없고_리뷰가_없어도_올릴_수_없다() throws Exception {
        long bakery = newBakery("사진A", "ACTIVE");
        long me = newUser("나");
        mvc.perform(withCsrf(multipart("/api/my/reviews/{id}/photos", bakery)
                .file(new MockMultipartFile("file", "a.jpg", "image/jpeg", PhotoFixtures.jpeg(100, 100)))))
                .andExpect(status().isUnauthorized());
        upload(me, bakery, PhotoFixtures.jpeg(100, 100), "a.jpg").andExpect(status().isNotFound());
    }

    @Test
    void 올리면_줄여서_저장되고_누구나_볼_수_있다() throws Exception {
        long me = newUser("나");
        long bakery = newBakery("사진A", "ACTIVE");
        review(me, bakery);

        MvcResult result = upload(me, bakery, PhotoFixtures.jpegWithExif(4000, 3000, 1), "IMG_0001.JPG")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.width").value(2560))
                .andExpect(jsonPath("$.height").value(1920))
                .andReturn();
        String url = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.url");
        String thumb = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.thumbUrl");
        assertThat(url).startsWith("/api/photos/").doesNotContain("IMG_0001");

        // 비로그인도 볼 수 있다
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().contentType("image/jpeg"));
        mvc.perform(get(thumb)).andExpect(status().isOk()).andExpect(content().contentType("image/jpeg"));

        // 리뷰 목록과 내 리뷰에 사진이 붙어 나온다
        mvc.perform(get("/api/bakeries/{id}/reviews", bakery))
                .andExpect(jsonPath("$.items[0].photos", hasSize(1)))
                .andExpect(jsonPath("$.items[0].photos[0].url").value(url));
        mvc.perform(get("/api/my/reviews/{id}", bakery).with(as(me))).andExpect(jsonPath("$.photos", hasSize(1)));
    }

    @Test
    void 리뷰_하나에_최대_3장이다() throws Exception {
        long me = newUser("나");
        long bakery = newBakery("사진A", "ACTIVE");
        review(me, bakery);
        for (int i = 0; i < 3; i++) {
            upload(me, bakery, PhotoFixtures.jpeg(300 + i, 200), "p" + i + ".jpg").andExpect(status().isCreated());
        }
        upload(me, bakery, PhotoFixtures.jpeg(300, 200), "p4.jpg")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("사진은 리뷰 하나에 최대 3장까지 올릴 수 있어요."));
        mvc.perform(get("/api/my/reviews/{id}", bakery).with(as(me))).andExpect(jsonPath("$.photos", hasSize(3)));
    }

    @Test
    void 사진이_아닌_파일과_지원하지_않는_형식은_이유와_함께_거부한다() throws Exception {
        long me = newUser("나");
        long bakery = newBakery("사진A", "ACTIVE");
        review(me, bakery);
        long before = countFiles();

        upload(me, bakery, "<html>not an image</html>".getBytes(), "evil.jpg")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(PhotoProcessor.UNSUPPORTED));
        upload(me, bakery, PhotoFixtures.gif(50, 50), "a.gif").andExpect(status().isBadRequest());
        upload(me, bakery, new byte[10 * 1024 * 1024 + 1], "huge.jpg")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("10MB")));
        assertThat(countFiles()).isEqualTo(before);
    }

    @Test
    void 내_사진만_지울_수_있고_지우면_파일도_사라진다() throws Exception {
        long me = newUser("나");
        long other = newUser("다른사람");
        long bakery = newBakery("사진A", "ACTIVE");
        review(me, bakery);
        review(other, bakery);

        MvcResult mine = upload(me, bakery, PhotoFixtures.jpeg(300, 200), "a.jpg").andExpect(status().isCreated()).andReturn();
        long photoId = ((Number) com.jayway.jsonpath.JsonPath.read(mine.getResponse().getContentAsString(), "$.id")).longValue();
        String url = com.jayway.jsonpath.JsonPath.read(mine.getResponse().getContentAsString(), "$.url");

        // 남이 지우려 해도 아무 일도 없다
        mvc.perform(withCsrf(delete("/api/my/reviews/{b}/photos/{p}", bakery, photoId)).with(as(other)))
                .andExpect(status().isNoContent());
        mvc.perform(get(url)).andExpect(status().isOk());

        long before = countFiles();
        mvc.perform(withCsrf(delete("/api/my/reviews/{b}/photos/{p}", bakery, photoId)).with(as(me)))
                .andExpect(status().isNoContent());
        mvc.perform(withCsrf(delete("/api/my/reviews/{b}/photos/{p}", bakery, photoId)).with(as(me)))
                .andExpect(status().isNoContent());
        mvc.perform(get(url)).andExpect(status().isNotFound());
        assertThat(countFiles()).isEqualTo(before - 2); // 원본 + 썸네일
    }

    @Test
    void 리뷰를_지우면_사진이_목록에서_사라진다() throws Exception {
        long me = newUser("나");
        long bakery = newBakery("사진A", "ACTIVE");
        review(me, bakery);
        upload(me, bakery, PhotoFixtures.jpeg(300, 200), "a.jpg").andExpect(status().isCreated());

        // (파일 삭제는 커밋 뒤에 일어난다. 테스트는 롤백되므로 실제 서버로 하는 E2E에서 확인한다.)
        mvc.perform(withCsrf(delete("/api/my/reviews/{id}", bakery)).with(as(me))).andExpect(status().isNoContent());
        mvc.perform(get("/api/bakeries/{id}/reviews", bakery)).andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void 숨김_처리된_사진은_보이지_않는다() throws Exception {
        long me = newUser("나");
        long bakery = newBakery("사진A", "ACTIVE");
        review(me, bakery);
        MvcResult uploaded = upload(me, bakery, PhotoFixtures.jpeg(300, 200), "a.jpg").andExpect(status().isCreated()).andReturn();
        String url = com.jayway.jsonpath.JsonPath.read(uploaded.getResponse().getContentAsString(), "$.url");
        jdbc.update("UPDATE review_photo SET status = 'HIDDEN'");

        mvc.perform(get(url)).andExpect(status().isNotFound());
        mvc.perform(get("/api/bakeries/{id}/reviews", bakery)).andExpect(jsonPath("$.items[0].photos", hasSize(0)));
    }

    @Test
    void 이상한_사진_주소는_404다() throws Exception {
        // 경로를 벗어나는 주소는 방화벽이나 형식 검사 중 한 곳에서 반드시 막힌다 (파일을 읽지 않는다).
        mvc.perform(get("/api/photos/{id}", "..%2F..%2Fetc%2Fpasswd")).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/photos/{id}", "zzzz")).andExpect(status().isNotFound());
        mvc.perform(get("/api/photos/{id}", "0".repeat(32))).andExpect(status().isNotFound());
    }
}
