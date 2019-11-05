package com.datadog.gradle

object Dependencies {

    object Versions {
        // Commons
        const val Kotlin = "1.3.41"
        const val AndroidToolsPlugin = "3.5.1"

        // JUnit
        const val JUnitJupiter = "5.5.2"
        const val JUnitPlatform = "1.5.2"
        const val JUnitVintage = "5.5.2"
        const val JunitMockitoExt = "2.23.0"

        // Tests Tools
        const val AssertJ = "0.2.1"
        const val Elmyr = "1.0.0-alpha2"
        const val Jacoco = "0.8.4"
        const val MockitoKotlin = "2.1.0"

        // Tools
        const val Detekt = "1.0.1"
        const val KtLint = "8.2.0"
        const val DependencyVersion = "0.27.0"
    }

    object Libraries {

        const val Kotlin = "org.jetbrains.kotlin:kotlin-stdlib:${Versions.Kotlin}"

        @JvmField
        val JUnit5 = arrayOf(
            "org.junit.platform:junit-platform-launcher:${Versions.JUnitPlatform}",
            "org.junit.vintage:junit-vintage-engine:${Versions.JUnitVintage}",
            "org.junit.jupiter:junit-jupiter:${Versions.JUnitJupiter}",
            "org.mockito:mockito-junit-jupiter:${Versions.JunitMockitoExt}"
        )

        @JvmField val TestTools = arrayOf(
            "net.wuerl.kotlin:assertj-core-kotlin:${Versions.AssertJ}",
            "com.github.xgouchet.Elmyr:core:${Versions.Elmyr}",
            "com.github.xgouchet.Elmyr:inject:${Versions.Elmyr}",
            "com.github.xgouchet.Elmyr:junit5:${Versions.Elmyr}",
            "com.nhaarman.mockitokotlin2:mockito-kotlin:${Versions.MockitoKotlin}"
        )
    }

    object ClassPaths {
        const val AndroidTools = "com.android.tools.build:gradle:${Versions.AndroidToolsPlugin}"
        const val Kotlin = "org.jetbrains.kotlin:kotlin-gradle-plugin:${Versions.Kotlin}"
        const val KtLint = "org.jlleitschuh.gradle:ktlint-gradle:${Versions.KtLint}"
    }

    object Repositories {
        const val Gradle = "https://plugins.gradle.org/m2/"
        const val Google = "https://maven.google.com"
        const val Jitpack = "https://jitpack.io"
    }

    object PluginNamespaces {
        const val Detetk = "io.gitlab.arturbosch"
        const val KtLint = "org.jlleitschuh.gradle"
        const val DependencyVersion = "com.github.ben-manes"
        const val Kotlin = "org.jetbrains.kotlin"
        const val KotlinAndroid = "org.jetbrains.kotlin.android"
    }
}
