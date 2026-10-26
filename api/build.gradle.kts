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

    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
