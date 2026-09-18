plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Deliberately a plain Kotlin module. If an Android dependency ever appears
// here, the multiplatform story is broken - that is what this module guards.
dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}

kotlin {
    jvmToolchain(21)
}
