import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    intellijPlatform {
        intellijIdeaUltimate("2025.2.6.2")
        testFramework(TestFrameworkType.Platform)
        bundledPlugin("com.intellij.mcpServer")
        plugins("com.jetbrains.php:252.28539.13")
    }

    val kotlinxSerializationJsonVersion = "1.11.0"
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationJsonVersion")
}
