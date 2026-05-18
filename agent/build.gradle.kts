plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ktor)
    application
}

application {
    mainClass.set("com.mediasage.agent.ApplicationKt")
}

dependencies {
    // Ktor Server
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.serialization.json)
    implementation(libs.logback)

    // Ktor Client (for calling Jira API)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)

    // Koin
    implementation(libs.koin.core)
    implementation(libs.koin.ktor)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Google Auth (Cloud Run Jobs API)
    implementation(libs.google.auth.oauth2)

    // Testing
    testImplementation(libs.kotlin.test)
    testImplementation(libs.ktor.server.tests)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.koin.test)
}
