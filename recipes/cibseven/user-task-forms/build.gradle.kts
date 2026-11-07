plugins {
    java
    alias(libs.plugins.spring.boot)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

configurations.runtimeClasspath {
    exclude(group = "com.sun.xml.bind")
}

dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    implementation(platform(libs.cibseven.bom))
    implementation(libs.cibseven.starter.webapp)
    implementation(libs.cibseven.starter.rest)
    implementation(libs.spring.boot.starter.jdbc)
    runtimeOnly(libs.h2)

    testImplementation(platform(libs.spring.boot.dependencies))
    testImplementation(platform(libs.cibseven.bom))
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.cibseven.bpm.assert)
}

tasks.test {
    useJUnitPlatform()
}

tasks.bootJar {
    archiveFileName = "user-task-forms.jar"
}
