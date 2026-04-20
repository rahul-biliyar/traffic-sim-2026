plugins {
    `java-library`
}

description = "Traffic Game logic — systems, models, map generation"

dependencies {
    api(project(":engine"))
    api(project(":shared"))
}
