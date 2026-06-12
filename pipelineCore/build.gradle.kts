plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
}

dependencies {
    // Exposed (table definitions and JDBC)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)

    // Serialization (JobCompletionEvent)
    implementation(libs.kotlinx.serialization.json)

    // Coroutines (JobRepository uses withContext)
    implementation(libs.kotlinx.coroutines.core)
}
