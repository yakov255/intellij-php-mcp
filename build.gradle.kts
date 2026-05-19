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
        bundledPlugin("com.jetbrains.php")
    }

    val kotlinxSerializationJsonVersion = "1.7.3"
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationJsonVersion")
}
