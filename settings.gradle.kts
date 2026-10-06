plugins {
    // 로컬에 JDK 21이 없으면 Gradle이 툴체인(JDK 21)을 자동으로 내려받는다.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "bigbread"

include("core", "api", "collector")
