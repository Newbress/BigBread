import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    id("org.springframework.boot") version "4.1.1" apply false
}

subprojects {
    apply(plugin = "java")

    group = "com.bigbread"
    version = "0.0.1-SNAPSHOT"

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    repositories {
        mavenCentral()
    }

    // 모든 모듈의 의존성 버전을 Spring Boot BOM으로 통일한다.
    dependencies {
        add("implementation", platform(SpringBootPlugin.BOM_COORDINATES))
        add("testImplementation", platform(SpringBootPlugin.BOM_COORDINATES))
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        // 통합 테스트는 Spring 컨텍스트를 여러 개 캐시한다. 풀을 작게 해 DB 접속 수(기본 100) 한도를 넘기지 않게 한다.
        environment("SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE", "3")
    }
}
