plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":core"))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-client")
    // 로그인 세션을 PostgreSQL에 저장 (서버 재시작 후에도 유지, 사용자 단위로 세션 만료 가능)
    implementation("org.springframework.boot:spring-boot-starter-session-jdbc")
    // Argon2 비밀번호 해시(Spring Security의 Argon2PasswordEncoder가 사용)
    implementation("org.bouncycastle:bcprov-jdk18on:1.80")
    // 인증 메일 발송 (SMTP)
    implementation("org.springframework.boot:spring-boot-starter-mail")
    // 리뷰 사진을 S3 호환 저장소(AWS S3, Cloudflare R2 등)에 저장
    implementation(platform("software.amazon.awssdk:bom:2.31.50"))
    implementation("software.amazon.awssdk:s3")
    implementation("software.amazon.awssdk:url-connection-client")
    // 리뷰 사진: JPEG(CMYK 등 특수 형식, EXIF 방향)·WebP 읽기
    implementation("com.twelvemonkeys.imageio:imageio-jpeg:3.15.3")
    implementation("com.twelvemonkeys.imageio:imageio-webp:3.15.3")
    implementation("com.twelvemonkeys.imageio:imageio-metadata:3.15.3")

    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    // 메일 발송 시험용 가짜 SMTP 서버
    testImplementation("com.icegreen:greenmail-junit5:2.1.15")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
