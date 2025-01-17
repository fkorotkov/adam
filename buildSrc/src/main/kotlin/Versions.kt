object Versions {
    val adam = System.getenv("GITHUB_TAG_NAME") ?: "0.0.2"
    val kotlin = "1.3.41"
    val coroutines = "1.2.2"

    val annotations = "16.0.2"
    val kxml = "2.3.0"
    val ktor = "1.2.3"
    val logging = "1.7.6"

    val assertk = "0.19"
    val junit = "4.12"
    val dokka = "0.9.17"
}

object BuildPlugins {
    val kotlinPlugin = "org.jetbrains.kotlin:kotlin-gradle-plugin:${Versions.kotlin}"
    val dokka = "org.jetbrains.dokka:dokka-gradle-plugin:${Versions.dokka}"
}

object Libraries {
    val annotations = "org.jetbrains:annotations:${Versions.annotations}"
    val coroutines = "org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.coroutines}}"
    val kxml = "net.sf.kxml:kxml2:${Versions.kxml}"
    val ktorNetwork = "io.ktor:ktor-network:${Versions.ktor}"
    val logging = "io.github.microutils:kotlin-logging:${Versions.logging}"
}

object TestLibraries {
    val assertk = "com.willowtreeapps.assertk:assertk:${Versions.assertk}"
    val junit = "junit:junit:${Versions.junit}"
}