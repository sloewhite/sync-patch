extension {
    name = "extensions/syncforreddit.rve"
}

android {
    namespace = "app.revanced.extension.syncforreddit"

    defaultConfig {
        minSdk = 23
    }
}

dependencies {
    compileOnly(project(":extensions:syncforreddit:stub"))
    compileOnly(libs.okhttp)
}

// The extension is plain Java. Keep the Kotlin standard library out of the code merged into Sync.
configurations.matching { it.name.endsWith("RuntimeClasspath") }.configureEach {
    exclude(group = "org.jetbrains.kotlin")
    exclude(group = "org.jetbrains", module = "annotations")
}
