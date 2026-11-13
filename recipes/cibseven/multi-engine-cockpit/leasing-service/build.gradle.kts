plugins {
    java
    alias(libs.plugins.spring.boot)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    implementation(platform(libs.cibseven.bom))
    implementation(libs.cibseven.starter.rest)
    implementation(libs.spring.boot.starter.jdbc)
    runtimeOnly(libs.h2)
}

tasks.bootJar {
    archiveFileName = "leasing-service.jar"
}
