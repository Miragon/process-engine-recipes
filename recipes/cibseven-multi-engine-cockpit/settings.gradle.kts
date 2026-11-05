plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "multi-engine-cockpit"

include("membership-service", "leasing-service", "logistics-service")
