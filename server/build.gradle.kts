plugins {
    alias(libs.plugins.micronaut.application)
}

description = "Traffic Game server — Micronaut WebSocket + REST"

application {
    mainClass = "com.trafficgame.server.Application"
}

dependencies {
    // Project modules
    implementation(project(":engine"))
    implementation(project(":game"))
    implementation(project(":shared"))

    // Micronaut core
    implementation(libs.micronaut.http.server.netty)
    implementation(libs.micronaut.websocket)
    implementation(libs.micronaut.jackson.databind)
    implementation(libs.micronaut.reactor)

    // MessagePack for binary WebSocket protocol
    implementation(libs.msgpack.core)
    implementation(libs.msgpack.jackson)

    // Jackson managed by Micronaut BOM

    // Runtime
    runtimeOnly(libs.logback.classic)
    runtimeOnly(libs.snakeyaml)

    // Testing
    testImplementation(libs.micronaut.http.client)
    testImplementation(libs.micronaut.test.junit5)
}

micronaut {
    runtime("netty")
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("com.trafficgame.server.*")
    }
}
